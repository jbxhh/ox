package org.monfluo.wallet.model

data class WalletStatusWithError(
    val status: WalletStatus,
    val error: String,
) {
    constructor(status: Int, error: String) : this(WalletStatus.entries[status], error)
}
