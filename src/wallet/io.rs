use std::path::Path;

#[derive(Debug, thiserror::Error)]
pub enum WalletIoError {
    #[error("Io: {0}")]
    Io(#[from] std::io::Error),
    #[error("Atomic write: {0}")]
    AtomicWrite(#[from] crate::wallet::atomic_fs::AtomicWriteError),
}

pub async fn read(path: &Path) -> Result<Vec<u8>, WalletIoError> {
    Ok(tokio::fs::read(path).await?)
}

pub async fn write(path: &Path, data: &[u8]) -> Result<(), WalletIoError> {
    Ok(super::atomic_fs::write_atomically(path, data).await?)
}
