package net.mynero.wallet.fragment.send;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.mynero.wallet.model.PendingTransaction;

public class SendViewModel extends ViewModel {
    private final MutableLiveData<Boolean> _sendingMax = new MutableLiveData<>(false);
    public LiveData<Boolean> sendingMax = _sendingMax;
    private final MutableLiveData<PendingTransaction> _pendingTransaction = new MutableLiveData<>(null);
    public LiveData<PendingTransaction> pendingTransaction = _pendingTransaction;

    public void setSendingMax(boolean value) {
        _sendingMax.setValue(value);
    }

    public void setPendingTransaction(PendingTransaction pendingTx) {
        _pendingTransaction.postValue(pendingTx);
    }
}