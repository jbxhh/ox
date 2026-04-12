use curve25519_dalek::constants::ED25519_BASEPOINT_TABLE;
use monero_wallet::ed25519::{Point, Scalar};
use monero_wallet::{
    ViewPair,
    address::{MoneroAddress, Network, SubaddressIndex},
};
use monero_wallet_util::seed::Seed;
use std::path::{Path, PathBuf};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

const WALLETS_DIRECTORY_FILE_NAME: &str = "wallets";
const WALLET_SECRETS_FILE_NAME: &str = "secrets.bin";

#[derive(Zeroize, ZeroizeOnDrop)]
pub struct Wallet {
    #[zeroize(skip)]
    path: PathBuf,
    spend_key: Scalar,
    view_key: Scalar,
}

#[derive(Debug, thiserror::Error)]
pub enum WalletError {
    #[error("Io: {0}")]
    Io(#[from] std::io::Error),
    #[error("Bad wallet name: {0}")]
    BadWalletName(String),
    #[error("Bad spend key")]
    BadSpendKey(Zeroizing<[u8; 32]>),
    #[error("Bad view key")]
    BadViewKey(Zeroizing<[u8; 32]>),
}

impl Wallet {
    pub async fn create<P: AsRef<Path>>(monfluo_directory: P, wallet_name: &str, seed: Seed) -> Result<(), WalletError> {
        let wallet_dir_path = Self::get_wallet_directory(monfluo_directory, wallet_name)?;
        tokio::fs::create_dir_all(&wallet_dir_path).await?;
        let wallet_file_path = wallet_dir_path.join(WALLET_SECRETS_FILE_NAME);
        let mut file = tokio::fs::OpenOptions::new()
            .create_new(true)
            .write(true)
            .open(wallet_file_path)
            .await?;
        let spend_key: Zeroizing<[u8; 32]> = seed.entropy();
        let view_key: Zeroizing<[u8; 32]> = crate::crypto::spend_key_to_view_key(&spend_key);
        file.write_all(&*spend_key).await?;
        file.write_all(&*view_key).await?;
        file.flush().await?;
        Ok(())
    }

    pub async fn open<P: AsRef<Path>>(monfluo_directory: P, wallet_name: &str) -> Result<Self, WalletError> {
        let wallet_dir_path = Self::get_wallet_directory(monfluo_directory, wallet_name)?;
        let wallet_file_path = wallet_dir_path.join(WALLET_SECRETS_FILE_NAME);
        let mut file = tokio::fs::OpenOptions::new()
            .read(true)
            .open(&wallet_file_path)
            .await?;
        let mut spend_key: Zeroizing<[u8; 32]> = Zeroizing::new([0; 32]);
        let mut view_key: Zeroizing<[u8; 32]> = Zeroizing::new([0; 32]);
        file.read_exact(&mut *spend_key).await?;
        file.read_exact(&mut *view_key).await?;

        let spend_key = Scalar::from(
            curve25519_dalek::Scalar::from_canonical_bytes(*spend_key)
                .into_option()
                .ok_or(WalletError::BadSpendKey(spend_key))?,
        );
        let view_key = Scalar::from(
            curve25519_dalek::Scalar::from_canonical_bytes(*view_key)
                .into_option()
                .ok_or(WalletError::BadViewKey(view_key))?,
        );

        Ok(Self { path: wallet_file_path, spend_key, view_key })
    }

    pub async fn close(self) {}

    pub fn spend_key(&self) -> Zeroizing<[u8; 32]> {
        Zeroizing::new(From::from(self.spend_key))
    }

    pub fn view_key(&self) -> Zeroizing<[u8; 32]> {
        Zeroizing::new(From::from(self.view_key))
    }

    pub fn address(&self, account_index: u32, address_index: u32, network: Network) -> MoneroAddress {
        let subaddress_index = SubaddressIndex::new(account_index, address_index);
        if let Some(subaddress_index) = subaddress_index {
            self.view_pair().subaddress(network, subaddress_index)
        } else if account_index == 0 && address_index == 0 {
            self.view_pair().legacy_address(network)
        } else {
            panic!("Failed to to construct subaddress index for account index {account_index} and address index {address_index}")
        }
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
        ViewPair::new(Point::from(&self.spend_key.into() * ED25519_BASEPOINT_TABLE), Zeroizing::new(self.view_key)).expect("failed to create view pair")
    }
}

#[cfg(test)]
mod tests {
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
        Wallet::create(monfluo_directory.path(), WALLET_NAME, seed.clone())
            .await
            .unwrap();
        let wallet = Wallet::open(monfluo_directory.path(), WALLET_NAME)
            .await
            .unwrap();
        assert_eq!(seed.entropy(), wallet.spend_key())
    }

    #[tokio::test]
    async fn open_nonexisting_wallet_returns_io_error() {
        const WALLET_NAME: &str = "Monero Wallet";
        let monfluo_directory = tempfile::tempdir().expect("Failed to create tempdir");
        let wallet_result = Wallet::open(monfluo_directory.path(), WALLET_NAME).await;
        assert!(matches!(wallet_result, Err(WalletError::Io(e)) if e.kind() == std::io::ErrorKind::NotFound));
    }
}
