#![deny(clippy::unwrap_used)]

mod command;

use clap::error::{ContextKind, ContextValue};
use clap::{
    Parser, Subcommand,
    error::{DefaultFormatter, Error, ErrorKind},
};
use directories::ProjectDirs;
use monfluo_oxide::seed::SeedType;
use std::fmt::Display;
use std::path::PathBuf;
use std::str::FromStr;
use std::sync::LazyLock;
use strum::VariantArray;
use tokio::runtime::Runtime;
use tracing_subscriber::{EnvFilter, prelude::*};

static PROJECT_DIRS: LazyLock<ProjectDirs> = LazyLock::new(|| ProjectDirs::from("org.monfluo", "Monfluo", "monfluo").expect("Failed to initialize project dirs"));
static RUNTIME: LazyLock<Runtime> = LazyLock::new(|| tokio::runtime::Runtime::new().expect("Failed to create tokio runtime"));

// We manually initialize statics in order to achieve fail (panic) fast behaviour
fn initialize_statics() {
    LazyLock::force(&PROJECT_DIRS);
    LazyLock::force(&RUNTIME);
}

#[derive(Parser)]
#[command(version, about, long_about = None)]
struct Cli {
    #[arg(long)]
    root_dir: Option<PathBuf>,
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    Launch {},
    Seed {
        #[command(subcommand)]
        command: SeedCommands,
    },
    Blockchain {
        #[command(subcommand)]
        command: BlockchainCommands,
    },
    Wallet {
        #[command(subcommand)]
        command: WalletCommands,
    },
}

#[derive(Subcommand)]
enum SeedCommands {
    /// Generate seed
    Generate {
        #[arg(long)]
        r#type: Option<SeedType>,
        #[arg(long)]
        language: Option<String>,
    },
    /// Analyze seed
    Analyze {
        #[arg(long)]
        r#type: Option<SeedType>,
        #[arg(long)]
        langugage: Option<String>,
        seed: String,
    },
}

#[derive(Subcommand)]
enum BlockchainCommands {}

#[derive(Subcommand)]
enum WalletCommands {
    /// List wallets
    List,
    /// Create new wallet
    Create {
        #[arg(long)]
        height: Option<u32>,
        #[arg(long, default_value = "Mainnet")]
        network: monfluo_oxide::monero_wallet::address::Network,
        #[arg(long)]
        password: Option<String>,
        #[arg(long, value_parser = parse_seed)]
        seed: Option<monfluo_oxide::monero_wallet_util::seed::Seed>,
        #[arg(long)]
        seed_offset_passphrase: Option<String>,
        name: String,
    },
    /// Delets walet
    Delete { name: String },
    /// Prints wallet information
    Print {
        #[arg(long)]
        password: Option<String>,
        name: String,
    },
    /// Open wallet in TUI
    Open {
        #[arg(long)]
        password: Option<String>,
        name: String,
    },
    /// Execute commands on wallet
    Execute {
        #[arg(long)]
        password: Option<String>,
        name: String,
        #[arg(last = true, required = true)]
        commands: Vec<String>,
    },
    /// Start RPC server for wallet
    Start { name: String },
}

pub fn main() {
    initialize_statics();
    tracing_subscriber::registry()
        .with(tracing_subscriber::fmt::layer())
        .with(EnvFilter::from_env("MONFLUO_LOG"))
        .init();

    let cli = Cli::parse();
    let monfluo_directory = cli
        .root_dir
        .as_deref()
        .unwrap_or(PROJECT_DIRS.data_dir())
        .to_owned();

    match cli.command {
        Commands::Launch {
            ..
        } => {
            RUNTIME.block_on(command::launch::run(monfluo_directory));
        }
        Commands::Seed {
            command: SeedCommands::Generate {
                r#type,
                language,
            },
        } => {
            let r#type = r#type.unwrap_or(SeedType::Monero);
            let seed_type = match r#type {
                SeedType::Monero => monfluo_oxide::monero_wallet_util::seed::SeedType::Original(
                    language
                        .as_ref()
                        .map(|language| parse_language_from_or_exit::<monfluo_oxide::monero_wallet_util::seed::original::Language>(language))
                        .unwrap_or(monfluo_oxide::monero_wallet_util::seed::original::Language::English),
                ),
                SeedType::Polyseed => monfluo_oxide::monero_wallet_util::seed::SeedType::Polyseed(
                    language
                        .as_ref()
                        .map(|language| parse_language_from_or_exit::<monfluo_oxide::monero_wallet_util::seed::polyseed::Language>(language))
                        .unwrap_or(monfluo_oxide::monero_wallet_util::seed::polyseed::Language::English),
                ),
            };
            RUNTIME.block_on(command::seed::generate::run(monfluo_directory, seed_type));
        }
        Commands::Seed {
            command: SeedCommands::Analyze {
                ..
            },
        } => {
            RUNTIME.block_on(command::seed::analyze::run(monfluo_directory));
        }
        Commands::Wallet {
            command: WalletCommands::List,
        } => {
            RUNTIME.block_on(command::wallet::list::run(monfluo_directory));
        }
        Commands::Wallet {
            command: WalletCommands::Create {
                height,
                network,
                password,
                seed,
                seed_offset_passphrase,
                name,
            },
        } => {
            RUNTIME.block_on(command::wallet::create::run(monfluo_directory, network, name, password.unwrap_or_default(), seed, height, seed_offset_passphrase));
        }
        Commands::Wallet {
            command: WalletCommands::Delete {
                name,
            },
        } => {
            RUNTIME.block_on(command::wallet::delete::run(monfluo_directory, name));
        }
        Commands::Wallet {
            command: WalletCommands::Print {
                password,
                name,
            },
        } => {
            RUNTIME.block_on(command::wallet::print::run(monfluo_directory, name, password.unwrap_or_default()));
        }
        Commands::Wallet {
            command: WalletCommands::Open {
                password,
                name,
            },
        } => {
            RUNTIME.block_on(command::wallet::open::run(monfluo_directory, name, password.unwrap_or_default()));
        }
        Commands::Wallet {
            command: WalletCommands::Execute {
                password,
                name,
                commands,
            },
        } => {
            RUNTIME.block_on(command::wallet::execute::run(monfluo_directory, name, password.unwrap_or_default(), commands));
        }
        Commands::Wallet {
            command: WalletCommands::Start {
                name,
            },
        } => {
            RUNTIME.block_on(command::wallet::start::run(monfluo_directory, name));
        }
    }
}

fn parse_seed(seed: &str) -> Result<monfluo_oxide::monero_wallet_util::seed::Seed, String> {
    monfluo_oxide::seed::try_decode_seed(seed, None, None).ok_or_else(|| "invalid seed".to_owned())
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
