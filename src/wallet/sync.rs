use std::{
    collections::HashMap,
    sync::{Arc, atomic::AtomicBool},
};

use monero_daemon_rpc::MoneroDaemon;
use monero_wallet::{
    address::Network,
    interface::{BlocksRequest, InterfaceError, MempoolTransaction, NodeInfo, ProvidesNodeInfo, ScannableBlock, UnvalidatedScannableBlocksWithMempool},
};
use tracing::{debug, info, warn};

use crate::{
    current_timestamp_millis,
    transport::MonfluoTransport,
    util::HexFormatted,
    wallet::{
        TransactionWrapper, Wallet,
        scan::{create_scanner, process_scan_result, process_transaction},
        state::{BlockIdentifier, Blockchain, TransactionBlockInfo},
    },
};
#[derive(Debug)]
pub enum SyncError {
    CouldNotFetchNodeInfo { error: InterfaceError },
    NodeNotSynced { node_info: NodeInfo },
    CouldNotFetchBlocks { error: InterfaceError },
}

#[derive(Debug)]
pub enum WalletSyncState {
    Preparing { timestamp: u64, current_height: u32 },
    Starting { timestamp: u64, current_height: u32, node_info: NodeInfo },
    Syncing { timestamp: u64, current_height: u32 },
    Finished { timestamp: u64, height: u32 },
    Cancelled { timestamp: u64 },
    Failed { timestamp: u64, error: SyncError },
}

pub trait WalletSyncObserver {
    fn observe(&mut self, state: WalletSyncState);
}

fn make_blocks_request(network: Network, blockchain: &Blockchain) -> BlocksRequest {
    let hashes = blockchain.hashes();
    if hashes.is_empty() {
        return BlocksRequest::from_height(blockchain.start_block_number() as u64);
    }
    let genesis = match network {
        Network::Mainnet => crate::MAINNET_GENESIS_BLOCK_HASH_BYTES,
        Network::Stagenet => crate::STAGENET_GENESIS_BLOCK_HASH_BYTES,
        Network::Testnet => crate::TESTNET_GENESIS_BLOCK_HASH_BYTES,
    };
    BlocksRequest::from_hashes(genesis, hashes)
}

async fn fetch_blocks(daemon: Arc<MoneroDaemon<MonfluoTransport>>, blocks_request: BlocksRequest, pool_info_since: u64) -> Result<monero_wallet::interface::UnvalidatedScannableBlocksWithMempool, monero_wallet::interface::InterfaceError> {
    debug!("Fetching blocks using blocks request {blocks_request:?} and with pool info since {pool_info_since}");
    daemon
        .get_blocks_bin(blocks_request, monero_wallet::interface::RequestedInfo::BlocksAndMempool, pool_info_since, None)
        .await
}

