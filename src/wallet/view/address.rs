use crate::wallet::view::balance::BalanceView;

#[derive(Debug, Clone)]
pub struct AddressView {
    pub account_index: u32,
    pub address_index: u32,
    pub value: String,
    pub used: bool,
    pub balance: BalanceView,
}
