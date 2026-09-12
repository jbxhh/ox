mod atomic_fs;
mod encryption;
mod io;
mod secrets;
mod state;
mod sync;
pub mod view;

use monero_wallet::send::SendError;
use monero_wallet::transaction::NotPruned;
use monero_wallet::transaction::PotentiallyPruned;
use monero_wallet::transaction::Timelock;
pub use sync::SyncError;
pub use sync::WalletSyncObserver;
pub use sync::WalletSyncState;
use tokio::runtime::Runtime;
use tokio::sync::Mutex;
use tokio::task::JoinHandle;

use crate::MoneroBytes;
use crate::transport::MonfluoTransport;
use crate::wallet::secrets::WalletSecrets;
pub use crate::wallet::state::OutgoingPayment;
pub use crate::wallet::state::OutgoingTransfer;
pub use crate::wallet::state::TransactionWrapper;
use crate::wallet::state::{WalletOutputWrapper, WalletState};
use crate::wallet::view::transaction::TransactionBlockInfoView;
use crate::wallet::view::transaction::{TransactionViewInput, TransactionViewSpend};
use arc_swap::ArcSwap;
use chrono::NaiveDateTime;
use curve25519_dalek::constants::ED25519_BASEPOINT_TABLE;
use monero_daemon_rpc::MoneroDaemon;
use monero_wallet::OutputWithDecoys;
use monero_wallet::ed25519::Point;
use monero_wallet::interface::{FeeRate, ProvidesUnvalidatedFeeRates, PublishTransaction};
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
use std::ops::Deref;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

const WALLETS_DIRECTORY_FILE_NAME: &str = "wallets";
const WALLET_SECRETS_FILE_NAME: &str = "secrets.bin";
const WALLET_DATA_FILE_NAME: &str = "data.bin";

pub type Daemon = Arc<MoneroDaemon<MonfluoTransport>>;

#[derive(Zeroize, ZeroizeOnDrop)]
pub struct Wallet {
    #[zeroize(skip)]
    path: PathBuf,
    secrets: WalletSecrets,
    #[zeroize(skip)]
    state: ArcSwap<WalletState>,
    #[zeroize(skip)]
    sync_handle: Mutex<Option<SyncHandle>>,
}

struct SyncHandle {
    handle: JoinHandle<()>,
    cancellation_token: Arc<AtomicBool>,
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
    #[error("Bad wallet name: {0}")]
    BadWalletName(String),
    #[error("Bad spend key")]
    BadSpendKey(Zeroizing<MoneroBytes>),
    #[error("Bad view key")]
    BadViewKey(Zeroizing<MoneroBytes>),
}

#[derive(Debug, Copy, Clone)]
pub struct Payment {
    pub address: MoneroAddress,
    pub amount: u64,
}

impl Wallet {
    pub async fn create<P: AsRef<Path>>(monfluo_directory: P, network: Network, wallet_name: &str, password: &str, seed: Seed, restore_height: u32) -> Result<(), WalletError> {
        let key = seed.key();
        let spend_key = crate::util::bytes_to_scalar(&key).unwrap();
        let view_key = crate::util::bytes_to_scalar(&crate::crypto::spend_key_to_view_key(&key)).unwrap();
        let secrets = WalletSecrets::new(spend_key, view_key, network, restore_height);
        let state = WalletState::new(restore_height);
        let wallet_dir_path = Self::get_wallet_directory(monfluo_directory, wallet_name)?;
        tokio::fs::create_dir_all(&wallet_dir_path).await?;
        let secrets_file_path = wallet_dir_path.join(WALLET_SECRETS_FILE_NAME);
        let state_file_path = wallet_dir_path.join(WALLET_DATA_FILE_NAME);
        secrets.store(&secrets_file_path, password).await.unwrap();
        state.store(&state_file_path, password).await.unwrap();
        Ok(())
    }

