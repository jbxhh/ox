#![deny(clippy::unwrap_used)]

pub mod crypto;
pub mod seed;
pub mod transport;
pub mod util;
pub mod wallet;

use std::sync::LazyLock;

use rand::SeedableRng;

pub use monero_daemon_rpc;
pub use monero_wallet;
pub use monero_wallet_util;
use rand_chacha::ChaCha20Rng;
use tokio::sync::{RwLock, RwLockWriteGuard};

pub type MoneroBytes = [u8; 32];

pub const VERSION: &str = env!("CARGO_PKG_VERSION");

pub const MAINNET_GENESIS_BLOCK_HASH: &str = "418015bb9ae982a1975da7d79277c2705727a56894ba0fb246adaabb1f4632e3";
pub const MAINNET_GENESIS_BLOCK_HASH_BYTES: MoneroBytes = [65, 128, 21, 187, 154, 233, 130, 161, 151, 93, 167, 215, 146, 119, 194, 112, 87, 39, 165, 104, 148, 186, 15, 178, 70, 173, 170, 187, 31, 70, 50, 227];
pub const STAGENET_GENESIS_BLOCK_HASH: &str = "76ee3cc98646292206cd3e86f74d88b4dcc1d937088645e9b0cbca84b7ce74eb";
pub const STAGENET_GENESIS_BLOCK_HASH_BYTES: MoneroBytes = [118, 238, 60, 201, 134, 70, 41, 34, 6, 205, 62, 134, 247, 77, 136, 180, 220, 193, 217, 55, 8, 134, 69, 233, 176, 203, 202, 132, 183, 206, 116, 235];
pub const TESTNET_GENESIS_BLOCK_HASH: &str = "48ca7cd3c8de5b6a4d53d2861fbdaedca141553559f9be9520068053cda8430b";
pub const TESTNET_GENESIS_BLOCK_HASH_BYTES: MoneroBytes = [72, 202, 124, 211, 200, 222, 91, 106, 77, 83, 210, 134, 31, 189, 174, 220, 161, 65, 85, 53, 89, 249, 190, 149, 32, 6, 128, 83, 205, 168, 67, 11];

static RNG: LazyLock<RwLock<ChaCha20Rng>> = LazyLock::new(|| RwLock::new(ChaCha20Rng::from_entropy()));

async fn get_rng_mut<'a>() -> RwLockWriteGuard<'a, ChaCha20Rng> {
    RNG.write().await
}

pub async fn generate_seed(lang: monero_wallet_util::seed::original::Language) -> monero_wallet_util::seed::original::Seed {
    monero_wallet_util::seed::original::Seed::new(&mut *get_rng_mut().await, lang)
}

pub async fn generate_polyseed(lang: monero_wallet_util::seed::polyseed::Language) -> monero_wallet_util::seed::polyseed::Polyseed {
    monero_wallet_util::seed::polyseed::Polyseed::new(&mut *get_rng_mut().await, lang)
}

pub fn current_timestamp() -> u64 {
    chrono::Local::now()
        .timestamp_millis()
        .try_into()
        .expect("failed to convert current timestamp into u64")
}

#[cfg(test)]
mod tests {
    use zeroize::Zeroizing;

    use crate::{MAINNET_GENESIS_BLOCK_HASH, MAINNET_GENESIS_BLOCK_HASH_BYTES, STAGENET_GENESIS_BLOCK_HASH, STAGENET_GENESIS_BLOCK_HASH_BYTES, TESTNET_GENESIS_BLOCK_HASH, TESTNET_GENESIS_BLOCK_HASH_BYTES};

    #[test]
    fn verify_mainnet_genesis_block_hash_bytes() {
        assert_eq!(crate::util::hex_to_bytes(MAINNET_GENESIS_BLOCK_HASH), Some(Zeroizing::new(MAINNET_GENESIS_BLOCK_HASH_BYTES.into())))
    }

    #[test]
    fn verify_stagenet_genesis_block_hash_bytes() {
        assert_eq!(crate::util::hex_to_bytes(STAGENET_GENESIS_BLOCK_HASH), Some(Zeroizing::new(STAGENET_GENESIS_BLOCK_HASH_BYTES.into())))
    }

    #[test]
    fn verify_testnet_genesis_block_hash_bytes() {
        assert_eq!(crate::util::hex_to_bytes(TESTNET_GENESIS_BLOCK_HASH), Some(Zeroizing::new(TESTNET_GENESIS_BLOCK_HASH_BYTES.into())))
    }
}
