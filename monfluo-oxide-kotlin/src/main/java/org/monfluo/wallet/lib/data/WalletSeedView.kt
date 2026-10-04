package org.monfluo.wallet.lib.data

import org.monfluo.wallet.lib.seed.MoneroSeedLanguage
import org.monfluo.wallet.lib.seed.PolyseedLanguage

sealed class WalletSeedView {
    data class Monero(
        val seed: String,
        val language: MoneroSeedLanguage,
    ) : WalletSeedView()

    data class Polyseed(
        val seed: String,
        val language: PolyseedLanguage,
    ) : WalletSeedView()
}
