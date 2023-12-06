package net.mynero.wallet.service

import net.mynero.wallet.model.PendingTransaction

class TxService(thread: MoneroHandlerThread) : ServiceBase(thread) {
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
        return thread?.createTx(address, amount, sendAll, feePriority, selectedUtxos)
    }

    @Throws(Exception::class)
    fun createTx(
        dests: List<Pair<String, String>>,
        sendAll: Boolean,
        feePriority: PendingTransaction.Priority,
        selectedUtxos: ArrayList<String>
    ): PendingTransaction? {
        return thread?.createTx(dests, sendAll, feePriority, selectedUtxos)
    }

    fun sendTx(pendingTransaction: PendingTransaction): Boolean {
        return thread?.sendTx(pendingTransaction) == true
    }

    companion object {
        @JvmField
        var instance: TxService? = null
    }
}