use std::path::PathBuf;

use monfluo_oxide::monero_wallet_util::seed::Seed;

pub async fn run(monfluo_directory: PathBuf, name: String, password: String, seed: Seed, height: u32) {
    match monfluo_oxide::wallet::Wallet::create(monfluo_directory, &name, &password, seed, None, height).await {
        Ok(()) => println!("Wallet created"),
        Err(e) => println!("Failed to create wallet: {e}"),
    }
}
