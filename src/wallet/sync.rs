use std::sync::Arc;

use monero_wallet::{
    Scanner,
    address::SubaddressIndex,
    interface::{ProvidesBlockchainMeta, ProvidesUnvalidatedScannableBlocks, ScannableBlock},
    transaction::Input,
};
use tracing::{debug, info};

use crate::{
    wallet::{Wallet, state::BlockIdentifier},
};

const STEP: u32 = 1000;
const ACCOUNTS: u32 = 100;
const ADDRESSES: u32 = 1000;

pub enum WalletSyncState {
    PreparingForSync,
    StartingSync {
        target_height: u32,
    },
    Syncing {
        target_height: u32,
    },
    FinishedSync,
}

pub trait WalletSyncObserver {
    fn observe(&mut self, state: WalletSyncState) -> impl Future<Output = ()>;
}

pub async fn sync<O: WalletSyncObserver>(wallet: Arc<Wallet>, daemon: super::Daemon, mut observer: O) {
    observer.observe(WalletSyncState::PreparingForSync).await;
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
    for account in 0..ACCOUNTS {
        let from_address = if account == 0 { 1 } else { 0 };
        for address in from_address..=ADDRESSES {
            scanner.register_subaddress(SubaddressIndex::new(account, address).expect("failed to construct subbaddress index during sync when registering addresses in scanner"));
        }
    }
    let target_height = daemon
        .latest_block_number()
        .await
        .expect("Failed to fetch latest block number") as u32;
    info!("Will be syncing from current wallet height {} to the latest block number {}", wallet_height, target_height);

    observer.observe(WalletSyncState::StartingSync { target_height }).await;
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
                    if let Some(subaddress_index) = output.subaddress() {
                        let account = subaddress_index.account();
                        let address = subaddress_index.address();
                        if state.tracked_addresses.get(&account).map_or(true, |&last_tracked_address| last_tracked_address <= address) {
                            let new_last_tracked_address = address + 1;
                            info!("Updating last tracked address for account {} to {}", account, new_last_tracked_address);
                            state.tracked_addresses.insert(account, new_last_tracked_address);
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
            state.set_current_height(Some(block_number));
        }

        wallet.update_state(state.clone());
        info!("Finished processing batch of blocks");
        wallet.store().await;
        debug!("Wallet stored after processing batch of blocks");
        observer.observe(WalletSyncState::Syncing { target_height }).await;
    }

    info!("Finished syncing");
    observer.observe(WalletSyncState::FinishedSync).await;
}
