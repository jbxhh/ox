use std::path::PathBuf;

use monfluo_oxide::monero_wallet_util::seed::SeedType;

pub async fn run(_monfluo_directory: PathBuf, seed_type: SeedType) {
    match seed_type {
        SeedType::Original(language) => {
            let seed = monfluo_oxide::generate_seed(language).await;
            println!("{}", &*seed.to_string())
        }
        SeedType::Polyseed(language) => {
            let seed = monfluo_oxide::generate_polyseed(language).await;
            println!("{}", &*seed.to_string())
        }
    }
}
