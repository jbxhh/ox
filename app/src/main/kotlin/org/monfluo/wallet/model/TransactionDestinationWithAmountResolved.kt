package org.monfluo.wallet.model

data class TransactionDestinationWithAmountResolved(
    val address: String,
    val amount: Long,
    val resolvedFrom: TransactionDestinationResolvedFrom? = null,
) {
    fun withoutAmount(): TransactionDestinationResolved = TransactionDestinationResolved(address, resolvedFrom)
}
