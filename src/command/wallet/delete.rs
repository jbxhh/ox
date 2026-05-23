use std::path::PathBuf;

pub async fn run(monfluo_directory: PathBuf, name: String) {
    match monfluo_oxide::wallet::Wallet::delete(monfluo_directory, &name).await {
        Ok(true) => println!("Wallet '{}' deleted", name),
        Ok(false) => println!("Wallet '{}' does not exist", name),
        Err(e) => println!("Failed to delete wallet '{}': {}", name, e),
    }
}
