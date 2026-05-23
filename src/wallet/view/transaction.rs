#[derive(Debug, Clone)]
pub struct TransactionView {
    pub transaction_hash: [u8; 32],
    pub timestamp: u64,
    pub fee: u64,
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
