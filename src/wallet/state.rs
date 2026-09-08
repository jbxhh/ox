use std::{
    collections::{BTreeMap, HashMap, VecDeque},
    path::Path,
};

use monero_wallet::WalletOutput;
use monero_wallet::{
    ed25519::{CompressedPoint, Scalar},
    transaction::{Input, Pruned, Transaction},
};
use serde::{Deserialize, Serialize, Serializer};
use zeroize::{Zeroize, ZeroizeOnDrop};

use crate::MoneroHash;

#[derive(Debug)]
pub enum Error {}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[repr(transparent)]
pub struct SerializableBytes<const N: usize = 32> {
    pub bytes: [u8; N],
}

impl<const N: usize> SerializableBytes<N> {
    pub fn new(bytes: [u8; N]) -> Self {
        Self {
            bytes,
        }
    }

    pub fn transmute(bytes: &[u8; N]) -> &Self {
        // SAFETY: SerializableBytes is `#[repr(transparent)]`
        unsafe { std::mem::transmute(bytes) }
    }
}

impl<const N: usize> Serialize for SerializableBytes<N> {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serde_utils::serialize_as_hex(self, serializer)
    }
}

impl<'de, const N: usize> Deserialize<'de> for SerializableBytes<N> {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: serde::Deserializer<'de>,
    {
        serde_utils::deserialize_from_hex(deserializer)
    }
}

pub type TransactionHashBytes = SerializableBytes;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Blockchain {
    start_block_number: u32,
    hashes: VecDeque<MoneroHash>,
}

impl Blockchain {
    pub fn new(start_block_number: u32, hashes: &[MoneroHash]) -> Self {
        Self {
            start_block_number,
            hashes: hashes.iter().copied().collect(),
        }
    }

    pub fn start_block_number(&self) -> u32 {
        self.start_block_number
    }

    pub fn last(&self) -> Option<&MoneroHash> {
        self.hashes.back()
    }

    pub fn push(&mut self, hash: MoneroHash) {
        self.hashes.push_back(hash);
    }

    pub fn truncate(&mut self, size: usize) {
        if size == 0 {
            return;
        }
        while self.hashes.len() > size {
            let _ = self.hashes.pop_front();
            self.start_block_number += 1;
        }
    }

