use monero_wallet::{address::Network, ed25519::Scalar};
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

use crate::{MoneroBytes, util::HexFormatted};

#[derive(Debug, thiserror::Error)]
pub enum SecretsError {
    #[error("Wallet io: {0}")]
    WalletIo(#[from] crate::wallet::io::WalletIoError),
    #[error("Encryption: {0}")]
    Encryption(#[from] crate::wallet::encryption::EncryptionError),
}

#[derive(Debug, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
#[serde(tag = "seed_type", rename_all = "snake_case")]
pub(crate) enum WalletSeed {
    Monero {
        seed: Zeroizing<String>,
        #[serde(serialize_with = "serialize_language", deserialize_with = "deserialize_language")]
        language: monero_wallet_util::seed::original::Language,
    },
    Polyseed {
        seed: Zeroizing<String>,
        #[serde(serialize_with = "serialize_language", deserialize_with = "deserialize_language")]
        language: monero_wallet_util::seed::polyseed::Language,
    },
}

impl WalletSeed {
    pub fn view(&self) -> crate::wallet::view::seed::WalletSeedView {
        match self {
            WalletSeed::Monero {
                seed,
                language,
            } => crate::wallet::view::seed::WalletSeedView::Monero {
                seed: seed.to_string(),
                language: *language,
            },
            WalletSeed::Polyseed {
                seed,
                language,
            } => crate::wallet::view::seed::WalletSeedView::Polyseed {
                seed: seed.to_string(),
                language: *language,
            },
        }
    }
}

#[derive(Debug, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
pub(crate) struct WalletSecrets {
    #[serde(serialize_with = "serialize_scalar")]
    #[serde(deserialize_with = "deserialize_scalar")]
    spend_key: Zeroizing<Scalar>,
    #[serde(serialize_with = "serialize_scalar")]
    #[serde(deserialize_with = "deserialize_scalar")]
    view_key: Zeroizing<Scalar>,
    #[serde(serialize_with = "serialize_network")]
    #[serde(deserialize_with = "deserialize_network")]
    network: Network,
    restore_height: Option<u32>,
    birthday: u64,
    seed: Option<WalletSeed>,
}

impl WalletSecrets {
    pub fn new(spend_key: Zeroizing<Scalar>, view_key: Zeroizing<Scalar>, network: Network, restore_height: Option<u32>, birthday: u64, seed: Option<WalletSeed>) -> Self {
        Self {
            spend_key,
            view_key,
            network,
            restore_height,
            birthday,
            seed,
        }
    }

    pub fn from_bytes(bytes: &[u8], password: &str) -> Result<Self, SecretsError> {
        Ok(super::encryption::open(bytes, password)?)
    }

    pub fn to_bytes(&self, password: &str) -> Result<Vec<u8>, SecretsError> {
        Ok(super::encryption::seal(self, password)?)
    }

    pub fn spend_key(&self) -> &Zeroizing<Scalar> {
        &self.spend_key
    }

    pub fn view_key(&self) -> &Zeroizing<Scalar> {
        &self.view_key
    }

    pub fn network(&self) -> Network {
        self.network
    }

    pub fn restore_height(&self) -> Option<u32> {
        self.restore_height
    }

    pub fn birthday(&self) -> u64 {
        self.birthday
    }

    pub fn seed(&self) -> Option<&WalletSeed> {
        self.seed.as_ref()
    }
}

fn serialize_language<S, L>(language: &L, serializer: S) -> Result<S::Ok, S::Error>
where
    S: Serializer,
    L: std::fmt::Display,
{
    serializer.serialize_str(&language.to_string())
}

fn deserialize_language<'de, D, L>(deserializer: D) -> Result<L, D::Error>
where
    D: Deserializer<'de>,
    L: std::str::FromStr,
    L::Err: std::fmt::Display,
{
    let language = String::deserialize(deserializer)?;
    L::from_str(&language).map_err(serde::de::Error::custom)
}

fn serialize_scalar<S>(value: &Zeroizing<Scalar>, serializer: S) -> Result<S::Ok, S::Error>
where
    S: Serializer,
{
    let bytes: Zeroizing<MoneroBytes> = Zeroizing::new(From::from(**value));
    let hex: Zeroizing<String> = Zeroizing::new(HexFormatted::borrowed(&*bytes).to_string());
    serializer.serialize_str(&hex)
}

fn deserialize_scalar<'de, D>(deserializer: D) -> Result<Zeroizing<Scalar>, D::Error>
where
    D: Deserializer<'de>,
{
    let hex: Zeroizing<String> = Zeroizing::new(String::deserialize(deserializer)?);
    let bytes: Zeroizing<Vec<u8>> = crate::util::hex_to_bytes(&hex).unwrap();
    let bytes: Zeroizing<MoneroBytes> = Zeroizing::new((&**bytes).try_into().unwrap());
    let scalar: Zeroizing<Scalar> = Zeroizing::new(Scalar::from(
        curve25519_dalek::Scalar::from_canonical_bytes(*bytes)
            .into_option()
            .unwrap(),
    ));
    Ok(scalar)
}

fn serialize_network<S>(value: &Network, serializer: S) -> Result<S::Ok, S::Error>
where
    S: Serializer,
{
    serializer.serialize_u8(*value as u8)
}

fn deserialize_network<'de, D>(deserializer: D) -> Result<Network, D::Error>
where
    D: Deserializer<'de>,
{
    let tag = u8::deserialize(deserializer)?;
    match tag {
        0 => Ok(Network::Mainnet),
        1 => Ok(Network::Stagenet),
        2 => Ok(Network::Testnet),
        _ => unreachable!("Bad network tag"),
    }
}
