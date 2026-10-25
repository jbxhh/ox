use zeroize::{Zeroize, ZeroizeOnDrop};

#[derive(Debug, Clone, Zeroize, ZeroizeOnDrop)]
pub enum WalletSeedView {
    Monero { seed: String, language: monero_wallet_util::seed::original::Language },
    Polyseed { seed: String, language: monero_wallet_util::seed::polyseed::Language },
}
