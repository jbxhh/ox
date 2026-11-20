package org.monfluo.wallet.lib.seed

interface SeedFactory<L : SeedLanguage, S : Seed<L>> {
    companion object {
        fun getFactory(seedType: SeedType): SeedFactory<*, *> =
            when (seedType) {
                SeedType.Monero -> MoneroSeed.Companion
                SeedType.Polyseed -> Polyseed.Companion
            }
    }

    fun generateSeed(language: L): S

    fun fromMnemonic(
        text: String,
        language: L?,
    ): S?
}
