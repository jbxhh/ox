package net.mynero.wallet.service

import net.mynero.wallet.model.PendingTransaction

class TxService(val thread: MoneroHandlerThread) {
    init {
        instance = this
    }

    @Throws(Exception::class)
    fun createTx(
        address: String,
        amount: String,
        sendAll: Boolean,
        feePriority: PendingTransaction.Priority,
        selectedUtxos: ArrayList<String>
    ): PendingTransaction? {
        return thread.createTx(address, amount, sendAll, feePriority, selectedUtxos)
    }

    @Throws(Exception::class)
    fun createTx(
        dests: List<Pair<String, String>>,
        sendAll: Boolean,
        feePriority: PendingTransaction.Priority,
        selectedUtxos: ArrayList<String>
    ): PendingTransaction? {
        return thread.createTx(dests, sendAll, feePriority, selectedUtxos)
    }

    fun sendTx(pendingTransaction: PendingTransaction): Boolean {
        return thread.sendTx(pendingTransaction)
    }

    companion object {
        var instance: TxService? = null
    }
}