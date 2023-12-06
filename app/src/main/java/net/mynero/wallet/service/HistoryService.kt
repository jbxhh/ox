package net.mynero.wallet.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import net.mynero.wallet.model.TransactionInfo
import net.mynero.wallet.model.WalletManager

class HistoryService(thread: MoneroHandlerThread) : ServiceBase(thread) {
    private val _history = MutableLiveData<List<TransactionInfo>>()

    @JvmField
    var history: LiveData<List<TransactionInfo>> = _history

    init {
        instance = this
    }

    fun refreshHistory() {
        _history.postValue(getHistory())
    }

    private fun getHistory(): List<TransactionInfo> {
        return WalletManager.instance!!.wallet!!.history!!.all
    }

    companion object {
        @JvmStatic
        var instance: HistoryService? = null
            private set
    }
}