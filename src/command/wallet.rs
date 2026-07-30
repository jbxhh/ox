use monfluo_oxide::wallet::{WalletSyncObserver, WalletSyncState};

pub mod create;
pub mod delete;
pub mod list;
pub mod open;
pub mod print;
pub mod start;

pub struct MonfluoObserver;

impl WalletSyncObserver for MonfluoObserver {
    async fn observe(&mut self, _state: WalletSyncState) {}
}
