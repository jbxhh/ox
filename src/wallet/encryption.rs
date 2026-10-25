// TODO: wallet secrets & data should be encrytped. Current encryption is no-op

use serde::{Serialize, de::DeserializeOwned};
use zeroize::Zeroizing;

#[derive(Debug, thiserror::Error)]
pub enum EncryptionError {
    #[error("Serde: {0}")]
    Serde(#[from] serde_json::Error),
}

pub(crate) fn seal<S: Serialize>(value: &S, password: &str) -> Result<Vec<u8>, EncryptionError> {
    let bytes = Zeroizing::new(serde_json::to_vec(value)?);
    encrypt(&bytes, password)
}

pub(crate) fn open<D: DeserializeOwned>(bytes: &[u8], password: &str) -> Result<D, EncryptionError> {
    let decrypted_bytes = decrypt(bytes, password)?;
    Ok(serde_json::from_slice(&decrypted_bytes)?)
}

pub(crate) fn encrypt(data: &[u8], _password: &str) -> Result<Vec<u8>, EncryptionError> {
    Ok(data.to_vec())
}

pub(crate) fn decrypt(data: &[u8], _password: &str) -> Result<Zeroizing<Vec<u8>>, EncryptionError> {
    Ok(Zeroizing::new(data.to_vec()))
}
