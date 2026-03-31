package org.monfluo.wallet.lib.seed

class Polyseed : Seed<PolyseedLanguage> {
    override val type: SeedType = SeedType.POLYSEED

    companion object : SeedFactory<PolyseedLanguage, Polyseed> {
        external override fun generateSeed(): Polyseed
    }
}