    pub fn as_sorted_slice(&mut self) -> &[MoneroHash] {
        self.hashes.make_contiguous()
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct BlockIdentifier {
    pub height: u32,
    #[serde(serialize_with = "serde_utils::serialize_as_hex")]
    #[serde(deserialize_with = "serde_utils::deserialize_from_hex")]
    pub hash: MoneroHash,
}

impl BlockIdentifier {
    pub fn new(height: u32, hash: MoneroHash) -> Self {
        Self {
            height,
            hash,
        }
    }
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub struct TransactionBlockInfo {
    pub block: BlockIdentifier,
    pub timestamp: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransactionWrapper {
    #[serde(serialize_with = "serde_utils::serialize_as_hex")]
    #[serde(deserialize_with = "serde_utils::deserialize_from_hex")]
    pub inner: Transaction<Pruned>,
    #[serde(serialize_with = "serde_utils::serialize_as_hex")]
    #[serde(deserialize_with = "serde_utils::deserialize_from_hex")]
    pub hash: MoneroHash,
    // None if the transaction is pending
    pub block_info: Option<TransactionBlockInfo>,
    // same as the block timestamp if synced from the blockchain, otherwise either the time we created this transaction or the time we first fetched it from the mempool
    pub first_seen_timestamp: u64,
}

impl TransactionWrapper {
    pub fn is_mempool_transaction(&self) -> bool {
        self.block_info.is_none()
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
pub struct WalletOutputWrapper {
    #[serde(serialize_with = "serde_utils::serialize_as_hex")]
    #[serde(deserialize_with = "serde_utils::deserialize_from_hex")]
    pub inner: WalletOutput,
    #[serde(serialize_with = "serde_utils::serialize_as_hex")]
    #[serde(deserialize_with = "serde_utils::deserialize_from_hex")]
    pub cached_key_image: CompressedPoint,
}

#[derive(Debug, Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
pub struct WalletState {
    #[zeroize(skip)]
    pub blockchain: Blockchain,
    #[zeroize(skip)]
    pub tracked_addresses: BTreeMap<u32, u32>,
    pub outputs: Vec<WalletOutputWrapper>,
    #[zeroize(skip)]
    pub transactions: HashMap<TransactionHashBytes, TransactionWrapper>,
    #[zeroize(skip)]
    #[serde(skip)]
    pub mempool_transactions: HashMap<MoneroHash, Transaction<Pruned>>,
    pub last_sync_daemon_time: Option<u64>,
    #[serde(skip)]
    pub cache: WalletStateCache,
}

#[derive(Debug, Clone, Default, Zeroize, ZeroizeOnDrop)]
pub struct WalletStateCache {
    #[zeroize(skip)]
    pub key_image_to_output: HashMap<CompressedPoint, WalletOutputWrapper>,
    #[zeroize(skip)]
    pub key_image_to_spend_transaction_input: HashMap<CompressedPoint, (MoneroHash, usize)>,
    #[zeroize(skip)]
    pub hash_to_transaction: HashMap<MoneroHash, TransactionWrapper>,
}

impl WalletState {
    pub fn new(restore_height: u32) -> Self {
        let mut tracked_addresses = BTreeMap::new();
        tracked_addresses.insert(0, 0);
        Self {
            blockchain: Blockchain::new(restore_height, &[]),
            tracked_addresses,
            outputs: Vec::new(),
            transactions: HashMap::new(),
            mempool_transactions: HashMap::new(),
            last_sync_daemon_time: None,
            cache: Default::default(),
        }
    }

    pub async fn load(path: &Path, password: &str) -> Result<Self, Error> {
        let bytes = tokio::fs::read(path).await.unwrap();
        let decrypted_bytes = super::encryption::decrypt(bytes, password).unwrap();
        let mut state: Self = serde_json::from_slice(&decrypted_bytes).unwrap();
        for output in state.outputs.clone().into_iter() {
            state.add_output_inner(output);
        }
        let mut transactions_to_add = Vec::new();
        for transaction in state.transactions.values() {
            let (transaction, relevant_inputs) = state.find_transaction_relevant_inputs(transaction.inner.clone(), transaction.hash, transaction.block_info, transaction.first_seen_timestamp);
            if !relevant_inputs.is_empty() {
                transactions_to_add.push((transaction, relevant_inputs));
            }
        }
        for (transaction_to_add, relevant_inputs) in transactions_to_add {
            state.add_transaction_inner(transaction_to_add.clone(), transaction_to_add.hash, relevant_inputs.clone());
        }
        Ok(state)
    }

    pub async fn store(&self, path: &Path, password: &str) -> Result<(), Error> {
        let bytes = serde_json::to_vec(&self).unwrap();
        let encrypted_bytes = super::encryption::encrypt(bytes, password).unwrap();
        super::atomic_fs::write_atomically(path, &encrypted_bytes)
            .await
            .unwrap();
        Ok(())
    }

    pub fn current_height(&self) -> u32 {
        self.blockchain.start_block_number + (self.blockchain.hashes.len() as u32).saturating_sub(1)
    }

    pub fn tracked_addresses(&self) -> &BTreeMap<u32, u32> {
        &self.tracked_addresses
    }

    pub fn outputs(&self) -> &Vec<WalletOutputWrapper> {
        &self.outputs
    }

    pub fn add_output(&mut self, output: WalletOutput, spend_key: &Scalar) {
        let cached_key_image = crate::crypto::key_image_of_output(&output, spend_key);
        let output = WalletOutputWrapper {
            inner: output,
            cached_key_image,
        };
        self.outputs.push(output.clone());
        self.add_output_inner(output);
    }

    fn add_output_inner(&mut self, output: WalletOutputWrapper) {
        self.cache
            .key_image_to_output
            .insert(output.cached_key_image, output);
    }

    pub fn add_transaction(&mut self, transaction: Transaction<Pruned>, transaction_hash: MoneroHash, transaction_block_info: Option<TransactionBlockInfo>, first_seen_timestamp: u64) {
        let transaction = TransactionWrapper {
            inner: transaction.clone(),
            hash: transaction_hash,
            block_info: transaction_block_info,
            first_seen_timestamp,
        };
        self.transactions
            .insert(TransactionHashBytes::new(transaction_hash), transaction.clone());
        self.cache
            .hash_to_transaction
            .insert(transaction_hash, transaction.clone());
    }

    pub fn add_transaction_if_spends_output(&mut self, transaction: Transaction<Pruned>, transaction_hash: MoneroHash, transaction_block_info: Option<TransactionBlockInfo>, first_seen_timestamp: u64) -> (TransactionWrapper, HashMap<usize, CompressedPoint>) {
        let (transaction, relevant_inputs) = self.find_transaction_relevant_inputs(transaction, transaction_hash, transaction_block_info, first_seen_timestamp);
        if !relevant_inputs.is_empty() {
            self.transactions
                .insert(TransactionHashBytes::new(transaction_hash), transaction.clone());
            self.add_transaction_inner(transaction.clone(), transaction_hash, relevant_inputs.clone());
        }
        (transaction, relevant_inputs)
    }

    fn add_transaction_inner(&mut self, transaction: TransactionWrapper, transaction_hash: MoneroHash, relevant_inputs: HashMap<usize, CompressedPoint>) {
        self.cache
            .hash_to_transaction
            .insert(transaction_hash, transaction.clone());
        for (input_index, key_image) in relevant_inputs {
            self.cache
                .key_image_to_spend_transaction_input
                .insert(key_image, (transaction_hash, input_index));
        }
    }

    fn find_transaction_relevant_inputs(&self, transaction: Transaction<Pruned>, transaction_hash: MoneroHash, transaction_block_info: Option<TransactionBlockInfo>, first_seen_timestamp: u64) -> (TransactionWrapper, HashMap<usize, CompressedPoint>) {
        let mut relevant_inputs = HashMap::new();

        for (input_index, input) in transaction.prefix().inputs.iter().enumerate() {
            if let Input::ToKey {
                amount: _,
                key_offsets: _,
                key_image,
            } = input
                && self.cache.key_image_to_output.contains_key(key_image)
            {
                relevant_inputs.insert(input_index, *key_image);
            }
        }

        let transaction = TransactionWrapper {
            inner: transaction.clone(),
            hash: transaction_hash,
            block_info: transaction_block_info,
            first_seen_timestamp,
        };

        (transaction, relevant_inputs)
    }
}

mod serde_utils {
    use monero_wallet::{
        WalletOutput,
        block::Block,
        ed25519::CompressedPoint,
        transaction::{Pruned, Transaction},
    };
    use serde::{Deserialize, Deserializer, Serializer};
    use std::io::Read;
    use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

    use crate::{util::HexFormatted, wallet::state::SerializableBytes};

    #[derive(Zeroize, ZeroizeOnDrop)]
    pub enum Bytes<'a, const N: usize = 0> {
        Vec(Vec<u8>),
        Array([u8; N]),
        #[zeroize(skip)]
        Slice(&'a [u8]),
    }

    impl<'a, const N: usize> Bytes<'a, N> {
        fn as_slice(&'a self) -> &'a [u8] {
            match self {
                Bytes::Vec(vec) => vec,
                Bytes::Array(array) => array,
                Bytes::Slice(slice) => slice,
            }
        }
    }

    pub trait SerializableToBytes<'a, const N: usize = 0> {
        fn serialize_to_bytes(&'a self) -> Bytes<'a, N>;
    }

    impl<'a, const N: usize> SerializableToBytes<'a, 0> for SerializableBytes<N> {
        fn serialize_to_bytes(&'a self) -> Bytes<'a> {
            Bytes::Slice(&self.bytes)
        }
    }

    impl<'a> SerializableToBytes<'a, 0> for WalletOutput {
        fn serialize_to_bytes(&'a self) -> Bytes<'a> {
            Bytes::Vec(self.serialize())
        }
    }

    impl<'a> SerializableToBytes<'a, 0> for Transaction<Pruned> {
        fn serialize_to_bytes(&'a self) -> Bytes<'a> {
            Bytes::Vec(self.serialize())
        }
    }

    impl<'a> SerializableToBytes<'a, 0> for Block {
        fn serialize_to_bytes(&'a self) -> Bytes<'a> {
            Bytes::Vec(self.serialize())
        }
    }

    impl<'a, const N: usize> SerializableToBytes<'a, N> for [u8; N] {
        fn serialize_to_bytes(&'a self) -> Bytes<'a, N> {
            Bytes::Slice(self)
        }
    }

    impl<'a> SerializableToBytes<'a, 32> for CompressedPoint {
        fn serialize_to_bytes(&'a self) -> Bytes<'a, 32> {
            Bytes::Array(self.to_bytes())
        }
    }

    pub trait DeserializableFromRead: Sized {
        fn read<R: Read>(r: &mut R) -> std::io::Result<Self>;
    }

    impl<const N: usize> DeserializableFromRead for SerializableBytes<N> {
        fn read<R: Read>(r: &mut R) -> std::io::Result<SerializableBytes<N>> {
            let mut bytes = [0; N];
            r.read_exact(&mut bytes)?;
            Ok(SerializableBytes {
                bytes,
            })
        }
    }

    impl DeserializableFromRead for WalletOutput {
        fn read<R: Read>(r: &mut R) -> std::io::Result<WalletOutput> {
            WalletOutput::read(r)
        }
    }

    impl DeserializableFromRead for Transaction<Pruned> {
        fn read<R: Read>(r: &mut R) -> std::io::Result<Transaction<Pruned>> {
            Transaction::<Pruned>::read(r)
        }
    }

    impl DeserializableFromRead for Block {
        fn read<R: Read>(r: &mut R) -> std::io::Result<Block> {
            Block::read(r)
        }
    }

    impl DeserializableFromRead for CompressedPoint {
        fn read<R: Read>(r: &mut R) -> std::io::Result<CompressedPoint> {
            CompressedPoint::read(r)
        }
    }

    impl<const N: usize> DeserializableFromRead for [u8; N] {
        fn read<R: Read>(r: &mut R) -> std::io::Result<[u8; N]> {
            let mut array: [u8; N] = [0; N];
            r.read_exact(&mut array)?;
            Ok(array)
        }
    }

    pub fn serialize_as_hex<'a, const N: usize, T: SerializableToBytes<'a, N>, S>(value: &'a T, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        let bytes: Bytes<'a, N> = value.serialize_to_bytes();
        let hex: Zeroizing<String> = Zeroizing::new(HexFormatted::borrowed(bytes.as_slice()).to_string());
        serializer.serialize_str(&hex)
    }

    pub fn deserialize_from_hex<'de, T: DeserializableFromRead, D>(deserializer: D) -> Result<T, D::Error>
    where
        D: Deserializer<'de>,
    {
        let hex: Zeroizing<String> = Zeroizing::new(String::deserialize(deserializer)?);
        let bytes: Zeroizing<Vec<u8>> = crate::util::hex_to_bytes(&hex).unwrap();
        let wallet_output = T::read(&mut bytes.as_slice()).unwrap();
        Ok(wallet_output)
    }
}
