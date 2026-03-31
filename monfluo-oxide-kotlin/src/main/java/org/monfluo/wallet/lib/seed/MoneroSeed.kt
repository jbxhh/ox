package org.monfluo.wallet.lib.seed

class MoneroSeed : Seed<MoneroSeedLanguage> {
    override val type: SeedType = SeedType.MONERO

    companion object : SeedFactory<PolyseedLanguage, Polyseed> {
        external override fun generateSeed(): Polyseed
    }
}
