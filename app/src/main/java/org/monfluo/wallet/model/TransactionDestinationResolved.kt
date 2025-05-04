package org.monfluo.wallet.model

data class TransactionDestinationResolved(val address: String, val resolvedFrom: TransactionDestinationResolvedFrom? = null) {

    fun withAmount(amount: Long): TransactionDestinationWithAmountResolved {
        return TransactionDestinationWithAmountResolved(address, amount, resolvedFrom)
    }
}
