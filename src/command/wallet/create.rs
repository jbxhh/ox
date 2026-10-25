use std::path::PathBuf;

use monfluo_oxide::{monero_wallet::address::Network, monero_wallet_util::seed::Seed};

pub async fn run(monfluo_directory: PathBuf, name: String, password: String, seed: Option<Seed>, height: Option<u32>) {
    let seed_was_provided = seed.is_some();
    let seed = match seed {
        Some(seed) => seed,
        None => monfluo_oxide::monero_wallet_util::seed::Seed::Original(monfluo_oxide::generate_seed(monfluo_oxide::monero_wallet_util::seed::original::Language::English).await),
    };
    let seed_birthday = seed.birthday() * 1000;
    let (restore_height, birthday) = match height {
        Some(height) => (Some(height), seed_birthday),
        None if seed_birthday != 0 => (None, seed_birthday),
        None if seed_was_provided => (Some(0), 0),
        None => (None, monfluo_oxide::current_timestamp()),
    };
    match monfluo_oxide::wallet::Wallet::create(monfluo_directory, Network::Mainnet, &name, &password, seed, restore_height, birthday).await {
        Ok(()) => println!("Wallet created"),
        Err(e) => println!("Failed to create wallet: {e}"),
    }
}
