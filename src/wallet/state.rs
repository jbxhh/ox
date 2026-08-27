use std::{
    collections::{BTreeMap, HashMap},
    path::Path,
};

use monero_wallet::WalletOutput;
use monero_wallet::{
    ed25519::{CompressedPoint, Scalar},
    transaction::{Input, Pruned, Transaction},
};
use serde::{Deserialize, Serialize};
use zeroize::{Zeroize, ZeroizeOnDrop};

#[derive(Debug)]
pub enum Error {}

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct BlockIdentifier {
    pub height: u32,
    #[serde(serialize_with = "serde_utils::serialize_as_hex")]
    #[serde(deserialize_with = "serde_utils::deserialize_from_hex")]
    pub hash: [u8; 32],
}

impl BlockIdentifier {
    pub fn new(height: u32, hash: [u8; 32]) -> Self {
        Self {
            height,
            hash,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
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
    pub hash: [u8; 32],
    pub block_info: TransactionBlockInfo,
    pub first_seen_timestamp: u64,
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

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub struct WalletHeight {
    pub start: u32,
    pub current: Option<u32>,
}

impl WalletHeight {
    pub fn new(start: u32) -> Self {
        Self {
            start,
            current: None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
pub struct WalletState {
    #[zeroize(skip)]
    pub height: WalletHeight,
    #[zeroize(skip)]
    pub tracked_addresses: BTreeMap<u32, u32>,
    pub outputs: Vec<WalletOutputWrapper>,
    #[zeroize(skip)]
    pub transactions: Vec<TransactionWrapper>,
    #[serde(skip)]
    pub cache: WalletStateCache,
}

#[derive(Debug, Clone, Default, Zeroize, ZeroizeOnDrop)]
pub struct WalletStateCache {
    #[zeroize(skip)]
    pub key_image_to_output: HashMap<CompressedPoint, WalletOutputWrapper>,
    #[zeroize(skip)]
    pub key_image_to_spend_transaction_input: HashMap<CompressedPoint, ([u8; 32], usize)>,
    #[zeroize(skip)]
    pub hash_to_transaction: HashMap<[u8; 32], TransactionWrapper>,
}

impl WalletState {
    pub fn new(height: u32) -> Self {
        let mut tracked_addresses = BTreeMap::new();
        tracked_addresses.insert(0, 0);
        Self {
            height: WalletHeight::new(height),
            tracked_addresses,
            outputs: Vec::new(),
            transactions: Vec::new(),
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
        for transaction in state.transactions.clone().into_iter() {
            let (transaction, relevant_inputs) = state.find_transaction_relevant_inputs(transaction.inner.clone(), transaction.hash, transaction.block_info.block, transaction.block_info.timestamp);
            if !relevant_inputs.is_empty() {
                state.add_transaction_inner(transaction.clone(), transaction.hash, relevant_inputs.clone());
            }
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

    pub fn height(&self) -> WalletHeight {
        self.height
    }

    pub fn tracked_addresses(&self) -> &BTreeMap<u32, u32> {
        &self.tracked_addresses
    }

    pub fn outputs(&self) -> &Vec<WalletOutputWrapper> {
        &self.outputs
    }

    pub fn set_current_height(&mut self, height: Option<u32>) {
        self.height.current = height
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

    pub fn add_transaction(&mut self, transaction: Transaction<Pruned>, transaction_hash: [u8; 32], block: BlockIdentifier, timestamp: u64) {
        let transaction = TransactionWrapper {
            inner: transaction.clone(),
            hash: transaction_hash,
            block_info: TransactionBlockInfo {
                block,
                timestamp,
            },
            first_seen_timestamp: timestamp,
        };
        if !self
            .cache
            .hash_to_transaction
            .contains_key(&transaction_hash)
        {
            self.transactions.push(transaction.clone());
            self.cache
                .hash_to_transaction
                .insert(transaction_hash, transaction.clone());
        }
    }

    pub fn add_transaction_if_spends_output(&mut self, transaction: Transaction<Pruned>, transaction_hash: [u8; 32], block: BlockIdentifier, timestamp: u64) -> (TransactionWrapper, HashMap<usize, CompressedPoint>) {
        let (transaction, relevant_inputs) = self.find_transaction_relevant_inputs(transaction, transaction_hash, block, timestamp);
        if !relevant_inputs.is_empty() {
            if !self
                .cache
                .hash_to_transaction
                .contains_key(&transaction_hash)
            {
                self.transactions.push(transaction.clone());
            }
            self.add_transaction_inner(transaction.clone(), transaction_hash, relevant_inputs.clone());
        }
        (transaction, relevant_inputs)
    }

    fn add_transaction_inner(&mut self, transaction: TransactionWrapper, transaction_hash: [u8; 32], relevant_inputs: HashMap<usize, CompressedPoint>) {
        self.cache
            .hash_to_transaction
            .insert(transaction_hash, transaction.clone());
        for (input_index, key_image) in relevant_inputs {
            self.cache
                .key_image_to_spend_transaction_input
                .insert(key_image, (transaction_hash, input_index));
        }
    }

    fn find_transaction_relevant_inputs(&mut self, transaction: Transaction<Pruned>, transaction_hash: [u8; 32], block: BlockIdentifier, timestamp: u64) -> (TransactionWrapper, HashMap<usize, CompressedPoint>) {
        let mut relevant_inputs = HashMap::new();

        for (input_index, input) in transaction.prefix().inputs.iter().enumerate() {
            if let Input::ToKey {
                amount: _,
                key_offsets: _,
                key_image,
            } = input
                && self.cache.key_image_to_output.get(key_image).is_some()
            {
                relevant_inputs.insert(input_index, *key_image);
            }
        }

        let transaction = TransactionWrapper {
            inner: transaction.clone(),
            hash: transaction_hash,
            block_info: TransactionBlockInfo {
                block,
                timestamp,
            },
            first_seen_timestamp: timestamp,
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
        let hex: Zeroizing<String> = crate::util::bytes_to_hex(bytes.as_slice());
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
