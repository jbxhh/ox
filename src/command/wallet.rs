use std::sync::{Arc, Mutex};

use monfluo_oxide::wallet::{WalletSyncObserver, WalletSyncState};
use tracing::info;

pub mod create;
pub mod delete;
pub mod execute;
pub mod list;
pub mod open;
pub mod print;
pub mod session;
pub mod start;

#[derive(Clone, Default)]
pub struct MonfluoObserver(Arc<Mutex<Option<WalletSyncState>>>);

impl MonfluoObserver {
    pub fn take_outcome(&self) -> Option<WalletSyncState> {
        self.0
            .lock()
            .expect("Failed to lock wallet sync state")
            .take()
    }
}

impl WalletSyncObserver for MonfluoObserver {
    fn observe(&mut self, state: WalletSyncState) {
        info!("Wallet state: {state:?}");
        *self.0.lock().expect("Failed to lock wallet sync state") = Some(state);
    }
}
