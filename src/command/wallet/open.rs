use std::{io::Write, path::PathBuf, sync::Arc};
use tokio::io::{AsyncBufReadExt, BufReader};

use monfluo_oxide::{
    transport::MonfluoTransport,
    wallet::{Daemon, Wallet},
};

use crate::command::wallet::MonfluoObserver;

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
            let balance = wallet.balance_view();
            println!("Balance: {}", monfluo_oxide::util::atomic_units_to_big_decimal(balance.total));
        } else if input == "address" {
            println!("Address: {:?}", wallet.address_view(0, 0));
        } else if input.starts_with("connect") {
            let url = input.split(' ').skip(1).next().unwrap();
            println!("Connecting to {}...", url);
            daemon = Some(Arc::new(MonfluoTransport::new(url.to_owned(), None).await.unwrap()));
            println!("Connected!");
        } else if input == "refresh" {
            if let Some(daemon) = &daemon {
                println!("Starting refresh");
                wallet.clone().sync(daemon.clone(), MonfluoObserver).await;
                println!("Refreshed!");
            } else {
                println!("Not connected!");
            }
        } else if input.starts_with("send") {
            if let Some(daemon) = &daemon {
                let mut params = input.split(' ').skip(1);
                let atomic_units = u64::from_str_radix(params.next().unwrap(), 10).unwrap(); // TODO: not atomic units but XMR
                let address = params.next().unwrap();
                let transaction = wallet
                    .create_transaction(address, atomic_units, daemon.clone())
                    .await;
                let signed_transaction = wallet.sign_tranasction(transaction);
                wallet
                    .send_transaction(signed_transaction, daemon.clone())
                    .await;
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
