use std::path::Path;

use monero_wallet::ed25519::Scalar;
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

#[derive(Debug, thiserror::Error)]
pub enum SecretsError {
    #[error("Wallet io: {0}")]
    WalletIo(#[from] crate::wallet::io::WalletIoError),
}

#[derive(Debug, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
pub struct WalletSecrets {
    #[serde(serialize_with = "serialize_scalar")]
    #[serde(deserialize_with = "deserialize_scalar")]
    spend_key: Zeroizing<Scalar>,
    #[serde(serialize_with = "serialize_scalar")]
    #[serde(deserialize_with = "deserialize_scalar")]
    view_key: Zeroizing<Scalar>,
    // 0 mainnet 1 stagenet 2 testnet
    network: u8,
    birthday: u64,
}

impl WalletSecrets {
    pub fn new(spend_key: Zeroizing<Scalar>, view_key: Zeroizing<Scalar>, network: u8, birthday: u64) -> Self {
        Self {
            spend_key,
            view_key,
            network,
            birthday,
        }
    }

    pub async fn load(path: &Path, password: &str) -> Result<Self, SecretsError> {
        Ok(super::io::read_json_from_file(path, password).await?)
    }

    pub async fn store(&self, path: &Path, password: &str) -> Result<(), SecretsError> {
        let bytes = serde_json::to_vec(&self).unwrap();
        let encrypted_bytes = super::encryption::encrypt(bytes, password).unwrap();
        super::atomic_fs::write_atomically(path, &encrypted_bytes)
            .await
            .unwrap();
        Ok(())
    }

    pub fn spend_key(&self) -> &Zeroizing<Scalar> {
        &self.spend_key
    }

    pub fn view_key(&self) -> &Zeroizing<Scalar> {
        &self.view_key
    }
}

fn serialize_scalar<S>(value: &Zeroizing<Scalar>, serializer: S) -> Result<S::Ok, S::Error>
where
    S: Serializer,
{
    let bytes: Zeroizing<[u8; 32]> = Zeroizing::new(From::from(**value));
    let hex: Zeroizing<String> = crate::util::bytes_to_hex(&*bytes);
    serializer.serialize_str(&hex)
}

fn deserialize_scalar<'de, D>(deserializer: D) -> Result<Zeroizing<Scalar>, D::Error>
where
    D: Deserializer<'de>,
{
    let hex: Zeroizing<String> = Zeroizing::new(String::deserialize(deserializer)?);
    let bytes: Zeroizing<Vec<u8>> = crate::util::hex_to_bytes(&hex).unwrap();
    let bytes: Zeroizing<[u8; 32]> = Zeroizing::new((&**bytes).try_into().unwrap());
    let scalar: Zeroizing<Scalar> = Zeroizing::new(Scalar::from(
        curve25519_dalek::Scalar::from_canonical_bytes(*bytes)
            .into_option()
            .unwrap(),
    ));
    Ok(scalar)
}
