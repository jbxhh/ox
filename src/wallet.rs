mod atomic_fs;
mod encryption;
mod io;
mod scan;
mod secrets;
mod state;
mod sync;
pub mod view;

use monero_wallet::interface::{FeePriority, PublishTransactionError, TransactionsError};
use monero_wallet::send::SendError;
use monero_wallet::transaction::NotPruned;
use monero_wallet::transaction::PotentiallyPruned;
pub use sync::SyncError;
pub use sync::WalletSyncObserver;
pub use sync::WalletSyncState;
use tokio::sync::Mutex;
use tokio::sync::Notify;
use tokio::sync::RwLock;
use tokio::sync::RwLockReadGuard;
use tokio::sync::RwLockWriteGuard;
use tracing::{debug, warn};

use crate::MoneroBytes;
use crate::transport::MonfluoTransport;
use crate::wallet::scan::create_scanner;
use crate::wallet::scan::process_scan_result;
use crate::wallet::scan::process_transaction;
use crate::wallet::secrets::{WalletSecrets, WalletSeed};
pub use crate::wallet::state::OutgoingPayment;
use crate::wallet::state::OutgoingPaymentType;
pub use crate::wallet::state::OutgoingTransfer;
pub use crate::wallet::state::TransactionWrapper;
use crate::wallet::state::WalletState;
use chrono::NaiveDateTime;
use curve25519_dalek::constants::ED25519_BASEPOINT_TABLE;
use monero_daemon_rpc::MoneroDaemon;
use monero_wallet::OutputWithDecoys;
use monero_wallet::WalletOutput;
use monero_wallet::ed25519::Point;
use monero_wallet::interface::{FeeError, FeeRate, ProvidesUnvalidatedFeeRates, PublishTransaction};
use monero_wallet::ringct::RctType;
use monero_wallet::send::{Change, SignableTransaction};
use monero_wallet::transaction::Transaction;
use monero_wallet::{
    ViewPair,
    address::{MoneroAddress, Network, SubaddressIndex},
};
use monero_wallet_util::seed::Seed;
use rand::RngCore;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

const WALLETS_DIRECTORY_FILE_NAME: &str = "wallets";
const WALLET_SECRETS_FILE_NAME: &str = "secrets.bin";
const WALLET_DATA_FILE_NAME: &str = "data.bin";

pub type Daemon = Arc<MoneroDaemon<MonfluoTransport>>;

#[derive(Zeroize, ZeroizeOnDrop)]
pub struct Wallet {
    #[zeroize(skip)]
    path: PathBuf,
    password: Zeroizing<String>,
    secrets: WalletSecrets,
    #[zeroize(skip)]
    state: RwLock<WalletState>,
    #[zeroize(skip)]
    store_lock: Mutex<()>,
}

#[derive(Debug, Copy, Clone)]
pub enum WalletStatus {
    NotConnected,
    Connecting,
    Connected,
    StartingSynchronization,
    Synchronizing { target: u32, updated_at: NaiveDateTime },
    Synchronized { finished_at: NaiveDateTime },
}

