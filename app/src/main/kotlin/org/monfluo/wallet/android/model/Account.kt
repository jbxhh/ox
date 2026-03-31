package org.monfluo.wallet.android.model

data class Account(
    val index: Int,
    val label: String,
    val balance: Balance,
)
