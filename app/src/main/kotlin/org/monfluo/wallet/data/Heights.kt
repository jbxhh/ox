package org.monfluo.wallet.data

data class Heights(
    val walletBeginSyncHeight: Long = 0,
    val walletHeight: Long = 0,
    val blockchainHeight: Long = 0,
    val blockchainTargetHeight: Long = 0,
)
