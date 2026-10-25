use std::{io::Write, path::PathBuf, sync::Arc};
use tokio::io::{AsyncBufReadExt, BufReader};

use monfluo_oxide::{
    monero_wallet::{address::MoneroAddress, interface::FeePriority},
    transport::MonfluoTransport,
    wallet::{Daemon, Payment, TransactionWrapper, Wallet},
};

use crate::{RUNTIME, command::wallet::MonfluoObserver};

pub async fn run(monfluo_directory: PathBuf, name: String, password: String) {
    match monfluo_oxide::wallet::Wallet::open(monfluo_directory, &name, &password).await {
        Ok(wallet) => {
            println!("Wallet opened");
            run_interactive(Arc::new(wallet)).await;
        }
        Err(e) => println!("Failed to open wallet: {e}"),
    }
}

async fn run_interactive(wallet: Arc<Wallet>) {
    let mut daemon: Option<Daemon> = None;
    loop {
        let input = match read_input(&wallet).await {
            Ok(input) => input,
            Err(e) => {
                println!("Failed to read input: {e}");
                break;
            }
        };
        if input == "exit" {
            println!("Exiting");
            break;
        } else if input == "balance" {
            let balance = wallet.balance_view().await;
            println!("Balance: {}", monfluo_oxide::util::atomic_units_to_big_decimal(balance.total));
        } else if input == "address" {
            let account_view = wallet.account_view(0).await;
            match account_view.addresses.first() {
                Some(address_view) => println!("Address: {}", address_view.value),
                None => println!("No addresses"),
            }
        } else if input.starts_with("connect") {
            let Some(url) = input.split(' ').nth(1) else {
                println!("Usage: connect <daemon url>");
                continue;
            };
            println!("Connecting to {}...", url);
            match MonfluoTransport::new(url.to_owned(), None).await {
                Ok(transport) => {
                    daemon = Some(Arc::new(transport));
                    println!("Connected!");
                }
                Err(e) => println!("Failed to connect: {e}"),
            }
        } else if input == "refresh" {
            if let Some(daemon) = &daemon {
                println!("Starting refresh");
                wallet
                    .clone()
                    .sync(&RUNTIME, daemon.clone(), MonfluoObserver);
                println!("Refreshed!");
            } else {
                println!("Not connected!");
            }
        } else if input.starts_with("send") {
            if let Some(daemon) = &daemon {
                let mut params = input.split(' ').skip(1);
                let (Some(atomic_units), Some(address)) = (params.next(), params.next()) else {
                    println!("Usage: send <atomic units> <address>");
                    continue;
                };
                let Ok(atomic_units) = atomic_units.parse::<u64>() else {
                    println!("Invalid amount: {}", atomic_units);
                    continue;
                };
                let network = monfluo_oxide::monero_wallet::address::Network::Mainnet;
                let Ok(address) = MoneroAddress::from_str(network, address) else {
                    println!("Invalid address: {}", address);
                    continue;
                };
                let (transaction, outgoing_transfer) = wallet
                    .create_transaction(
                        daemon.clone(),
                        vec![Payment {
                            address,
                            amount: atomic_units,
                        }],
                        FeePriority::Normal,
                    )
                    .await;
                let signed_transaction = wallet.sign_tranasction(transaction).await;
                let transaction_hash = signed_transaction.hash();
                let transaction = TransactionWrapper::new(signed_transaction, transaction_hash, None, monfluo_oxide::current_timestamp(), Some(outgoing_transfer));
                wallet.send_transaction(daemon.clone(), transaction).await;
                println!("Transaction sent!");
            } else {
                println!("Not connected!");
            }
        } else {
            println!("Unknown command");
        }
    }
}

async fn read_input(wallet: &Wallet) -> Result<String, std::io::Error> {
    print!("[Monero Wallet '{}'] > ", wallet.name().unwrap_or("???"));
    std::io::stdout().flush()?;
    let mut stdin = BufReader::new(tokio::io::stdin());
    let mut input = String::new();
    stdin.read_line(&mut input).await?;
    Ok(input.trim().to_owned())
}
