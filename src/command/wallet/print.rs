use std::path::PathBuf;

use monfluo_oxide::{monero_wallet::address::SubaddressIndex, util::HexFormatted};

pub async fn run(monfluo_directory: PathBuf, name: String) {
    match monfluo_oxide::wallet::Wallet::open(monfluo_directory, &name, "").await {
        Ok(wallet) => {
            println!("Private spend key: {}", HexFormatted::borrowed(&*wallet.spend_key()));
            println!("Private view key: {}", HexFormatted::borrowed(&*wallet.view_key()));
            println!("First mainnet address: {}", wallet.address(None));
            println!("Second mainnet address: {}", wallet.address(SubaddressIndex::new(0, 1)));
            println!("Current height: {:?}", wallet.current_height().await);

            println!(">>>>>>>>>> Accounts >>>>>>>>>>");
            for account_view in wallet.accounts_view().await {
                println!("Account #{}", account_view.account_index);
                let last_used_address = account_view
                    .addresses
                    .iter()
                    .enumerate()
                    .rfind(|(_index, address)| address.used)
                    .map(|(index, _address)| index)
                    .unwrap_or(0);
                for address_view in account_view
                    .addresses
                    .into_iter()
                    .take(last_used_address + 1)
                {
                    println!("Address #{}.{}: {}", address_view.account_index, address_view.address_index, address_view.value);
                    println!("Used: {}, amount: {}", address_view.used, monfluo_oxide::util::atomic_units_to_big_decimal(address_view.amount));
                }
            }
            println!("<<<<<<<<<<");

            println!("Transactions\n");
            for transaction in wallet.transaction_views().await {
                println!("Transaction hash: {}", HexFormatted::borrowed(&transaction.transaction_hash));
                println!("Transaction fee: {}", monfluo_oxide::util::atomic_units_to_big_decimal(transaction.fee));
                println!(
                    "Transaction time: {}",
                    chrono::DateTime::from_timestamp_secs(transaction.timestamp as i64)
                        .unwrap()
                        .with_timezone(&chrono::Local)
                );
                println!("Inputs size: {}", transaction.inputs.len());
                for input in transaction.inputs {
                    println!("{} XMR to {}", monfluo_oxide::util::atomic_units_to_big_decimal(input.amount), input.address);
                }
                println!("Spends size: {}", transaction.spends.len());
                for output in transaction.spends {
                    println!("{} XMR", monfluo_oxide::util::atomic_units_to_big_decimal(output.amount));
                }
                println!();
            }
        }
        Err(e) => println!("Failed to open wallet: {e}"),
    }
}
