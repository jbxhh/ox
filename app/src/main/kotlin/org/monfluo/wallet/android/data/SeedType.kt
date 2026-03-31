package org.monfluo.wallet.android.data

import org.monfluo.wallet.android.R

enum class SeedType {
    MONERO,
    POLYSEED,
    ;

    fun getDescriptionResourceId(): Int =
        when (this) {
            MONERO -> R.string.seed_desc_legacy
            POLYSEED -> R.string.seed_desc_polyseed
        }
}
