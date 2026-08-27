use std::sync::{Arc, atomic::AtomicBool};

use monero_wallet::{
    Scanner,
    address::SubaddressIndex,
    interface::{InterfaceError, NodeInfo, ProvidesNodeInfo, ProvidesUnvalidatedScannableBlocks, ScannableBlock},
    transaction::Input,
};
use tracing::{debug, info};

use crate::wallet::{Wallet, state::BlockIdentifier};

// max amount of blocks we attempt to fetch at once
const STEP: u32 = 1000;
// how many accounts we register in the scanner
// TODO: this should be a wallet setting, so that advanced users can override it to a higher value
const ACCOUNTS: u32 = 100;
// how many addresses we register per account in the scanner
// TODO: this should be a wallet setting, so that advanced users can override it to a higher value
const ADDRESSES: u32 = 1000;

#[derive(Debug)]
pub enum SyncError {
    CouldNotFetchNodeInfo { error: InterfaceError },
    NodeNotSynced { node_info: NodeInfo },
    CouldNotFetchBlocks { error: InterfaceError },
}

#[derive(Debug)]
pub enum WalletSyncState {
    Preparing { timestamp: u64, current_height: u32 },
    Starting { timestamp: u64, current_height: u32, target_height: u32, node_info: NodeInfo },
    Syncing { timestamp: u64, current_height: u32, target_height: u32 },
    Finished { timestamp: u64, height: u32 },
    Cancelled { timestamp: u64 },
    Failed { timestamp: u64, error: SyncError },
}

pub trait WalletSyncObserver {
    fn observe(&mut self, state: WalletSyncState);
}

// TODO: chain reorgs are currently not handled at all
pub async fn sync<O: WalletSyncObserver + Send>(wallet: Arc<Wallet>, daemon: super::Daemon, mut observer: O, cancellation_token: Arc<AtomicBool>) {
    let mut state = (*wallet.state()).clone();
    let wallet_height = state.height();
    let initial_height = wallet_height
        .current
        .unwrap_or(wallet_height.start.saturating_sub(1));
    observer.observe(WalletSyncState::Preparing {
        timestamp: current_timestamp(),
        current_height: initial_height,
    });

    let mut scanner = Scanner::new(wallet.view_pair());
    for account in 0..ACCOUNTS {
        let from_address = if account == 0 { 1 } else { 0 };
        for address in from_address..=ADDRESSES {
            scanner.register_subaddress(SubaddressIndex::new(account, address).expect("failed to construct subbaddress index during sync when registering addresses in scanner"));
        }
    }
    let node_info = match daemon.node_info().await {
        Ok(node_info) => node_info,
        Err(e) => {
            observer.observe(WalletSyncState::Failed {
                timestamp: current_timestamp(),
                error: SyncError::CouldNotFetchNodeInfo {
                    error: e,
                },
            });
            return;
        }
    };
    let node_height = node_info.height;
    let node_target_height = node_info.target_height;
    if node_target_height != 0 && node_height != node_target_height {
        observer.observe(WalletSyncState::Failed {
            timestamp: current_timestamp(),
            error: SyncError::NodeNotSynced {
                node_info,
            },
        });
        return;
    }
    let target_height = node_height - 1;
    info!("Will be syncing from current wallet height {} to the latest block number {}", initial_height, target_height);
    observer.observe(WalletSyncState::Starting {
        timestamp: current_timestamp(),
        current_height: initial_height,
        target_height,
        node_info,
    });

    let mut latest_synced_block = initial_height;
    let mut handle = Some(tokio::spawn({
        let daemon = daemon.clone();
        async move {
            daemon
                .contiguous_scannable_blocks((initial_height as usize + 1)..=(initial_height + STEP).min(target_height) as usize)
                .await
        }
    }));

    while let Some(current_handle) = handle.take() {
        if cancellation_token.load(std::sync::atomic::Ordering::Relaxed) {
            debug!("Sync cancelled");
            observer.observe(WalletSyncState::Cancelled {
                timestamp: current_timestamp(),
            });
        }
        debug!("Waiting on block fetching task..");
        let blocks = match current_handle
            .await
            .expect("Failed to spawn block fetching task")
        {
            Ok(blocks) => blocks,
            Err(e) => {
                observer.observe(WalletSyncState::Failed {
                    timestamp: current_timestamp(),
                    error: SyncError::CouldNotFetchBlocks {
                        error: e,
                    },
                });
                return;
            }
        };
        if blocks.is_empty() {
            info!("Fetched 0 blocks, stopping sync");
            break;
        }
        observer.observe(WalletSyncState::Syncing {
            timestamp: current_timestamp(),
            current_height: latest_synced_block,
            target_height,
        });
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
                    if let Some(subaddress_index) = output.subaddress() {
                        let account = subaddress_index.account();
                        let address = subaddress_index.address();
                        if state
                            .tracked_addresses
                            .get(&account)
                            .map_or(true, |&last_tracked_address| last_tracked_address <= address)
                        {
                            let new_last_tracked_address = address + 1;
                            info!("Updating last tracked address for account {} to {}", account, new_last_tracked_address);
                            state
                                .tracked_addresses
                                .insert(account, new_last_tracked_address);
                        }
                    }
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
            latest_synced_block = block_number;
            state.set_current_height(Some(block_number));
        }

        wallet.update_state(state.clone());
        info!("Finished processing batch of blocks");
        wallet.store().await;
        debug!("Wallet stored after processing batch of blocks");
        observer.observe(WalletSyncState::Syncing {
            timestamp: current_timestamp(),
            current_height: latest_synced_block,
            target_height,
        });
    }

    info!("Finished syncing");
    observer.observe(WalletSyncState::Finished {
        timestamp: current_timestamp(),
        height: latest_synced_block,
    });
}

fn current_timestamp() -> u64 {
    chrono::Local::now()
        .timestamp_millis()
        .try_into()
        .expect("failed to convert current timestamp into u64")
}
