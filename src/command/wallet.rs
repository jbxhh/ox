use monfluo_oxide::wallet::{WalletSyncObserver, WalletSyncState};

pub mod create;
pub mod delete;
pub mod list;
pub mod open;
pub mod print;
pub mod start;

pub struct MonfluoObserver;

impl WalletSyncObserver for MonfluoObserver {
    fn observe(&mut self, state: WalletSyncState) {
        println!("Wallet state: {state:?}");
    }
}
