use std::sync::Arc;

use chrono::Local;
use monero_daemon_rpc::MoneroDaemon;
use monero_wallet::{
    Scanner,
    address::SubaddressIndex,
    interface::{ProvidesBlockchainMeta, ProvidesUnvalidatedScannableBlocks, ScannableBlock},
    transaction::Input,
};
use tracing::{debug, info, warn};

use crate::{
    transport::MonfluoTransport,
    wallet::{Wallet, WalletStatus, state::BlockIdentifier},
};

const STEP: u32 = 1000;

// the future must be awaited, otherwise the status is going to be wrong
#[must_use]
pub fn start_sync(wallet: Arc<Wallet>) -> Option<impl Future<Output = ()> + 'static> {
    let Some(daemon) = wallet.daemon.load_full() else {
        warn!("Cannot sync because there is no daemon available");
        return None;
    };
    let initial_wallet_status = wallet.status();
    if !matches!(initial_wallet_status, WalletStatus::Connected | WalletStatus::Synchronized { .. }) {
        warn!("Refusing to sync, because wallet status is neither connected nor synchronized");
        return None;
    }
    wallet.set_status(WalletStatus::StartingSynchronization);
    Some(sync(wallet, daemon))
}

async fn sync(wallet: Arc<Wallet>, daemon: Arc<MoneroDaemon<MonfluoTransport>>) {
    let mut state = (*wallet.state()).clone();
    let wallet_height = state
        .height()
        .map(|wallet_height| {
            wallet_height
                .current
                .unwrap_or(wallet_height.start.saturating_sub(1))
        })
        .unwrap_or(0);

    let mut scanner = Scanner::new(wallet.view_pair());
    for (&account, &max_address) in state.tracked_addresses() {
        let from_address = if account == 0 { 1 } else { 0 };
        for address in from_address..=max_address {
            scanner.register_subaddress(SubaddressIndex::new(account, address).expect("failed to construct subbaddress index during sync when registering addresses in scanner"));
        }
    }

    let target_height = daemon
        .latest_block_number()
        .await
        .expect("Failed to fetch latest block number") as u32;
    info!("Will be syncing from current wallet height {} to the latest block number {}", wallet_height, target_height);

    wallet.set_status(WalletStatus::Synchronizing {
        target: target_height,
        updated_at: Local::now().naive_local(),
    });

    let mut handle = Some(tokio::spawn({
        let daemon = daemon.clone();
        async move {
            daemon
                .contiguous_scannable_blocks((wallet_height as usize + 1)..=(wallet_height + STEP).min(target_height) as usize)
                .await
        }
    }));

    while let Some(current_handle) = handle.take() {
        debug!("Waiting on block fetching task..");
        let blocks = current_handle
            .await
            .expect("Failed to spawn block fetching task")
            .expect("Failed to fetch blocks");
        if blocks.is_empty() {
            info!("Fetched 0 blocks, stopping sync");
            break;
        } else {
            let first_block_height = blocks
                .first()
                .expect("Failed to get first block after checking that blocks vec is not empty")
                .block
                .number() as u32;
            let last_block_height = blocks
                .last()
                .expect("Failed to get last block after checking that blocks vec is not empty")
                .block
                .number() as u32;
            info!("Fetched {} blocks with heights {}-{}", blocks.len(), first_block_height, last_block_height);
            if last_block_height < target_height {
                debug!("Last block height ({}) is less than target block height ({}), spawning new block fetching task", last_block_height, target_height);
                handle = Some(tokio::spawn({
                    let daemon = daemon.clone();
                    async move {
                        daemon
                            .contiguous_scannable_blocks((last_block_height as usize + 1)..=(last_block_height + STEP).min(target_height) as usize)
                            .await
                    }
                }));
            }
        }

        for block in blocks {
            let block_number = block.block.number() as u32;
            let block_hash = block.block.hash();
            debug!("Processing block at height {} with hash {}", block_number, &*crate::util::bytes_to_hex(&block_hash));

            let block = ScannableBlock {
                block: block.block,
                transactions: block
                    .transactions
                    .into_iter()
                    .map(|tx| tx.as_ref().clone())
                    .collect(),
                output_index_for_first_ringct_output: block.output_index_for_first_ringct_output,
            };

            let result = scanner.scan(block.clone()).expect("Failed to scan a block");
            let outputs = result.ignore_additional_timelock();
            if !outputs.is_empty() {
                info!("Found {} outputs", outputs.len());
                for output in outputs {
                    let (transaction_hash, transaction) = block
                        .block
                        .transactions
                        .iter()
                        .zip(block.transactions.iter())
                        .find(|(transaction_hash, _transaction)| **transaction_hash == output.transaction())
                        .expect("Could not find transaction of a wallet output");
                    info!("Found output with relative id {} to address {:?} and amount {} in transaction {}", output.index_on_blockchain(), output.subaddress(), output.commitment().amount, *crate::util::bytes_to_hex(transaction_hash));
                    state.add_transaction(transaction.clone(), *transaction_hash, BlockIdentifier::new(block_number, block_hash), block.block.header.timestamp);
                    state.add_output(output, wallet.secrets.spend_key());
                }
            }

            for (transaction_hash, transaction) in block
                .block
                .transactions
                .iter()
                .zip(block.transactions.iter())
            {
                let (transaction, relevant_inputs) = state.add_transaction_if_spends_output(transaction.clone(), *transaction_hash, BlockIdentifier::new(block_number, block_hash), block.block.header.timestamp);
                if !relevant_inputs.is_empty() {
                    for input_index in relevant_inputs.keys().copied() {
                        let Input::ToKey {
                            ..
                        } = transaction.inner.prefix().inputs[input_index]
                        else {
                            panic!("Wallet returned bad relevant input index {input_index} for transaction {}", *crate::util::bytes_to_hex(transaction_hash));
                        };
                        info!("Found a spend in transaction {}", *crate::util::bytes_to_hex(transaction_hash));
                    }
                }
            }
            state.set_current_height(Some(block_number));
        }

        info!("Finished processing batch of blocks");
        wallet.update_state(state.clone());
        wallet.set_status(WalletStatus::Synchronizing {
            target: target_height,
            updated_at: Local::now().naive_local(),
        });
        wallet.store().await;
    }

    info!("Finished syncing");
    wallet.set_status(WalletStatus::Synchronized {
        finished_at: Local::now().naive_local(),
    });
}
