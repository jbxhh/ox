package org.monfluo.wallet.android.model

data class TransactionDestinationResolved(
    val address: String,
    val resolvedFrom: TransactionDestinationResolvedFrom? = null,
) {
    fun withAmount(amount: Long): TransactionDestinationWithAmountResolved = TransactionDestinationWithAmountResolved(address, amount, resolvedFrom)
}
