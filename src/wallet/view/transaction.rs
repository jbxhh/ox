use crate::MoneroHash;

#[derive(Debug, Clone)]
pub struct TransactionBlockInfoView {
    pub block_height: u32,
    pub block_hash: MoneroHash,
}

#[derive(Debug, Clone)]
pub struct TransactionView {
    pub transaction_hash: MoneroHash,
    pub timestamp: u64,
    pub fee: u64,
    pub confirmations: u32,
    pub block_info: Option<TransactionBlockInfoView>,
    pub inputs: Vec<TransactionViewInput>,
    pub spends: Vec<TransactionViewSpend>,
}

#[derive(Debug, Clone)]
pub struct TransactionViewInput {
    pub address: String,
    pub amount: u64,
}

#[derive(Debug, Clone)]
pub struct TransactionViewSpend {
    pub amount: u64,
}
