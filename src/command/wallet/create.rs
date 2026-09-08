use std::path::PathBuf;

use monfluo_oxide::{monero_wallet::address::Network, monero_wallet_util::seed::Seed};

pub async fn run(monfluo_directory: PathBuf, name: String, password: String, seed: Option<Seed>, height: u32) {
    let seed = match seed {
        Some(seed) => seed,
        None => monfluo_oxide::monero_wallet_util::seed::Seed::Original(monfluo_oxide::generate_seed(monfluo_oxide::monero_wallet_util::seed::original::Language::English).await),
    };
    match monfluo_oxide::wallet::Wallet::create(monfluo_directory, Network::Mainnet, &name, &password, seed, height).await {
        Ok(()) => println!("Wallet created"),
        Err(e) => println!("Failed to create wallet: {e}"),
    }
}
