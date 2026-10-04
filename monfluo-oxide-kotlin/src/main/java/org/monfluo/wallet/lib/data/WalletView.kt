package org.monfluo.wallet.lib.data

data class WalletView(
    val currentHeight: Int?,
    val restoreHeight: Int?,
    val adjustedRestoreHeight: Int?,
    val accounts: List<AccountView>,
    val transactions: List<TransactionView>,
    val balance: Balance,
)
