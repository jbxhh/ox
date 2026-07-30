use std::path::PathBuf;

use monfluo_oxide::monero_wallet::address::SubaddressIndex;

pub async fn run(monfluo_directory: PathBuf, name: String) {
    match monfluo_oxide::wallet::Wallet::open(monfluo_directory, &name, "").await {
        Ok(wallet) => {
            let state = wallet.state();
            println!("Private spend key: {}", *monfluo_oxide::util::bytes_to_hex(&*wallet.spend_key()));
            println!("Private view key: {}", *monfluo_oxide::util::bytes_to_hex(&*wallet.view_key()));
            println!("First mainnet address: {}", wallet.address(None, monfluo_oxide::monero_wallet::address::Network::Mainnet));
            println!("Second mainnet address: {}", wallet.address(SubaddressIndex::new(0, 1), monfluo_oxide::monero_wallet::address::Network::Mainnet));
            println!("Height: {:?}", wallet.state().height());

            println!(">>>>>>>>>> Accounts >>>>>>>>>>");
            for account_view in wallet.accounts_view() {
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

            let mut outputs = state.outputs().clone();
            outputs.sort_by_key(|output| output.inner.index_on_blockchain());

            println!("Outputs: {}", outputs.len());
            for output in outputs {
                println!(">>>>>>>>>>");
                println!("Output #{} in transaction {} to address {} with value of {}", output.inner.index_on_blockchain(), *monfluo_oxide::util::bytes_to_hex(&output.inner.transaction()), wallet.address(output.inner.subaddress(), monfluo_oxide::monero_wallet::address::Network::Mainnet), output.inner.commitment().amount);
                if let Some((spent_transaction_hash, _)) = state
                    .cache
                    .key_image_to_spend_transaction_input
                    .get(&output.cached_key_image)
                {
                    println!("Spent in transaction {}", *monfluo_oxide::util::bytes_to_hex(spent_transaction_hash))
                }
                println!("<<<<<<<<<<");
            }

            println!("Transactions\n");
            for transaction in wallet.transaction_views() {
                println!("Transaction hash: {}", *monfluo_oxide::util::bytes_to_hex(&transaction.transaction_hash));
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