#[derive(Debug, thiserror::Error)]
pub enum WalletError {
    #[error("Io: {0}")]
    Io(#[from] std::io::Error),
    #[error("Secrets: {0}")]
    Secrets(#[from] secrets::SecretsError),
    #[error("Io: {0}")]
    WalletIo(#[from] crate::wallet::io::WalletIoError),
    #[error("Encryption: {0}")]
    Encryption(#[from] crate::wallet::encryption::EncryptionError),
    #[error("Bad wallet name: {0}")]
    BadWalletName(String),
    #[error("Wallet '{0}' already exists")]
    WalletAlreadyExists(String),
    #[error("Encrypted polyseeds are not supported")]
    EncryptedPolyseed,
    #[error("Bad spend key")]
    BadSpendKey(Zeroizing<MoneroBytes>),
    #[error("Bad view key")]
    BadViewKey(Zeroizing<MoneroBytes>),
    #[error("Address is for {actual:?} but the wallet is for {expected:?}")]
    WrongAddressNetwork { expected: Network, actual: Network },
    #[error("Wallet has not been synced yet")]
    NotSynced,
    #[error("Not enough funds: {available} available, {required} required")]
    NotEnoughFunds { available: u64, required: u64 },
    #[error("Failed to fetch fee rate: {0}")]
    Fee(#[from] FeeError),
    #[error("Failed to select decoys: {0}")]
    Decoys(#[from] TransactionsError),
    #[error("Failed to construct transaction: {0}")]
    Send(#[from] SendError),
    #[error("Failed to sign transaction: {0}")]
    Sign(SendError),
    #[error("Failed to publish transaction: {0}")]
    Publish(#[from] PublishTransactionError),
    #[error("Failed to scan published transaction: {0}")]
    Scan(#[from] monero_wallet::ScanError),
    #[error("Sweep fee probe produced a transaction without a necessary fee")]
    SweepFeeProbeSucceeded,
    #[error("Sweep fee probe reported no necessary fee")]
    SweepFeeProbeMissingFee,
    #[error(transparent)]
    State(#[from] crate::wallet::state::Error),
}

fn ensure_payment_network(expected: Network, address: &MoneroAddress) -> Result<(), WalletError> {
    let actual = address.network();
    if actual != expected {
        return Err(WalletError::WrongAddressNetwork {
            expected,
            actual,
        });
    }
    Ok(())
}

#[derive(Debug, Copy, Clone)]
pub struct Payment {
    pub address: MoneroAddress,
    pub amount: u64,
}

// everything a send needs from the wallet state, taken at once so that the state lock isn't held
// while fetching fees and decoys from the daemon
struct SendSnapshot {
    current_height: u32,
    available_wallet_outputs: Vec<WalletOutput>,
}

impl SendSnapshot {
    async fn capture(wallet: &Wallet, account_index: u32) -> Result<Self, WalletError> {
        let state = wallet.read_state().await;
        let current_height = state.current_height().ok_or(WalletError::NotSynced)?;
        let available_wallet_outputs = state.find_available_wallet_outputs(account_index);
        Ok(Self {
            current_height,
            available_wallet_outputs,
        })
    }
}

impl Wallet {
    pub async fn create<P: AsRef<Path>>(monfluo_directory: P, network: Network, wallet_name: &str, password: &str, seed: Seed, seed_offset_passphrase: Option<&str>, restore_height: Option<u32>, birthday: u64) -> Result<(), WalletError> {
        let mut key = seed.key();
        if let Some(seed_offset_passphrase) = seed_offset_passphrase
            && !seed_offset_passphrase.is_empty()
        {
            key = crate::crypto::remove_seed_offset(&key, seed_offset_passphrase);
        }
        crate::crypto::sc_reduce32(&mut key);
        let spend_key = crate::util::bytes_to_scalar(&key).expect("Failed to convert reduced seed key into a scalar");
        let view_key = crate::util::bytes_to_scalar(&crate::crypto::spend_key_to_view_key(&key)).expect("Failed to convert reduced view key into a scalar");
        let seed = match &seed {
            monero_wallet_util::seed::Seed::Original(seed) => WalletSeed::Monero {
                seed: seed.to_string(),
                language: seed.language(),
            },
            monero_wallet_util::seed::Seed::Polyseed(seed) => {
                if crate::seed::polyseed_is_encrypted(seed) {
                    return Err(WalletError::EncryptedPolyseed);
                }
                WalletSeed::Polyseed {
                    seed: seed.to_string(),
                    language: seed.language(),
                }
            }
        };
        let secrets = WalletSecrets::new(spend_key, view_key, network, restore_height, birthday, Some(seed));
        let state = WalletState::new();
        let wallets_directory = Self::get_wallets_directory(monfluo_directory.as_ref());
        let wallet_dir_path = Self::get_wallet_directory(monfluo_directory, wallet_name)?;
        tokio::fs::create_dir_all(&wallets_directory).await?;
        match tokio::fs::create_dir(&wallet_dir_path).await {
            Ok(()) => {}
            Err(e) if e.kind() == std::io::ErrorKind::AlreadyExists => return Err(WalletError::WalletAlreadyExists(wallet_name.to_owned())),
            Err(e) => return Err(e.into()),
        }
        let secrets_file_path = wallet_dir_path.join(WALLET_SECRETS_FILE_NAME);
        let state_file_path = wallet_dir_path.join(WALLET_DATA_FILE_NAME);
        let secrets = secrets.to_bytes(password)?;
        let state = state.to_bytes(password)?;
        let write_wallet_files = async {
            crate::wallet::io::write(&secrets_file_path, &secrets).await?;
            crate::wallet::io::write(&state_file_path, &state).await?;
            Ok::<(), WalletError>(())
        };
        if let Err(e) = write_wallet_files.await {
            let _ = tokio::fs::remove_dir_all(&wallet_dir_path).await;
            return Err(e);
        }
        Ok(())
    }

    pub async fn open<P: AsRef<Path>>(monfluo_directory: P, wallet_name: &str, password: &str) -> Result<Self, WalletError> {
        let wallet_dir_path = Self::get_wallet_directory(monfluo_directory, wallet_name)?;
        let secrets_file_path = wallet_dir_path.join(WALLET_SECRETS_FILE_NAME);
        let state_file_path = wallet_dir_path.join(WALLET_DATA_FILE_NAME);
        let secrets_bytes = crate::wallet::io::read(&secrets_file_path).await?;
        let secrets = WalletSecrets::from_bytes(&secrets_bytes, password)?;
        let state_bytes = crate::wallet::io::read(&state_file_path).await?;
        let state = RwLock::new(WalletState::from_bytes(&state_bytes, password)?);

        Ok(Self {
            path: wallet_dir_path,
            password: Zeroizing::new(password.to_owned()),
            secrets,
            state,
            store_lock: Mutex::new(()),
        })
    }

    pub async fn list<P: AsRef<Path>>(monfluo_directory: P) -> Result<Vec<String>, WalletError> {
        let wallets_directory = Self::get_wallets_directory(monfluo_directory);

        let mut directories = match tokio::fs::read_dir(wallets_directory).await {
            Ok(directories) => directories,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                return Ok(Vec::new());
            }
            Err(e) => {
                return Err(e.into());
            }
        };

        let mut wallets = Vec::new();
        loop {
            match directories.next_entry().await {
                Ok(Some(wallet_directory)) => {
                    if let Some(wallet) = wallet_directory.file_name().to_str() {
                        wallets.push(wallet.to_owned());
                    }
                }
                Ok(None) => break,
                Err(e) => {
                    return Err(e.into());
                }
            }
        }

        Ok(wallets)
    }

    pub async fn delete<P: AsRef<Path>>(monfluo_directory: P, wallet_name: &str) -> Result<bool, WalletError> {
        let wallet_dir_path = Self::get_wallet_directory(monfluo_directory, wallet_name)?;
        match tokio::fs::remove_dir_all(wallet_dir_path).await {
            Ok(()) => Ok(true),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(false),
            Err(e) => Err(e.into()),
        }
    }

    pub async fn rename<P: AsRef<Path>>(monfluo_directory: P, wallet_name: &str, new_name: &str) -> Result<(), WalletError> {
        let wallet_dir_path = Self::get_wallet_directory(monfluo_directory.as_ref(), wallet_name)?;
        let new_wallet_dir_path = Self::get_wallet_directory(monfluo_directory, new_name)?;
        tokio::fs::rename(wallet_dir_path, new_wallet_dir_path).await?;
        Ok(())
    }

    pub async fn store(&self) -> Result<(), WalletError> {
        let _store_guard = self.store_lock.lock().await;
        let state_file_path = self.path.join(WALLET_DATA_FILE_NAME);
        let state = self.read_state().await.to_bytes(&self.password)?;
        crate::wallet::io::write(&state_file_path, &state).await?;
        Ok(())
    }

    pub fn name(&self) -> Option<&str> {
        self.path
            .file_name()
            .and_then(|file_name| file_name.to_str())
    }

    pub async fn sync<O: sync::WalletSyncObserver>(&self, daemon: Daemon, observer: O, cancel: &Notify) {
        sync::sync(self, daemon, observer, cancel).await
    }

    pub(crate) async fn read_state(&self) -> RwLockReadGuard<'_, WalletState> {
        self.state.read().await
    }

    pub(crate) async fn write_state(&self) -> RwLockWriteGuard<'_, WalletState> {
        self.state.write().await
    }

    pub async fn current_height(&self) -> Option<u32> {
        self.state.read().await.current_height()
    }

    pub async fn create_transaction(&self, daemon: Daemon, account_index: u32, payments: Vec<Payment>, fee_priority: FeePriority) -> Result<(SignableTransaction, OutgoingTransfer), WalletError> {
        let network = self.network();
        for payment in &payments {
            ensure_payment_network(network, &payment.address)?;
        }
        let send_snapshot = SendSnapshot::capture(self, account_index).await?;
        self.create_transaction_inner(daemon, account_index, send_snapshot, payments, fee_priority)
            .await
    }

    async fn create_transaction_inner(&self, daemon: Daemon, account_index: u32, send_snapshot: SendSnapshot, payments: Vec<Payment>, fee_priority: FeePriority) -> Result<(SignableTransaction, OutgoingTransfer), WalletError> {
        let SendSnapshot {
            current_height,
            available_wallet_outputs,
        } = send_snapshot;
        let rct_type: RctType = RctType::ClsagBulletproofPlus;
        let fee_rate: FeeRate = daemon.fee_rate(fee_priority).await?;
        let outgoing_view_key: Zeroizing<MoneroBytes> = Self::create_outgoing_view_key().await;
        let mut available_wallet_outputs_iter = available_wallet_outputs.into_iter();
        let payments_sum = payments.iter().map(|payment| payment.amount).sum::<u64>();
        let mut inputs_sum = 0;
        let mut inputs = Vec::new();
        while inputs_sum < payments_sum
            && let Some(available_wallet_output) = available_wallet_outputs_iter.next()
        {
            let input: OutputWithDecoys = OutputWithDecoys::new(&mut *crate::get_rng_mut().await, &*daemon, 16, current_height as usize, available_wallet_output).await?;
            inputs_sum += input.commitment().amount;
            inputs.push(input);
        }
        let change: Change = Change::new(self.view_pair(), Self::change_subaddress(account_index));
        let data: Vec<Vec<u8>> = vec![];
        let raw_payments = payments
            .into_iter()
            .map(|payment| (payment.address, payment.amount))
            .collect::<Vec<_>>();
        loop {
            match SignableTransaction::new(rct_type, outgoing_view_key.clone(), inputs.clone(), raw_payments.clone(), change.clone(), data.clone(), fee_rate) {
                Ok(signable_transaction) => {
                    let change_address = self.address(Self::change_subaddress(account_index));
                    let outgoing_transfer = Self::create_outgoing_transfer(&outgoing_view_key, &raw_payments, Some(change_address), inputs_sum, payments_sum, signable_transaction.necessary_fee())?;
                    return Ok((signable_transaction, outgoing_transfer));
                }
                Err(SendError::NotEnoughFunds {
                    outputs,
                    necessary_fee,
                    ..
                }) => {
                    let needed_amount = outputs + necessary_fee.unwrap_or_default();
                    while inputs_sum < needed_amount {
                        let Some(available_wallet_output) = available_wallet_outputs_iter.next() else {
                            return Err(WalletError::NotEnoughFunds {
                                available: inputs_sum,
                                required: needed_amount,
                            });
                        };
                        let input: OutputWithDecoys = OutputWithDecoys::new(&mut *crate::get_rng_mut().await, &*daemon, 16, current_height as usize, available_wallet_output).await?;
                        inputs_sum += input.commitment().amount;
                        inputs.push(input);
                    }
                }
                Err(e) => return Err(e.into()),
            }
        }
    }

    pub async fn create_sweep_transaction(&self, daemon: Daemon, account_index: u32, address: MoneroAddress, fee_priority: FeePriority) -> Result<(SignableTransaction, OutgoingTransfer), WalletError> {
        ensure_payment_network(self.network(), &address)?;
        let send_snapshot = SendSnapshot::capture(self, account_index).await?;
        self.create_sweep_transaction_inner(daemon, send_snapshot, address, fee_priority)
            .await
    }

    async fn create_sweep_transaction_inner(&self, daemon: Daemon, send_snapshot: SendSnapshot, address: MoneroAddress, fee_priority: FeePriority) -> Result<(SignableTransaction, OutgoingTransfer), WalletError> {
        let SendSnapshot {
            current_height,
            available_wallet_outputs,
        } = send_snapshot;
        let rct_type: RctType = RctType::ClsagBulletproofPlus;
        let fee_rate: FeeRate = daemon.fee_rate(fee_priority).await?;
        let outgoing_view_key: Zeroizing<MoneroBytes> = Self::create_outgoing_view_key().await;
        let mut inputs_sum = 0;
        let mut inputs = Vec::new();
        for available_wallet_output in available_wallet_outputs {
            let input: OutputWithDecoys = OutputWithDecoys::new(&mut *crate::get_rng_mut().await, &*daemon, 16, current_height as usize, available_wallet_output).await?;
            inputs_sum += input.commitment().amount;
            inputs.push(input);
        }
        debug!("Total funds from {} inputs in sweep transactions: {}", inputs.len(), inputs_sum);
        let change: Change = Change::new(crate::crypto::random_view_pair(&mut *crate::get_rng_mut().await), None);
        let data: Vec<Vec<u8>> = vec![];
        let test_raw_payments = vec![(address, inputs_sum)];
        let necessary_fee = match SignableTransaction::new(rct_type, outgoing_view_key.clone(), inputs.clone(), test_raw_payments, change.clone(), data.clone(), fee_rate) {
            Ok(_probe_transaction) => return Err(WalletError::SweepFeeProbeSucceeded),
            Err(SendError::NotEnoughFunds {
                inputs: _,
                outputs: _,
                necessary_fee,
            }) => necessary_fee.ok_or(WalletError::SweepFeeProbeMissingFee)?,
            Err(e) => return Err(e.into()),
        };
        debug!("Necessary fee for sweep: {}", necessary_fee);
        let Some(sweep_amount) = inputs_sum.checked_sub(necessary_fee) else {
            return Err(WalletError::NotEnoughFunds {
                available: inputs_sum,
                required: necessary_fee,
            });
        };
        let raw_payments = vec![(address, sweep_amount)];
        let signable_transaction = SignableTransaction::new(rct_type, outgoing_view_key.clone(), inputs, raw_payments.clone(), change, data, fee_rate)?;
        let outgoing_transfer = Self::create_outgoing_transfer(&outgoing_view_key, &raw_payments, None, inputs_sum, sweep_amount, signable_transaction.necessary_fee())?;
        Ok((signable_transaction, outgoing_transfer))
    }

    async fn create_outgoing_view_key() -> Zeroizing<MoneroBytes> {
        let mut array: MoneroBytes = [0; 32];
        crate::get_rng_mut().await.fill_bytes(&mut array);
        Zeroizing::new(array)
    }

    fn create_outgoing_transfer(outgoing_view_key: &MoneroBytes, raw_payments: &[(MoneroAddress, u64)], change_address: Option<MoneroAddress>, inputs_sum: u64, payments_sum: u64, fee: u64) -> Result<OutgoingTransfer, WalletError> {
        let mut payments = Vec::with_capacity(raw_payments.len() + 1);
        for raw_payment in raw_payments {
            payments.push(OutgoingPayment {
                address: raw_payment.0.to_string(),
                amount: raw_payment.1,
                r#type: OutgoingPaymentType::Payment,
            });
        }
        let Some(change_amount) = inputs_sum
            .checked_sub(payments_sum)
            .and_then(|amount| amount.checked_sub(fee))
        else {
            return Err(WalletError::NotEnoughFunds {
                available: inputs_sum,
                required: payments_sum.saturating_add(fee),
            });
        };
        match (change_address, change_amount) {
            (Some(change_address), 1..) => payments.push(OutgoingPayment {
                address: change_address.to_string(),
                amount: change_amount,
                r#type: OutgoingPaymentType::Change,
            }),
            (None, 1..) => warn!("Not recording a change output of {} atomic units with no change address", change_amount),
            (_, 0) => {}
        }
        Ok(OutgoingTransfer {
            outgoing_view_key: *outgoing_view_key,
            payments,
        })
    }

    pub async fn sign_transaction(&self, transaction: SignableTransaction) -> Result<Transaction, WalletError> {
        transaction
            .sign(&mut *crate::get_rng_mut().await, self.secrets.spend_key())
            .map_err(WalletError::Sign)
    }

    pub async fn send_transaction(&self, daemon: Daemon, transaction: TransactionWrapper<NotPruned>) -> Result<(), WalletError> {
        daemon.publish_transaction(&transaction.inner).await?;
        let transaction = transaction.prune();
        let mut state = self.state.write().await;
        let scanner = create_scanner(&state, self);
        let timelocked = scanner.scan_transaction(0, transaction.hash, &transaction.inner)?;
        process_transaction(&mut state, transaction.clone());
        let mut transactions = HashMap::new();
        transactions.insert(transaction.hash, transaction);
        process_scan_result(&mut state, self.secrets.spend_key(), timelocked, transactions);
        Ok(())
    }

    pub async fn register_new_account(&self) -> u32 {
        let mut state = self.write_state().await;
        state.addresses.register_new_account()
    }

    pub async fn register_new_address(&self, account_index: u32) -> u32 {
        let mut state = self.write_state().await;
        state
            .addresses
            .increment_last_tracked_address(account_index)
    }

    pub async fn wallet_view(&self) -> view::WalletView {
        let state = self.read_state().await;
        view::wallet_view(&state, self)
    }

    pub async fn balance_view(&self) -> view::balance::BalanceView {
        let state = self.read_state().await;
        view::balance_view(&state)
    }

    pub async fn account_view(&self, account_index: u32) -> view::account::AccountView {
        let state = self.read_state().await;
        view::account_view(&state, self, account_index)
    }

    pub async fn accounts_view(&self) -> Vec<view::account::AccountView> {
        let state = self.read_state().await;
        view::accounts_view(&state, self)
    }

    pub async fn transaction_views(&self) -> Vec<view::transaction::TransactionView> {
        let state = self.read_state().await;
        view::transaction_views(&state, self)
    }

    pub async fn create_transaction_view_of<P: PotentiallyPruned>(&self, raw_transaction: &TransactionWrapper<P>) -> view::transaction::TransactionView {
        let state = self.read_state().await;
        view::transaction_view_of(&state, self, raw_transaction)
    }

    pub(crate) fn restore_height(&self) -> Option<u32> {
        self.secrets.restore_height()
    }

    pub fn seed_view(&self) -> Option<view::seed::WalletSeedView> {
        self.secrets.seed().map(|seed| seed.view())
    }

    pub fn public_spend_key(&self) -> MoneroBytes {
        self.view_pair().spend().compress().to_bytes()
    }

    pub fn public_view_key(&self) -> MoneroBytes {
        self.view_pair().view().compress().to_bytes()
    }

    pub fn spend_key(&self) -> Zeroizing<MoneroBytes> {
        Zeroizing::new(From::from(**self.secrets.spend_key()))
    }

    pub fn view_key(&self) -> Zeroizing<MoneroBytes> {
        Zeroizing::new(From::from(**self.secrets.view_key()))
    }

    pub fn legacy_seed(&self, language: monero_wallet_util::seed::original::Language, seed_offset_passphrase: &str) -> monero_wallet_util::seed::original::Seed {
        let spend_key = self.spend_key();
        let seed_key = if seed_offset_passphrase.is_empty() { spend_key } else { crate::crypto::add_seed_offset(&spend_key, seed_offset_passphrase) };
        monero_wallet_util::seed::original::Seed::from_entropy(language, seed_key).expect("Failed to encode the spend key as a seed")
    }

    pub fn network(&self) -> Network {
        self.secrets.network()
    }

    pub fn address(&self, subaddress_index: Option<SubaddressIndex>) -> MoneroAddress {
        let network = self.secrets.network();
        if let Some(subaddress_index) = subaddress_index { self.view_pair().subaddress(network, subaddress_index) } else { self.view_pair().legacy_address(network) }
    }

    pub fn get_wallets_directory<P: AsRef<Path>>(monfluo_directory: P) -> PathBuf {
        monfluo_directory.as_ref().join(WALLETS_DIRECTORY_FILE_NAME)
    }

    pub fn get_wallet_directory<P: AsRef<Path>>(monfluo_directory: P, wallet_name: &str) -> Result<PathBuf, WalletError> {
        let wallet_name_path = PathBuf::from(wallet_name);
        if wallet_name_path.is_absolute() || wallet_name_path.parent() != Some(Path::new("")) {
            return Err(WalletError::BadWalletName(wallet_name.to_owned()));
        }
        Ok(Self::get_wallets_directory(monfluo_directory).join(wallet_name_path))
    }

    fn view_pair(&self) -> ViewPair {
        ViewPair::new(Point::from(&(**self.secrets.spend_key()).into() * ED25519_BASEPOINT_TABLE), self.secrets.view_key().clone()).expect("failed to create view pair")
    }

    fn change_subaddress(account_index: u32) -> Option<SubaddressIndex> {
        SubaddressIndex::new(account_index, 0)
    }
}

#[cfg(test)]
mod tests {
    use crate::wallet::io::WalletIoError;

    use super::*;

    #[test]
    fn get_wallet_directory() {
        let result = Wallet::get_wallet_directory("/path/to/monfluo/dir", "my-wallet");
        assert!(matches!(result, Ok(path) if path.to_str() == Some("/path/to/monfluo/dir/wallets/my-wallet")));
    }

    #[test]
    fn get_wallet_directory_with_trailing_slash_in_wallets_dir() {
        let result = Wallet::get_wallet_directory("/path/to/monfluo/dir/", "my-wallet");
        assert!(matches!(result, Ok(path) if path.to_str() == Some("/path/to/monfluo/dir/wallets/my-wallet")));
    }

    #[test]
    fn get_wallet_directory_with_absolute_wallet_name() {
        let result = Wallet::get_wallet_directory("/monfluo/wallets/dir/", "/my-wallet");
        assert!(matches!(result, Err(WalletError::BadWalletName(wallet_name)) if wallet_name == "/my-wallet"));
    }

    #[test]
    fn get_wallet_directory_with_slash_in_wallet_name() {
        let result = Wallet::get_wallet_directory("/monfluo/wallets/dir/", "my-dir/my-wallet");
        assert!(matches!(result, Err(WalletError::BadWalletName(wallet_name)) if wallet_name == "my-dir/my-wallet"));
    }

    #[tokio::test]
    async fn create_and_open_wallet() {
        const WALLET_NAME: &str = "Monero Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let seed = monero_wallet_util::seed::Seed::Original(crate::generate_seed(monero_wallet_util::seed::original::Language::English).await);
        Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", seed.clone(), None, None, 1780000000000)
            .await
            .unwrap();
        let wallet = Wallet::open(monfluo_directory.path(), WALLET_NAME, "")
            .await
            .unwrap();
        assert_eq!(seed.key(), wallet.spend_key())
    }

    #[tokio::test]
    async fn create_and_open_wallet_from_polyseed() {
        const WALLET_NAME: &str = "Monero Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let seed = monero_wallet_util::seed::Seed::Polyseed(crate::generate_polyseed(monero_wallet_util::seed::polyseed::Language::English).await);
        Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", seed.clone(), None, None, 1780000000000)
            .await
            .unwrap();
        let wallet = Wallet::open(monfluo_directory.path(), WALLET_NAME, "")
            .await
            .unwrap();
        // polyseed keys are a raw PBKDF2 output, so the wallet stores their reduced form (as Monero does)
        let mut expected_spend_key = seed.key();
        crate::crypto::sc_reduce32(&mut expected_spend_key);
        assert_eq!(expected_spend_key, wallet.spend_key())
    }

    #[tokio::test]
    async fn creating_a_wallet_with_an_existing_name_fails() {
        const WALLET_NAME: &str = "Monero Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let seed = monero_wallet_util::seed::Seed::Original(crate::generate_seed(monero_wallet_util::seed::original::Language::English).await);
        Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", seed.clone(), None, None, 1780000000000)
            .await
            .expect("Failed to create the wallet");

        let other_seed = monero_wallet_util::seed::Seed::Original(crate::generate_seed(monero_wallet_util::seed::original::Language::English).await);
        let create_result = Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", other_seed, None, None, 1780000000000).await;
        assert!(matches!(create_result, Err(WalletError::WalletAlreadyExists(name)) if name == WALLET_NAME));

        let wallet = Wallet::open(monfluo_directory.path(), WALLET_NAME, "")
            .await
            .expect("Failed to open the wallet");
        assert_eq!(seed.key(), wallet.spend_key())
    }

    #[tokio::test]
    async fn rename_wallet() {
        const WALLET_NAME: &str = "Monero Wallet";
        const NEW_WALLET_NAME: &str = "Renamed Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let seed = monero_wallet_util::seed::Seed::Original(crate::generate_seed(monero_wallet_util::seed::original::Language::English).await);
        Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", seed.clone(), None, None, 1780000000000)
            .await
            .unwrap();
        Wallet::rename(monfluo_directory.path(), WALLET_NAME, NEW_WALLET_NAME)
            .await
            .unwrap();
        assert_eq!(Wallet::list(monfluo_directory.path()).await.unwrap(), vec![NEW_WALLET_NAME.to_owned()]);
        let wallet = Wallet::open(monfluo_directory.path(), NEW_WALLET_NAME, "")
            .await
            .unwrap();
        assert_eq!(seed.key(), wallet.spend_key())
    }

    #[tokio::test]
    async fn rename_wallet_to_existing_wallet_fails() {
        const WALLET_NAME: &str = "Monero Wallet";
        const OTHER_WALLET_NAME: &str = "Other Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let seed = monero_wallet_util::seed::Seed::Original(crate::generate_seed(monero_wallet_util::seed::original::Language::English).await);
        Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", seed.clone(), None, None, 1780000000000)
            .await
            .unwrap();
        Wallet::create(monfluo_directory.path(), Network::Stagenet, OTHER_WALLET_NAME, "", seed, None, None, 1780000000000)
            .await
            .unwrap();
        let rename_result = Wallet::rename(monfluo_directory.path(), WALLET_NAME, OTHER_WALLET_NAME).await;
        assert!(rename_result.is_err())
    }

    #[tokio::test]
    async fn wallet_secrets_store_the_seed() {
        const WALLET_NAME: &str = "Monero Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let seed = monero_wallet_util::seed::Seed::Polyseed(crate::generate_polyseed(monero_wallet_util::seed::polyseed::Language::English).await);
        Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", seed.clone(), None, None, 1780000000000)
            .await
            .unwrap();
        let wallet = Wallet::open(monfluo_directory.path(), WALLET_NAME, "")
            .await
            .unwrap();
        let stored = wallet.seed_view().expect("Failed to get the stored seed");
        assert!(matches!(
            &stored,
            crate::wallet::view::seed::WalletSeedView::Polyseed {
                seed: stored_seed,
                language: monero_wallet_util::seed::polyseed::Language::English,
            } if *stored_seed == *seed.to_string()
        ));
    }

    #[tokio::test]
    async fn creating_a_wallet_from_an_encrypted_polyseed_fails() {
        const WALLET_NAME: &str = "Monero Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        // Encrypted by the reference polyseed implementation with the passphrase "mysecret"
        let words = "woman pistol speed perfect artist swear budget quantum write card envelope spoon claw school name mask";
        let seed = monero_wallet_util::seed::Seed::Polyseed(crate::seed::try_decode_polyseed(Zeroizing::new(words.to_owned())).expect("Failed to decode the encrypted polyseed"));
        let create_result = Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", seed, None, None, 1780000000000).await;
        assert!(matches!(create_result, Err(WalletError::EncryptedPolyseed)));
    }

    #[tokio::test]
    async fn legacy_seed_of_a_polyseed_wallet_restores_the_same_keys() {
        const WALLET_NAME: &str = "Monero Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let seed = monero_wallet_util::seed::Seed::Polyseed(crate::generate_polyseed(monero_wallet_util::seed::polyseed::Language::English).await);
        Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", seed, None, None, 1780000000000)
            .await
            .unwrap();
        let wallet = Wallet::open(monfluo_directory.path(), WALLET_NAME, "")
            .await
            .unwrap();
        let legacy_seed = wallet.legacy_seed(monero_wallet_util::seed::original::Language::English, "");
        let restored = monero_wallet_util::seed::original::Seed::from_string(monero_wallet_util::seed::original::Language::English, legacy_seed.to_string()).expect("Failed to decode the legacy seed");
        assert_eq!(*restored.entropy(), *wallet.spend_key());
    }

    #[tokio::test]
    async fn wallet_created_with_a_seed_offset_restores_from_the_offset_seed() {
        const WALLET_NAME: &str = "Monero Wallet";
        const SEED_OFFSET_PASSPHRASE: &str = "mysecret";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let seed = monero_wallet_util::seed::Seed::Original(crate::generate_seed(monero_wallet_util::seed::original::Language::English).await);

        // Restoring with an offset stores a key derived from the offset seed
        Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", seed.clone(), Some(SEED_OFFSET_PASSPHRASE), None, 1780000000000)
            .await
            .unwrap();
        let wallet = Wallet::open(monfluo_directory.path(), WALLET_NAME, "")
            .await
            .unwrap();
        let expected_spend_key = crate::crypto::remove_seed_offset(&seed.key(), SEED_OFFSET_PASSPHRASE);
        assert_eq!(*wallet.spend_key(), *expected_spend_key);

        // Showing the seed with the same offset reproduces the words the user typed
        let offset_seed = wallet.legacy_seed(monero_wallet_util::seed::original::Language::English, SEED_OFFSET_PASSPHRASE);
        assert_eq!(*offset_seed.to_string(), *seed.to_string());

        // And without the offset it is a different seed
        let raw_seed = wallet.legacy_seed(monero_wallet_util::seed::original::Language::English, "");
        assert_ne!(*raw_seed.to_string(), *seed.to_string());
    }

    #[tokio::test]
    async fn an_empty_seed_offset_passphrase_is_the_same_as_none() {
        const WALLET_NAME: &str = "Monero Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let seed = monero_wallet_util::seed::Seed::Original(crate::generate_seed(monero_wallet_util::seed::original::Language::English).await);

        Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", seed.clone(), Some(""), None, 1780000000000)
            .await
            .unwrap();
        let wallet = Wallet::open(monfluo_directory.path(), WALLET_NAME, "")
            .await
            .unwrap();
        assert_eq!(seed.key(), wallet.spend_key());
    }

    #[tokio::test]
    async fn wallet_view_carries_the_restore_heights() {
        const WALLET_NAME: &str = "Monero Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let seed = monero_wallet_util::seed::Seed::Original(crate::generate_seed(monero_wallet_util::seed::original::Language::English).await);
        Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", seed, None, Some(1234), 1780000000000)
            .await
            .unwrap();
        let wallet = Wallet::open(monfluo_directory.path(), WALLET_NAME, "")
            .await
            .unwrap();

        let wallet_view = wallet.wallet_view().await;
        assert_eq!(wallet_view.restore_height, Some(1234));

        // the public keys must match the ones the address encodes
        let address = wallet.address(None);
        let parsed = MoneroAddress::from_str(Network::Stagenet, &address.to_string()).expect("Failed to parse the address");
        assert_eq!(wallet.public_spend_key(), parsed.spend().compress().to_bytes());
        assert_eq!(wallet.public_view_key(), parsed.view().compress().to_bytes());

        // nothing synced yet, so there are no outputs and it falls back to the restore height
        assert_eq!(wallet_view.adjusted_restore_height, Some(1234));
    }

    #[tokio::test]
    async fn open_nonexisting_wallet_returns_io_error() {
        const WALLET_NAME: &str = "Monero Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let wallet_result = Wallet::open(monfluo_directory.path(), WALLET_NAME, "").await;
        assert!(matches!(wallet_result, Err(WalletError::WalletIo(WalletIoError::Io(e))) if e.kind() == std::io::ErrorKind::NotFound));
    }

    async fn open_stagenet_wallet() -> (tempfile::TempDir, Wallet) {
        const WALLET_NAME: &str = "Monero Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let seed = monero_wallet_util::seed::Seed::Original(crate::generate_seed(monero_wallet_util::seed::original::Language::English).await);
        Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", seed, None, None, 1780000000000)
            .await
            .unwrap();
        let wallet = Wallet::open(monfluo_directory.path(), WALLET_NAME, "")
            .await
            .unwrap();
        (monfluo_directory, wallet)
    }

    #[tokio::test]
    async fn network_is_preserved_when_reopening_wallet() {
        let (_monfluo_directory, wallet) = open_stagenet_wallet().await;
        assert_eq!(wallet.network(), Network::Stagenet);
        assert_eq!(wallet.address(None).network(), Network::Stagenet);
    }

    #[tokio::test]
    async fn payment_on_another_network_is_rejected() {
        let (_monfluo_directory, wallet) = open_stagenet_wallet().await;
        let mainnet_address = MoneroAddress::from_str(Network::Mainnet, "42ey1afDFnn4886T7196doS9GPMzexD9gXpsZJDwVjeRVdFCSoHnv7KPbBeGpzJBzHRCAs9UxqeoyFQMYbqSWYTfJJQAWDm").expect("Failed to parse a known mainnet address");
        let result = ensure_payment_network(wallet.network(), &mainnet_address);
        assert!(matches!(
            result,
            Err(WalletError::WrongAddressNetwork {
                expected: Network::Stagenet,
                actual: Network::Mainnet
            })
        ));
    }

    #[tokio::test]
    async fn payment_on_the_same_network_is_accepted() {
        let (_monfluo_directory, wallet) = open_stagenet_wallet().await;
        let address = wallet.address(Some(SubaddressIndex::new(0, 1).expect("Failed to construct subaddress index")));
        assert!(ensure_payment_network(wallet.network(), &address).is_ok());
    }

    #[tokio::test]
    async fn outgoing_transfer_does_not_underflow_when_fee_exceeds_inputs() {
        let (_monfluo_directory, wallet) = open_stagenet_wallet().await;
        let result = Wallet::create_outgoing_transfer(&[0; 32], &[], Some(wallet.address(None)), 100, 100, 10);
        assert!(matches!(
            result,
            Err(WalletError::NotEnoughFunds {
                available: 100,
                required: 110
            })
        ));
    }
}
