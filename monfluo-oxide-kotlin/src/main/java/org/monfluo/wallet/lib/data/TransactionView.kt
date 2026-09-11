package org.monfluo.wallet.lib.data

import kotlin.math.absoluteValue

data class TransactionView(
    val transactionHash: MoneroBytes,
    val timestamp: Long,
    val fee: Long,
    val confirmations: Int,
    val blockInfo: TransactionBlockInfo?,
    val inputs: List<TransactionViewInput>,
    val spends: List<TransactionViewSpend>,
    val outgoingViewKey: MoneroBytes?,
) {
    val direction: TransactionDirection = if (spends.isNotEmpty()) TransactionDirection.SPEND else TransactionDirection.INPUT
    val fullAmount: Long = (inputs.sumOf { it.amount } - spends.sumOf { it.amount }).absoluteValue
    val amount: Long = if (direction == TransactionDirection.SPEND) fullAmount - fee else fullAmount
}

enum class TransactionDirection {
    INPUT,
    SPEND,
}
