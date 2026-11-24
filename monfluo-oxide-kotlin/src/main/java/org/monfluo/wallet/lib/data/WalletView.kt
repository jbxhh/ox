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
            .flatMap { it.addresses }
            .let { addresses ->
                Balance(
                    total = addresses.sumOf { it.balance.total },
                    unlocked = addresses.sumOf { it.balance.unlocked },
                    pending = addresses.sumOf { it.balance.pending },
                    frozen = addresses.sumOf { it.balance.frozen },
                )
            }
}
