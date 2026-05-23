mod atomic_fs;
mod encryption;
mod io;
mod secrets;
mod state;
mod sync;
mod view;

use crate::transport::MonfluoTransport;
use crate::wallet::secrets::WalletSecrets;
use crate::wallet::state::{WalletOutputWrapper, WalletState};
use crate::wallet::view::transaction::{TransactionViewInput, TransactionViewSpend};
use arc_swap::{ArcSwap, ArcSwapOption};
use chrono::NaiveDateTime;
use curve25519_dalek::constants::ED25519_BASEPOINT_TABLE;
use monero_daemon_rpc::MoneroDaemon;
use monero_wallet::OutputWithDecoys;
use monero_wallet::address::Address;
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
use std::path::{Path, PathBuf};
use std::sync::Arc;
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

const WALLETS_DIRECTORY_FILE_NAME: &str = "wallets";
const WALLET_SECRETS_FILE_NAME: &str = "secrets.bin";
const WALLET_DATA_FILE_NAME: &str = "data.bin";

#[derive(Zeroize, ZeroizeOnDrop)]
pub struct Wallet {
    #[zeroize(skip)]
    path: PathBuf,
    secrets: WalletSecrets,
    #[zeroize(skip)]
    state: ArcSwap<WalletState>,
    #[zeroize(skip)]
    daemon: ArcSwapOption<MoneroDaemon<MonfluoTransport>>,
    #[zeroize(skip)]
    status: ArcSwap<WalletStatus>,
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
    BadSpendKey(Zeroizing<[u8; 32]>),
    #[error("Bad view key")]
    BadViewKey(Zeroizing<[u8; 32]>),
}

impl Wallet {
    pub async fn create<P: AsRef<Path>>(monfluo_directory: P, wallet_name: &str, password: &str, seed: Seed, birthday: Option<u64>, height: Option<u32>) -> Result<(), WalletError> {
        let key = seed.key();
        let spend_key = crate::util::bytes_to_scalar(&key).unwrap();
        let view_key = crate::util::bytes_to_scalar(&crate::crypto::spend_key_to_view_key(&key)).unwrap();
        let secrets = WalletSecrets::new(spend_key, view_key, 0, birthday.unwrap_or(0));
        let state = WalletState::new(height);
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
        let state = ArcSwap::new(Arc::new(WalletState::load(&state_file_path, password).await.unwrap()));

        Ok(Self {
            path: wallet_dir_path,
            secrets,
            state,
            daemon: ArcSwapOption::new(None),
            status: ArcSwap::new(Arc::new(WalletStatus::Connecting)),
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
        while let next_entry = directories.next_entry().await {
            match next_entry {
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

    pub fn status(&self) -> WalletStatus {
        **self.status.load()
    }

    pub(crate) fn set_status(&self, status: WalletStatus) {
        self.status.store(Arc::new(status));
    }

    // TODO: there is obviously a race, think of a better way to do that
    // Also, what if we have already connected to a daemon?
    pub async fn connect(&self, url: String, proxy: Option<reqwest::Proxy>) {
        self.set_status(WalletStatus::Connecting);
        let daemon = MonfluoTransport::new(url, proxy).await.unwrap();
        self.daemon.store(Some(Arc::new(daemon)));
        self.set_status(WalletStatus::Connected);
    }

    pub async fn refresh(self: Arc<Self>) -> bool {
        match sync::start_sync(self) {
            Some(future) => {
                future.await;
                true
            }
            None => false,
        }
    }

    pub fn refresh_async(self: Arc<Self>) -> bool {
        match sync::start_sync(self) {
            Some(future) => {
                tokio::spawn(future);
                true
            }
            None => false,
        }
    }

    pub async fn close(self) {}

    pub async fn create_transaction(&self, address: &str, amount: u64) -> SignableTransaction {
        let daemon = self
            .daemon
            .load_full()
            .expect("Failed to create transaction because there is no daemon");
        let state = self.state.load();
        let rct_type: RctType = RctType::ClsagBulletproofPlus;
        let outgoing_view_key: Zeroizing<[u8; 32]> = {
            let mut array: [u8; 32] = [0; 32];
            crate::get_rng_mut().fill_bytes(&mut array);
            Zeroizing::new(array)
        };
        let output = state.outputs.last().unwrap();
        let input: OutputWithDecoys = OutputWithDecoys::new(&mut *crate::get_rng_mut(), &*daemon, 16, state.height.unwrap().current.unwrap() as usize, output.inner.clone())
            .await
            .unwrap();
        let inputs: Vec<OutputWithDecoys> = vec![input];
        let payments: Vec<(monero_wallet::address::Address<_>, u64)> = vec![(Address::from_str(Network::Mainnet, address).unwrap(), amount)];
        let change: Change = Change::new(self.view_pair(), None);
        let data: Vec<Vec<u8>> = vec![];
        let fee_rate: FeeRate = daemon
            .fee_rate(monero_wallet::interface::FeePriority::Normal)
            .await
            .unwrap();
        SignableTransaction::new(rct_type, outgoing_view_key, inputs, payments, change, data, fee_rate).unwrap()
    }

    pub fn sign_tranasction(&self, transaction: SignableTransaction) -> Transaction {
        transaction
            .sign(&mut *crate::get_rng_mut(), self.secrets.spend_key())
            .unwrap()
    }

    pub async fn send_transaction(&self, transaction: Transaction) {
        let daemon = self
            .daemon
            .load_full()
            .expect("Failed to create transaction because there is no daemon");
        daemon.publish_transaction(&transaction).await.unwrap();
    }

    pub fn balance_view(&self) -> view::balance::BalanceView {
        let state = self.state.load();
        let mut total = 0;
        for output in state.outputs.iter() {
            if state
                .cache
                .key_image_to_spend_transaction_input
                .get(&output.cached_key_image)
                .is_none()
            {
                total += output.inner.commitment().amount;
            }
        }
        view::balance::BalanceView {
            total,
            unlocked: 0,
            pending: 0,
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

        for output in state.outputs.iter() {
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

    pub fn transactions_view(&self) -> Vec<view::transaction::TransactionView> {
        let state = self.state.load();

        let mut raw_transactions = state.transactions.clone();
        raw_transactions.sort_by_key(|raw_transaction| (raw_transaction.block_info.block.height, raw_transaction.hash));

        let mut wallet_inputs = HashMap::<[u8; 32], Vec<&WalletOutputWrapper>>::new();
        let mut wallet_outputs = HashMap::<[u8; 32], Vec<&WalletOutputWrapper>>::new();

        for output in state.outputs.iter() {
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

                let spends = wallet_outputs
                    .get(&raw_transaction.hash)
                    .map(Vec::as_slice)
                    .unwrap_or_default()
                    .iter()
                    .map(|wallet_output| TransactionViewSpend {
                        amount: wallet_output.inner.commitment().amount,
                    })
                    .collect();

                view::transaction::TransactionView {
                    transaction_hash: raw_transaction.hash,
                    timestamp: raw_transaction.block_info.timestamp,
                    fee: raw_transaction.inner.fee(),
                    inputs,
                    spends,
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

    pub fn spend_key(&self) -> Zeroizing<[u8; 32]> {
        Zeroizing::new(From::from(**self.secrets.spend_key()))
    }

    pub fn view_key(&self) -> Zeroizing<[u8; 32]> {
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
        let seed = monero_wallet_util::seed::Seed::Original(crate::generate_seed(monero_wallet_util::seed::original::Language::English));
        Wallet::create(monfluo_directory.path(), WALLET_NAME, "", seed.clone(), Some(1), Some(1))
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
