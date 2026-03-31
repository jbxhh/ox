package org.monfluo.wallet.lib.seed

sealed interface Seed<L : SeedLanguage> {
    val type: SeedType
}
