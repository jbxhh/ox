package org.monfluo.wallet.lib.data

data class AccountView(
    val accountIndex: Int,
    val addresses: List<AddressView>,
) {
    val balance: Balance =
        addresses
            .let { addresses ->
                Balance(
                    total = addresses.sumOf { it.balance.total },
                    unlocked = addresses.sumOf { it.balance.unlocked },
                    pending = addresses.sumOf { it.balance.pending },
                    frozen = addresses.sumOf { it.balance.frozen },
                )
            }
}
