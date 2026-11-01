use std::path::PathBuf;

pub async fn run(monfluo_directory: PathBuf) {
    let wallets = match monfluo_oxide::wallet::Wallet::list(monfluo_directory).await {
        Ok(wallets) => wallets,
        Err(e) => {
            println!("Failed to list wallets: {e}");
            return;
        }
    };

    if wallets.is_empty() {
        println!("No wallets found");
    } else {
        for (i, wallet) in wallets.into_iter().enumerate() {
            println!("#{}: {}", i + 1, wallet);
        }
    }
}
