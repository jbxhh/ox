package org.monfluo.wallet.lib.wallet

import org.monfluo.wallet.lib.data.AccountView
import org.monfluo.wallet.lib.data.Balance
import org.monfluo.wallet.lib.data.TransactionView

class Wallet(
    @Suppress("unused") private val _handle: Long,
) {
    var currentAccountIndex: Int = 0

    external fun getName(): String

    external fun sync(
        daemon: Daemon,
        observer: WalletSyncObserver,
    )

    external fun getBalance(): Balance

    external fun getAccountView(accountIndex: Int): AccountView

    fun getAccountView(): AccountView = getAccountView(currentAccountIndex)

    external fun getTransactionViews(accountIndex: Int): List<TransactionView>

    fun getTransactionViews(): List<TransactionView> = getTransactionViews(currentAccountIndex)

    external fun registerNewAccount(): Int

    external fun registerNewAddress(account: Int): Int

    fun registerNewAddressForCurrentAccount(): Int = registerNewAddress(currentAccountIndex)

    external fun store()

    external fun close()
}
