package org.monfluo.wallet.lib.seed

interface SeedLanguageUtils<L : SeedLanguage> {
    companion object {
        fun getUtils(seedType: SeedType): SeedLanguageUtils<*> =
            when (seedType) {
                SeedType.Monero -> MoneroSeedLanguage.Companion
                SeedType.Polyseed -> PolyseedLanguage.Companion
            }
    }

    fun getWords(language: L): Set<String>

    // fun getLanguage(word: String): L?
}
