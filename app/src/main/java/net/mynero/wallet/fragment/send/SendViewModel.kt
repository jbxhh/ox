package net.mynero.wallet.fragment.send;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.mynero.wallet.model.PendingTransaction;

public class SendViewModel extends ViewModel {
    private final MutableLiveData<Boolean> _sendingMax = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> _showAddOutputButton = new MutableLiveData<>(true);
    private final MutableLiveData<PendingTransaction> _pendingTransaction = new MutableLiveData<>(null);
    public LiveData<Boolean> sendingMax = _sendingMax;
    public LiveData<Boolean> showAddOutputButton = _showAddOutputButton;
    public LiveData<PendingTransaction> pendingTransaction = _pendingTransaction;

    public void setSendingMax(boolean value) {
        _sendingMax.setValue(value);
        setShowAddOutputButton(!value);
    }

    public void setShowAddOutputButton(boolean value) {
        _showAddOutputButton.setValue(value);
    }

    public void setPendingTransaction(PendingTransaction pendingTx) {
        _pendingTransaction.postValue(pendingTx);
    }
}