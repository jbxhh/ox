package org.monfluo.wallet.android.model

data class Balance(
    var total: Long,
    var unlocked: Long,
    var frozen: Long,
    var locked: Long,
)
