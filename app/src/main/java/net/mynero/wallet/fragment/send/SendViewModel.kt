package net.mynero.wallet.fragment.send

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import net.mynero.wallet.model.PendingTransaction

class SendViewModel : ViewModel() {
    private val _sendingMax = MutableLiveData(false)
    private val _showAddOutputButton = MutableLiveData(true)
    private val _pendingTransaction = MutableLiveData<PendingTransaction?>(null)
    var sendingMax: LiveData<Boolean?> = _sendingMax
    var showAddOutputButton: LiveData<Boolean?> = _showAddOutputButton
    var pendingTransaction: LiveData<PendingTransaction?> = _pendingTransaction
    fun setSendingMax(value: Boolean) {
        _sendingMax.value = value
        setShowAddOutputButton(!value)
    }

    fun setShowAddOutputButton(value: Boolean) {
        _showAddOutputButton.value = value
    }

    fun setPendingTransaction(pendingTx: PendingTransaction?) {
        _pendingTransaction.postValue(pendingTx)
    }
}