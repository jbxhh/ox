#![deny(clippy::unwrap_used)]

pub mod crypto;
pub mod seed;
pub mod transport;
pub mod util;
pub mod wallet;

use std::sync::{LazyLock, RwLock, RwLockWriteGuard};

use rand::SeedableRng;

pub use monero_wallet;
pub use monero_wallet_util;
use rand_chacha::ChaCha20Rng;

pub const VERSION: &'static str = env!("CARGO_PKG_VERSION");

static RNG: LazyLock<RwLock<ChaCha20Rng>> = LazyLock::new(|| RwLock::new(ChaCha20Rng::from_entropy()));

fn get_rng_mut<'a>() -> RwLockWriteGuard<'a, ChaCha20Rng> {
    RNG.write().expect("failed to acquire write lock on RNG")
}

pub fn generate_seed(lang: monero_wallet_util::seed::original::Language) -> monero_wallet_util::seed::original::Seed {
    monero_wallet_util::seed::original::Seed::new(&mut *get_rng_mut(), lang)
}

pub fn generate_polyseed(lang: monero_wallet_util::seed::polyseed::Language) -> monero_wallet_util::seed::polyseed::Polyseed {
    monero_wallet_util::seed::polyseed::Polyseed::new(&mut *get_rng_mut(), lang)
}
