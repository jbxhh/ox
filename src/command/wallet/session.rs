use std::{sync::Arc, time::Duration};

use tokio::sync::Notify;

use clap::Parser;
use monfluo_oxide::{
    monero_wallet::{address::MoneroAddress, interface::FeePriority},
    transport::MonfluoTransport,
    wallet::{Daemon, Payment, TransactionWrapper, Wallet, WalletSyncState},
};

use crate::command::wallet::MonfluoObserver;

const REQUEST_TIMEOUT: Duration = Duration::from_secs(30);

#[derive(Parser, Debug)]
#[command(name = "wallet", no_binary_name = true, override_usage = "<COMMAND>")]
pub enum SessionCommands {
    /// Connect to a daemon
    Connect { url: String },
    /// Refresh wallet
    Refresh,
    /// Show balance
    Balance,
    /// Show first address
    Address,
    /// Send funds
    Send { amount: u64, address: String },
    /// Exit
    Exit,
}

pub fn parse(command: &str) -> Result<SessionCommands, clap::Error> {
    let Some(tokens) = shlex::split(command) else {
        return Err(clap::Error::raw(clap::error::ErrorKind::InvalidValue, "unbalanced quotes in command\n"));
    };
    SessionCommands::try_parse_from(tokens)
}

pub struct Session {
    pub wallet: Arc<Wallet>,
    daemon: Option<Daemon>,
}

impl Session {
    pub fn new(wallet: Arc<Wallet>) -> Self {
        Self {
            wallet,
            daemon: None,
        }
    }

    // returns false when the session should end
    pub async fn execute(&mut self, command: SessionCommands) -> bool {
        match command {
            SessionCommands::Connect {
                url,
            } => {
                println!("Connecting to {}...", url);
                match MonfluoTransport::new(url.to_owned(), None, None, REQUEST_TIMEOUT).await {
                    Ok(transport) => {
                        self.daemon = Some(Arc::new(transport));
                        println!("Connected!");
                    }
                    Err(e) => println!("Failed to connect: {e}"),
                }
            }
            SessionCommands::Refresh => {
                let Some(daemon) = &self.daemon else {
                    println!("Not connected!");
                    return true;
                };
                println!("Starting refresh");
                let cancel = Notify::new();
                let observer = MonfluoObserver::default();
                self.wallet
                    .sync(daemon.clone(), observer.clone(), &cancel)
                    .await;
                match observer.take_outcome() {
                    Some(WalletSyncState::Failed {
                        error,
                        ..
                    }) => println!("Refresh failed: {error:?}"),
                    Some(WalletSyncState::Cancelled {
                        ..
                    }) => println!("Refresh cancelled"),
                    _ => println!("Refreshed!"),
                }
            }
            SessionCommands::Balance => {
                let balance = self.wallet.balance_view().await;
                println!("Balance: {}", monfluo_oxide::util::atomic_units_to_big_decimal(balance.total));
            }
            SessionCommands::Address => {
                let account_view = self.wallet.account_view(0).await;
                match account_view.addresses.first() {
                    Some(address_view) => println!("Address: {}", address_view.value),
                    None => println!("No addresses"),
                }
            }
            SessionCommands::Send {
                amount,
                address,
            } => {
                let Some(daemon) = &self.daemon else {
                    println!("Not connected!");
                    return true;
                };
                let Ok(address) = MoneroAddress::from_str(self.wallet.network(), &address) else {
                    println!("Invalid address: {}", address);
                    return true;
                };
                let (transaction, outgoing_transfer) = match self
                    .wallet
                    .create_transaction(
                        daemon.clone(),
                        0,
                        vec![Payment {
                            address,
                            amount,
                        }],
                        FeePriority::Normal,
                    )
                    .await
                {
                    Ok(transaction) => transaction,
                    Err(e) => {
                        println!("Failed to create transaction: {e}");
                        return true;
                    }
                };
                let signed_transaction = match self.wallet.sign_transaction(transaction).await {
                    Ok(signed_transaction) => signed_transaction,
                    Err(e) => {
                        println!("Failed to sign transaction: {e}");
                        return true;
                    }
                };
                let transaction_hash = signed_transaction.hash();
                let transaction = TransactionWrapper::new(signed_transaction, transaction_hash, None, monfluo_oxide::current_timestamp(), Some(outgoing_transfer));
                match self
                    .wallet
                    .send_transaction(daemon.clone(), transaction)
                    .await
                {
                    Ok(()) => println!("Transaction sent!"),
                    Err(e) => println!("Failed to send transaction: {e}"),
                }
            }
            SessionCommands::Exit => {
                println!("Exiting");
                return false;
            }
        }
        true
    }
}
