#![deny(clippy::unwrap_used)]

use clap::error::{ContextKind, ContextValue};
use clap::{
    Parser, Subcommand,
    error::{DefaultFormatter, Error, ErrorKind},
};
use directories::ProjectDirs;
use monfluo_oxide::seed::SeedType;
use monfluo_oxide::wallet::Wallet;
use std::fmt::Display;
use std::path::PathBuf;
use std::str::FromStr;
use std::sync::LazyLock;
use strum::VariantArray;
use tokio::runtime::Runtime;

static PROJECT_DIRS: LazyLock<ProjectDirs> = LazyLock::new(|| ProjectDirs::from("org.monfluo", "Monfluo", "monfluo").expect("Failed to initialize project dirs"));
static RUNTIME: LazyLock<Runtime> = LazyLock::new(|| tokio::runtime::Runtime::new().expect("Failed to create tokio runtime"));

#[derive(Parser)]
#[command(version, about, long_about = None)]
struct Cli {
    // TODO: quiet flag
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Generate seed
    GenerateSeed {
        #[arg(long)]
        seed_type: Option<SeedType>,
        #[arg(long)]
        seed_language: Option<String>,
    },
    /// Analyze seed
    AnalyzeSeed {
        #[arg(long)]
        r#type: Option<SeedType>,
        #[arg(long)]
        langugage: Option<String>,
        seed: String,
    },
    // List wallets
    ListWallets {
        #[arg(long)]
        root_dir: Option<PathBuf>,
    },
    /// Create new wallet
    CreateWallet {
        #[arg(long)]
        root_dir: Option<PathBuf>,
        #[arg(long)]
        name: String,
    },
    /// Open wallet in TUI
    OpenWallet {
        #[arg(long)]
        root_dir: Option<PathBuf>,
        #[arg(long)]
        name: String,
    },
    /// Start RPC server for wallet
    StartWallet {
        #[arg(long)]
        root_dir: Option<PathBuf>,
        #[arg(long)]
        name: String,
    },
}

pub fn main() {
    let cli = Cli::parse();

    match &cli.command {
        Commands::GenerateSeed { seed_type: r#type, seed_language: language } => {
            let seed_type = r#type.unwrap_or(SeedType::Monero);
            match seed_type {
                SeedType::Monero => {
                    let language = language
                        .as_ref()
                        .map(|language| parse_language_from_or_exit::<monfluo_oxide::monero_wallet_util::seed::original::Language>(language))
                        .unwrap_or(monfluo_oxide::monero_wallet_util::seed::original::Language::English);
                    println!("Generating seed...");
                    let seed = monfluo_oxide::generate_seed(language);
                    println!("{}", &*seed.to_string())
                }
                SeedType::Polyseed => {
                    // TODO: support creating encrypted polyseeds
                    let language = language
                        .as_ref()
                        .map(|language| parse_language_from_or_exit::<monfluo_oxide::monero_wallet_util::seed::polyseed::Language>(language))
                        .unwrap_or(monfluo_oxide::monero_wallet_util::seed::polyseed::Language::English);
                    println!("Generating seed...");
                    let seed = monfluo_oxide::generate_polyseed(language);
                    println!("{}", &*seed.to_string())
                }
            }
        }
        Commands::AnalyzeSeed { r#type, langugage, seed } => {
            println!("TODO")
        }
        Commands::ListWallets { root_dir } => {
            let monfluo_directory = root_dir.as_deref().unwrap_or(PROJECT_DIRS.data_dir());
            let wallets_directory = Wallet::get_wallets_directory(monfluo_directory);

            let wallets: Result<Vec<String>, std::io::Error> = RUNTIME.block_on(async {
                let mut directories = match tokio::fs::read_dir(wallets_directory).await {
                    Ok(directories) => directories,
                    Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(vec![]),
                    Err(e) => return Err(e),
                };
                let mut wallets = vec![];
                while let Some(wallet_directory) = directories.next_entry().await? {
                    if let Some(wallet) = wallet_directory.file_name().to_str() {
                        wallets.push(wallet.to_owned());
                    }
                }
                Ok(wallets)
            });

            match wallets {
                Ok(wallets) => {
                    if wallets.is_empty() {
                        println!("No wallets found");
                    } else {
                        for (i, wallet) in wallets.into_iter().enumerate() {
                            println!("#{}: {}", i + 1, wallet);
                        }
                    }
                }
                Err(e) => println!("Failed to list wallets: {e}"),
            }
        }
        Commands::CreateWallet { name, root_dir } => {
            println!("Creating wallet..");
            let monfluo_directory = root_dir.as_deref().unwrap_or(PROJECT_DIRS.data_dir());
            let seed = monfluo_oxide::generate_seed(monfluo_oxide::monero_wallet_util::seed::original::Language::English);
            let seed = monfluo_oxide::monero_wallet_util::seed::Seed::Original(seed);

            RUNTIME.block_on(async {
                match monfluo_oxide::wallet::Wallet::create(monfluo_directory, name, seed).await {
                    Ok(()) => println!("Wallet created"),
                    Err(e) => println!("Failed to create wallet: {e}"),
                }
            });
        }
        Commands::OpenWallet { name, root_dir } => {
            println!("Opening wallet..");
            let monfluo_directory = root_dir.as_deref().unwrap_or(PROJECT_DIRS.data_dir());
            RUNTIME.block_on(async {
                match monfluo_oxide::wallet::Wallet::open(monfluo_directory, name).await {
                    Ok(wallet) => {
                        println!("spend key: {}", monfluo_oxide::util::bytes_to_hex(&*wallet.spend_key()));
                        println!("view key: {}", monfluo_oxide::util::bytes_to_hex(&*wallet.view_key()));
                        println!("legacy mainnet address: {}", wallet.address(0, 0, monfluo_oxide::monero_wallet::address::Network::Mainnet));
                        println!("first mainnet address: {}", wallet.address(0, 1, monfluo_oxide::monero_wallet::address::Network::Mainnet));
                        println!("TODO");
                    }
                    Err(e) => println!("Failed to open wallet: {e}"),
                }
            });
        }
        Commands::StartWallet { root_dir, name } => {
            println!("TODO");
        }
    }
}

fn parse_language_from_or_exit<L: FromStr + VariantArray + Display>(language_str: &str) -> L {
    match L::from_str(language_str) {
        Ok(language) => language,
        Err(_) => {
            // FIXME: unlike standard clap errors, our custom error is not colored for some reason
            let mut err = Error::<DefaultFormatter>::new(ErrorKind::InvalidValue);
            err.insert(ContextKind::InvalidArg, ContextValue::String("--language <LANGUAGE>".to_string()));
            err.insert(ContextKind::InvalidValue, ContextValue::String(language_str.to_owned()));
            err.insert(ContextKind::ValidValue, ContextValue::Strings(L::VARIANTS.iter().map(ToString::to_string).collect()));
            err.exit()
        }
    }
}
