package org.monfluo.wallet.lib.wallet

import org.monfluo.wallet.lib.data.Address
import org.monfluo.wallet.lib.data.FeePriority
import org.monfluo.wallet.lib.data.MoneroBytes
import org.monfluo.wallet.lib.data.Network
import org.monfluo.wallet.lib.data.Payment
import org.monfluo.wallet.lib.data.TransactionView
import org.monfluo.wallet.lib.data.WalletSeedView
import org.monfluo.wallet.lib.data.WalletView
import org.monfluo.wallet.lib.seed.MoneroSeed
import org.monfluo.wallet.lib.seed.MoneroSeedLanguage
import org.monfluo.wallet.lib.wallet.sync.WalletSyncObserver

class Wallet(
    @Suppress("unused") private val _handle: Long,
) {
    var currentAccountIndex: Int = 0

    external fun getName(): String

    external fun network(): Network

    external fun sync(
        daemon: Daemon,
        observer: WalletSyncObserver,
    )

    external fun getWalletView(): WalletView

    external fun getSeedView(): WalletSeedView?

    external fun getSpendKey(): MoneroBytes

    external fun getViewKey(): MoneroBytes

    external fun getPublicSpendKey(): MoneroBytes

    external fun getPublicViewKey(): MoneroBytes

    external fun getAddress(): String

    external fun getLegacySeed(
        language: MoneroSeedLanguage,
        seedOffsetPassphrase: String,
    ): MoneroSeed

    external fun registerNewAccount(): Int

    external fun registerNewAddress(account: Int): Int

    fun registerNewAddressForCurrentAccount(): Int = registerNewAddress(currentAccountIndex)

    external fun createAndSignTransaction(
        daemon: Daemon,
        accountIndex: Int,
        payments: List<Payment>,
        feePriority: FeePriority,
    ): TransactionView

    external fun createAndSignSweepTransaction(
        daemon: Daemon,
        accountIndex: Int,
        address: Address,
        feePriority: FeePriority,
    ): TransactionView

    external fun sendTransaction(
        daemon: Daemon,
        transactionHash: MoneroBytes,
    )

    external fun store()

    external fun close()
}
