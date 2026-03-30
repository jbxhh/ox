#![deny(clippy::unwrap_used)]

pub mod crypto;
pub mod util;

use std::sync::{LazyLock, RwLock, RwLockWriteGuard};

use monero_wallet_util::seed::{original::{Language, Seed}};
use rand::SeedableRng;

pub use monero_wallet;
pub use monero_wallet_util;
use rand_chacha::ChaCha20Rng;

static RNG: LazyLock<RwLock<ChaCha20Rng>> = LazyLock::new(|| RwLock::new(ChaCha20Rng::from_entropy()));

fn get_rng_mut<'a>() -> RwLockWriteGuard<'a, ChaCha20Rng> {
    RNG.write().expect("failed to acquire write lock on RNG")
}

pub fn generate_seed(lang: Language) -> Seed {
    Seed::new(&mut *get_rng_mut(), lang)
}
