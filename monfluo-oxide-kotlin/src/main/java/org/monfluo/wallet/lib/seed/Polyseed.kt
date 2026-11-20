package org.monfluo.wallet.lib.seed

class Polyseed private constructor(
    mnemonic: String,
    language: PolyseedLanguage,
) : Seed<PolyseedLanguage>(mnemonic, language) {
    override val type: SeedType = SeedType.Polyseed

    companion object : SeedFactory<PolyseedLanguage, Polyseed> {
        @JvmStatic
        external override fun generateSeed(language: PolyseedLanguage): Polyseed

        @JvmStatic
        external override fun fromMnemonic(
            text: String,
            language: PolyseedLanguage?,
        ): Polyseed?

        @JvmStatic
        fun fromMnemonic(text: String): Polyseed? = fromMnemonic(text, null)
    }
}