    pub async fn open<P: AsRef<Path>>(monfluo_directory: P, wallet_name: &str, password: &str) -> Result<Self, WalletError> {
        let wallet_dir_path = Self::get_wallet_directory(monfluo_directory, wallet_name)?;
        let secrets_file_path = wallet_dir_path.join(WALLET_SECRETS_FILE_NAME);
        let state_file_path = wallet_dir_path.join(WALLET_DATA_FILE_NAME);
        let secrets = WalletSecrets::load(&secrets_file_path, password).await?;
        let state = ArcSwap::new(Arc::new(
            WalletState::load(&state_file_path, password)
                .await
                .expect("Failed to load wallet state"),
        ));

        Ok(Self {
            path: wallet_dir_path,
            secrets,
            state,
            sync_handle: Mutex::new(None),
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

    pub async fn store(&self) {
        let state_file_path = self.path.join(WALLET_DATA_FILE_NAME);
        self.state().store(&state_file_path, "").await.unwrap();
    }

    pub fn name(&self) -> Option<&str> {
        self.path
            .file_name()
            .and_then(|file_name| file_name.to_str())
    }

    pub fn sync<O: sync::WalletSyncObserver + Send + 'static>(self: Arc<Self>, runtime: &Runtime, daemon: Daemon, observer: O) {
        let wallet = self.clone();
        let Ok(mut sync_state_guard) = self.sync_handle.try_lock() else {
            panic!("TODO: return Err. Cannot take lock on a sync state guard");
        };
        if let Some(sync_state) = &mut *sync_state_guard
            && !sync_state.handle.is_finished()
        {
            panic!("TODO: return Err. Sync is already in progress");
        }
        let cancellation_token = Arc::new(AtomicBool::new(false));
        let handle = runtime.spawn(sync::sync(wallet, daemon, observer, cancellation_token.clone()));
        *sync_state_guard = Some(SyncHandle {
            handle,
            cancellation_token,
        })
    }

    pub async fn close(&self) {
        let Ok(mut sync_state_guard) = self.sync_handle.try_lock() else {
            panic!("TODO: return Err. Cannot take lock on a sync state guard");
        };
        if let Some(sync_state) = sync_state_guard.take() {
            sync_state
                .cancellation_token
                .store(true, std::sync::atomic::Ordering::Relaxed);
            let _ = sync_state.handle.await;
        }
        self.store().await;
    }

    pub async fn create_transaction(&self, daemon: Daemon, payments: Vec<Payment>) -> (SignableTransaction, OutgoingTransfer) {
        let state = self.state.load();
        let rct_type: RctType = RctType::ClsagBulletproofPlus;
        let fee_rate: FeeRate = daemon
            .fee_rate(monero_wallet::interface::FeePriority::Normal)
            .await
            .unwrap();
        let outgoing_view_key: Zeroizing<MoneroBytes> = {
            let mut array: MoneroBytes = [0; 32];
            crate::get_rng_mut().await.fill_bytes(&mut array);
            Zeroizing::new(array)
        };
        let mut available_wallet_outputs_iter = {
            let mut available_wallet_outputs = state
                .outputs
                .values()
                // TODO: also filter out immature enotes
                .filter(|output| {
                    !state
                        .cache
                        .key_image_to_spend_transaction_input
                        .contains_key(&output.cached_key_image)
                })
                .collect::<Vec<_>>();
            // TODO: this will select lowest-amount enotes first, is this the strategy we want to use?
            available_wallet_outputs.sort_by_key(|output| output.inner.commitment().amount);
            available_wallet_outputs.into_iter()
        };
        let payments_sum = payments.iter().map(|payment| payment.amount).sum::<u64>();
        let mut inputs_sum = 0;
        let mut inputs = Vec::new();
        while inputs_sum < payments_sum
            && let Some(available_wallet_output) = available_wallet_outputs_iter.next()
        {
            let input: OutputWithDecoys = OutputWithDecoys::new(&mut *crate::get_rng_mut().await, &*daemon, 16, state.current_height() as usize, available_wallet_output.inner.clone())
                .await
                .unwrap();
            inputs_sum += input.commitment().amount;
            inputs.push(input);
        }
        let change: Change = Change::new(self.view_pair(), None);
        let data: Vec<Vec<u8>> = vec![];
        let outgoing_transfer = OutgoingTransfer {
            outgoing_view_key: *outgoing_view_key,
            payments: payments
                .iter()
                .map(|payment| OutgoingPayment {
                    address: payment.address.to_string(),
                    amount: payment.amount,
                })
                .collect::<Vec<_>>(),
        };
        let raw_payments = payments
            .into_iter()
            .map(|payment| (payment.address, payment.amount))
            .collect::<Vec<_>>();
        loop {
            match SignableTransaction::new(rct_type, outgoing_view_key.clone(), inputs.clone(), raw_payments.clone(), change.clone(), data.clone(), fee_rate) {
                Ok(signable_transaction) => return (signable_transaction, outgoing_transfer),
                Err(SendError::NotEnoughFunds {
                    inputs: _,
                    outputs: needed_amount,
                    necessary_fee: _,
                }) => {
                    while inputs_sum < needed_amount
                        && let Some(available_wallet_output) = available_wallet_outputs_iter.next()
                    {
                        let input: OutputWithDecoys = OutputWithDecoys::new(&mut *crate::get_rng_mut().await, &*daemon, 16, state.current_height() as usize, available_wallet_output.inner.clone())
                            .await
                            .unwrap();
                        inputs.push(input);
                    }
                    if inputs_sum < needed_amount {
                        // TODO: return error instead of panicking
                        panic!("Not enough funds to create transaction. Available funds: {}, required funds: {}", inputs_sum, needed_amount);
                    }
                }
                Err(e) => {
                    // TODO: return error instead of panicking
                    panic!("Failed to create transaction: {e:?}");
                }
            }
        }
    }

    pub async fn sign_tranasction(&self, transaction: SignableTransaction) -> Transaction {
        transaction
            .sign(&mut *crate::get_rng_mut().await, self.secrets.spend_key())
            .unwrap()
    }

    pub async fn send_transaction(&self, daemon: Daemon, transaction: TransactionWrapper<NotPruned>) {
        daemon
            .publish_transaction(&transaction.inner)
            .await
            .unwrap();
        let transaction = transaction.prune();
        self.state.rcu(|state| {
            let mut state = state.deref().clone();
            state.add_transaction(transaction.clone());
            Arc::new(state)
        });
    }

    pub fn register_new_account(&self) -> u32 {
        let mut last_account = None;
        self.state.rcu(|state| {
            let mut state = state.deref().clone();
            let last_tracked_account = state
                .tracked_addresses
                .last_key_value()
                .map(|(k, _v)| *k)
                .expect("there are no tracked addresses");
            let new_account = last_tracked_account + 1;
            state.tracked_addresses.insert(new_account, 0);
            last_account = Some(new_account);
            Arc::new(state)
        });
        last_account.expect("last account was not set after registering new account")
    }

    pub fn register_new_address(&self, account_index: u32) -> Option<u32> {
        let mut last_address = None;
        self.state.rcu(|state| {
            let mut state = state.deref().clone();
            match state.tracked_addresses.get(&account_index).copied() {
                Some(last_tracked_address) => {
                    let new_address = last_tracked_address + 1;
                    state.tracked_addresses.insert(account_index, new_address);
                    last_address = Some(new_address);
                    Arc::new(state)
                }
                None => Arc::new(state),
            }
        });
        last_address
    }

    pub fn balance_view(&self) -> view::balance::BalanceView {
        let state = self.state.load();
        let mut total = 0;
        let mut unlocked = 0;
        let mut pending = 0;
        for output in state.outputs.values() {
            if !state
                .cache
                .key_image_to_spend_transaction_input
                .contains_key(&output.cached_key_image)
            {
                let amount = output.inner.commitment().amount;
                total += amount;
                if output.inner.additional_timelock() == Timelock::None || matches!(output.inner.additional_timelock(), Timelock::Block(block) if block >= state.current_height() as usize) {
                    unlocked += amount;
                } else {
                    pending += amount;
                }
            }
        }
        view::balance::BalanceView {
            total,
            unlocked,
            pending,
            frozen: 0,
        }
    }

    pub fn accounts_view(&self) -> Vec<view::account::AccountView> {
        let state = self.state.load();
        let last_address_index = state.tracked_addresses().keys().last().copied();
        last_address_index
            .map(|last_address_index| {
                (0..=last_address_index)
                    .map(|account_index| self.account_view(account_index))
                    .collect()
            })
            .unwrap_or_default()
    }

    pub fn account_view(&self, account_index: u32) -> view::account::AccountView {
        let state = self.state.load();
        let last_address_index = state.tracked_addresses().get(&account_index).copied();
        view::account::AccountView {
            account_index,
            addresses: last_address_index
                .map(|last_address_index| {
                    (0..=last_address_index)
                        .map(|address_index| self.address_view(account_index, address_index))
                        .collect()
                })
                .unwrap_or_default(),
        }
    }

    pub fn address_view(&self, account_index: u32, address_index: u32) -> view::address::AddressView {
        let state = self.state.load();
        let subaddres_index = SubaddressIndex::new(account_index, address_index);
        let mut used: bool = false;
        let mut amount: u64 = 0;

        for output in state.outputs.values() {
            if output.inner.subaddress() == subaddres_index {
                used = true;
                if !state
                    .cache
                    .key_image_to_spend_transaction_input
                    .contains_key(&output.cached_key_image)
                {
                    amount += output.inner.commitment().amount;
                }
            }
        }

        view::address::AddressView {
            account_index,
            address_index,
            value: self
                .address(SubaddressIndex::new(account_index, address_index), Network::Mainnet)
                .to_string(),
            used,
            amount,
        }
    }

    pub fn transaction_views(&self) -> Vec<view::transaction::TransactionView> {
        let state = self.state.load();
        let mut raw_transactions = state.transactions.values().collect::<Vec<_>>();
        raw_transactions.sort_by_key(|raw_transaction| {
            (
                raw_transaction
                    .block_info
                    .as_ref()
                    .map(|block_info| block_info.timestamp),
                raw_transaction.hash,
            )
        });
        self.transaction_views_of(raw_transactions)
    }

    pub fn transaction_view_of<P: PotentiallyPruned>(&self, raw_transaction: &TransactionWrapper<P>) -> view::transaction::TransactionView {
        self.transaction_views_of(vec![raw_transaction]).remove(0)
    }

    pub fn transaction_views_of<P: PotentiallyPruned>(&self, raw_transactions: Vec<&TransactionWrapper<P>>) -> Vec<view::transaction::TransactionView> {
        let state = self.state.load();
        let current_height = state.current_height();

        let mut wallet_inputs = HashMap::<MoneroBytes, Vec<&WalletOutputWrapper>>::new();
        let mut wallet_outputs = HashMap::<MoneroBytes, Vec<&WalletOutputWrapper>>::new();

        for output in state.outputs.values() {
            wallet_inputs
                .entry(output.inner.transaction())
                .or_default()
                .push(output);
        }

        for (key_image, &(transaction_hash, _input_index)) in state.cache.key_image_to_spend_transaction_input.iter() {
            wallet_outputs
                .entry(transaction_hash)
                .or_default()
                .push(&state.cache.key_image_to_output[key_image]);
        }

        raw_transactions
            .into_iter()
            .map(|raw_transaction| {
                let inputs = wallet_inputs
                    .get(&raw_transaction.hash)
                    .map(Vec::as_slice)
                    .unwrap_or_default()
                    .iter()
                    .map(|wallet_output| TransactionViewInput {
                        address: self
                            .address(wallet_output.inner.subaddress(), Network::Mainnet)
                            .to_string(),
                        amount: wallet_output.inner.commitment().amount,
                    })
                    .collect();

                let spends = raw_transaction
                    .outgoing_transfer
                    .as_ref()
                    .map(|outgoing_transfer| {
                        outgoing_transfer
                            .payments
                            .iter()
                            .map(|outgoing_payment| TransactionViewSpend {
                                address: Some(outgoing_payment.address.clone()),
                                amount: outgoing_payment.amount,
                            })
                            .collect::<Vec<_>>()
                    })
                    .unwrap_or_else(|| {
                        wallet_outputs
                            .get(&raw_transaction.hash)
                            .map(Vec::as_slice)
                            .unwrap_or_default()
                            .iter()
                            .map(|wallet_output| TransactionViewSpend {
                                address: None,
                                amount: wallet_output.inner.commitment().amount,
                            })
                            .collect()
                    });

                view::transaction::TransactionView {
                    transaction_hash: raw_transaction.hash,
                    timestamp: raw_transaction
                        .block_info
                        .as_ref()
                        .map_or(raw_transaction.first_seen_timestamp, |block_info| block_info.timestamp),
                    fee: raw_transaction.inner.fee(),
                    confirmations: raw_transaction
                        .block_info
                        .as_ref()
                        .map_or(0, |block_info| current_height.saturating_sub(block_info.block.height - 1)),
                    block_info: raw_transaction
                        .block_info
                        .map(|block_info| TransactionBlockInfoView {
                            block_height: block_info.block.height,
                            block_hash: block_info.block.hash,
                        }),
                    inputs,
                    spends,
                    outgoing_view_key: raw_transaction
                        .outgoing_transfer
                        .as_ref()
                        .map(|outgoing_transfer| outgoing_transfer.outgoing_view_key),
                }
            })
            .collect()
    }

    pub fn state(&self) -> Arc<WalletState> {
        self.state.load_full()
    }

    pub fn update_state(&self, state: WalletState) {
        self.state.store(Arc::new(state));
    }

    pub fn spend_key(&self) -> Zeroizing<MoneroBytes> {
        Zeroizing::new(From::from(**self.secrets.spend_key()))
    }

    pub fn view_key(&self) -> Zeroizing<MoneroBytes> {
        Zeroizing::new(From::from(**self.secrets.view_key()))
    }

    pub fn address(&self, subaddress_index: Option<SubaddressIndex>, network: Network) -> MoneroAddress {
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
}

#[cfg(test)]
mod tests {
    use crate::wallet::{io::WalletIoError, secrets::SecretsError};

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
        Wallet::create(monfluo_directory.path(), Network::Stagenet, WALLET_NAME, "", seed.clone(), 0)
            .await
            .unwrap();
        let wallet = Wallet::open(monfluo_directory.path(), WALLET_NAME, "")
            .await
            .unwrap();
        assert_eq!(seed.key(), wallet.spend_key())
    }

    #[tokio::test]
    async fn open_nonexisting_wallet_returns_io_error() {
        const WALLET_NAME: &str = "Monero Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let wallet_result = Wallet::open(monfluo_directory.path(), WALLET_NAME, "").await;
        assert!(matches!(wallet_result, Err(WalletError::Secrets(SecretsError::WalletIo(WalletIoError::Io(e)))) if e.kind() == std::io::ErrorKind::NotFound));
    }
}
