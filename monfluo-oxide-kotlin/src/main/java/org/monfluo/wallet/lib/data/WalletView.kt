package org.monfluo.wallet.lib.data

data class WalletView(
    val currentHeight: Int?,
    val restoreHeight: Int?,
    val adjustedRestoreHeight: Int?,
    val accounts: List<AccountView>,
    val transactions: List<TransactionView>,
) {
    val balance: Balance =
        accounts
            .let { accounts ->
                Balance(
                    total = accounts.sumOf { it.balance.total },
                    unlocked = accounts.sumOf { it.balance.unlocked },
                    pending = accounts.sumOf { it.balance.pending },
                    frozen = accounts.sumOf { it.balance.frozen },
                )
            }
}
