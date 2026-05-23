use std::path::PathBuf;

pub async fn run(monfluo_directory: PathBuf) {
    let wallets = monfluo_oxide::wallet::Wallet::list(monfluo_directory)
        .await
        .unwrap();

    if wallets.is_empty() {
        println!("No wallets found");
    } else {
        for (i, wallet) in wallets.into_iter().enumerate() {
            println!("#{}: {}", i + 1, wallet);
        }
    }
}
