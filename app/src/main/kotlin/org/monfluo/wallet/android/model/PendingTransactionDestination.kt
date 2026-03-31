package org.monfluo.wallet.android.model

sealed interface PendingTransactionDestination {
    data class Default(
        val value: List<TransactionDestinationWithAmountResolved>,
    ) : PendingTransactionDestination

    data class Sweep(
        val value: TransactionDestinationResolved,
    ) : PendingTransactionDestination

    fun getResolvedDestinations(): List<TransactionDestinationResolved> =
        when (this) {
            is Default -> this.value.map { it.withoutAmount() }
            is Sweep -> listOf(this.value)
        }
}
