package org.monfluo.wallet.android.data

import org.monfluo.wallet.android.R
import org.monfluo.wallet.lib.seed.SeedType

fun SeedType.next(): SeedType =
    when (this) {
        SeedType.Monero -> SeedType.Polyseed
        SeedType.Polyseed -> SeedType.Monero
    }

fun SeedType.getDescriptionResourceId(): Int =
    when (this) {
        SeedType.Monero -> R.string.seed_desc_legacy
        SeedType.Polyseed -> R.string.seed_desc_polyseed
    }