pub async fn sync<O: WalletSyncObserver + Send>(wallet: Arc<Wallet>, daemon: super::Daemon, mut observer: O, cancellation_token: Arc<AtomicBool>) {
    let wallet_initial_height = wallet.read_state().await.current_height();
    let network = wallet.secrets.network();
    observer.observe(WalletSyncState::Preparing {
        timestamp: current_timestamp_millis(),
        current_height: wallet_initial_height,
    });

    let mut scanner = create_scanner(&*wallet.read_state().await, &wallet);
    let node_info = match daemon.node_info().await {
        Ok(node_info) => node_info,
        Err(e) => {
            observer.observe(WalletSyncState::Failed {
                timestamp: current_timestamp_millis(),
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
            timestamp: current_timestamp_millis(),
            error: SyncError::NodeNotSynced {
                node_info,
            },
        });
        return;
    }
    info!("Will be syncing from current wallet height {}", wallet_initial_height);
    observer.observe(WalletSyncState::Starting {
        timestamp: current_timestamp_millis(),
        current_height: wallet_initial_height,
        node_info,
    });

    let mut latest_synced_block = wallet_initial_height;
    let mut handle = {
        let state = wallet.read_state().await;
        let blocks_request = make_blocks_request(network, &state.blockchain);
        let task = tokio::spawn(fetch_blocks(daemon.clone(), blocks_request, state.cache.last_sync_daemon_time.unwrap_or(0)));
        Some(task)
    };
    while let Some(current_handle) = handle.take() {
        if cancellation_token.load(std::sync::atomic::Ordering::Relaxed) {
            debug!("Sync cancelled");
            observer.observe(WalletSyncState::Cancelled {
                timestamp: current_timestamp_millis(),
            });
            return;
        }
        debug!("Waiting on block fetching task..");
        let blocks_with_mempool = match current_handle
            .await
            .expect("Failed to spawn block fetching task")
        {
            Ok(blocks) => blocks,
            Err(e) => {
                observer.observe(WalletSyncState::Failed {
                    timestamp: current_timestamp_millis(),
                    error: SyncError::CouldNotFetchBlocks {
                        error: e,
                    },
                });
                return;
            }
        };
        let sync_timestamp = current_timestamp_millis();
        observer.observe(WalletSyncState::Syncing {
            timestamp: sync_timestamp,
            current_height: latest_synced_block,
        });

        let UnvalidatedScannableBlocksWithMempool {
            blocks,
            mempool,
            remaining_mempool_txids,
            removed_mempool_txids,
            daemon_time,
            incremental,
            current_height,
            start_height: _,
        } = blocks_with_mempool;

        // Process blocks
        let mut state = wallet.write_state().await;
        if blocks.is_empty() {
            info!("Fetched 0 blocks, stopping sync");
        } else {
            let last_local_blockchain_block = state.blockchain.last().copied();
            let first_block = blocks
                .first()
                .expect("Failed to get first block after checking that blocks vec is not empty");
            let first_block_height = first_block.block.number() as u32;
            let last_block_height = blocks
                .last()
                .expect("Failed to get last block after checking that blocks vec is not empty")
                .block
                .number() as u32;
            info!("Fetched {} blocks with heights {}-{}", blocks.len(), first_block_height, last_block_height);
            // when there are blocks to sync, the first returned block is the same as the last block in our blockchain unless re-org happened
            if let Some(last_blockchain_block_hash) = last_local_blockchain_block
                && last_blockchain_block_hash != first_block.block.hash()
            {
                panic!("TODO: re-orgs are not supported. First daemon block hash: {}, our last block hash: {}", HexFormatted::borrowed(&first_block.block.hash()), HexFormatted::borrowed(&last_blockchain_block_hash));
            }
            if (last_block_height as u64) < current_height - 1 {
                info!("Spawning next block fetching task");
                // we need to create a temporary new blockchain because we want to start fetching new blocks immediately,
                // before the current ones are processed and added to the actual wallet blockchain
                let mut fast_forwarded_blockchain = state.blockchain.clone();
                for block in blocks
                    .iter()
                    .skip_while(|block| Some(block.block.hash()) == last_local_blockchain_block)
                {
                    fast_forwarded_blockchain.push(block.block.hash());
                }
                handle = {
                    let blocks_request = make_blocks_request(network, &fast_forwarded_blockchain);
                    let task = tokio::spawn(fetch_blocks(daemon.clone(), blocks_request, daemon_time));
                    Some(task)
                };
            } else {
                info!("Fetched last block, stopping sync");
            }

            for block in blocks
                .into_iter()
                .skip_while(|block| Some(block.block.hash()) == last_local_blockchain_block)
            {
                let block_number = block.block.number() as u32;
                let block_hash = block.block.hash();
                debug!("Processing block at height {} with hash {}", block_number, HexFormatted::borrowed(&block_hash));

                let block = ScannableBlock {
                    block: block.block,
                    transactions: block
                        .transactions
                        .into_iter()
                        .map(|tx| tx.as_ref().clone())
                        .collect(),
                    output_index_for_first_ringct_output: block.output_index_for_first_ringct_output,
                };

                let block_timestamp = block.block.header.timestamp;
                let transaction_block_info = Some(TransactionBlockInfo {
                    block: BlockIdentifier::new(block_number, block_hash),
                    timestamp: block.block.header.timestamp,
                });
                let transactions = block
                    .block
                    .transactions
                    .iter()
                    .zip(block.transactions.iter())
                    .map(|(transaction_hash, transaction)| {
                        (
                            *transaction_hash,
                            TransactionWrapper {
                                inner: transaction.clone(),
                                hash: *transaction_hash,
                                block_info: transaction_block_info,
                                first_seen_timestamp: block_timestamp,
                                outgoing_transfer: None,
                            },
                        )
                    })
                    .collect::<HashMap<_, _>>();

                let timelocked = scanner.scan(block.clone()).expect("Failed to scan a block");
                process_scan_result(&mut state, wallet.secrets.spend_key(), timelocked, transactions.clone());
                for transaction in transactions.into_values() {
                    process_transaction(&mut state, transaction);
                }

                latest_synced_block = block_number;
                state.blockchain.push(block.block.hash());
            }
        }

        // Process mempool
        if !incremental {
            debug!("Daemon mempool is non incremental, clearing local mempool");
            state.cache.mempool_transactions.clear();
        } else {
            debug!("Daemon mempool is incremental");
        }
        debug!("{} transactions were removed from local mempool", removed_mempool_txids.len());
        for removed_mempool_txid in removed_mempool_txids {
            state
                .cache
                .mempool_transactions
                .remove(&removed_mempool_txid);
        }
        debug!("{} transactions in daemon mempool", mempool.len());
        for mempool_transaction in mempool {
            let MempoolTransaction {
                tx_hash: transaction_hash,
                transaction,
                double_spend_seen: _,
            } = mempool_transaction;
            let timelocked = match scanner.scan_transaction(0, transaction_hash, &transaction) {
                Ok(timelocked) => timelocked,
                Err(e) => {
                    warn!("Failed to scan mempool transaction: {e}");
                    continue;
                }
            };
            let mempool_transaction_timestamp = sync_timestamp / 1000;
            let transaction_wrapper = TransactionWrapper {
                inner: transaction.clone(),
                hash: transaction_hash,
                block_info: None,
                first_seen_timestamp: mempool_transaction_timestamp,
                outgoing_transfer: None,
            };
            let transactions = [(transaction_hash, transaction_wrapper.clone())]
                .into_iter()
                .collect();
            process_scan_result(&mut state, wallet.secrets.spend_key(), timelocked, transactions);
            process_transaction(&mut state, transaction_wrapper);
            state
                .cache
                .mempool_transactions
                .insert(transaction_hash, transaction);
        }
        debug!("{} transactions in local mempool", state.cache.mempool_transactions.len());
        debug!("{} remaining mempool transactions", remaining_mempool_txids.len());
        // TODO: fetch and process remaining_mempool_txids

        state.cache.last_sync_daemon_time = Some(daemon_time);
        drop(state);
        info!("Finished processing batch of blocks");
        wallet.store().await;
        debug!("Wallet stored after processing batch of blocks");
        observer.observe(WalletSyncState::Syncing {
            timestamp: current_timestamp_millis(),
            current_height: latest_synced_block,
        });
    }

    info!("Finished syncing");
    observer.observe(WalletSyncState::Finished {
        timestamp: current_timestamp_millis(),
        height: latest_synced_block,
    });
}
