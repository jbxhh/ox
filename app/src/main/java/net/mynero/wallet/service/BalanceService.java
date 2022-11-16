package net.mynero.wallet.service;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import net.mynero.wallet.model.BalanceInfo;
import net.mynero.wallet.model.Wallet;
import net.mynero.wallet.model.WalletManager;
import net.mynero.wallet.util.Constants;

public class BalanceService extends ServiceBase {
    public static BalanceService instance = null;
    private final MutableLiveData<BalanceInfo> _balanceInfo = new MutableLiveData<>(null);
    public LiveData<BalanceInfo> balanceInfo = _balanceInfo;

    public BalanceService(MoneroHandlerThread thread) {
        super(thread);
        instance = this;
    }

    public static BalanceService getInstance() {
        return instance;
    }

    public void refreshBalance() {
        long rawUnlocked = getUnlockedBalanceRaw();
        long rawLocked = getLockedBalanceRaw();
        _balanceInfo.postValue(new BalanceInfo(rawUnlocked, rawLocked));
    }

    public long getUnlockedBalanceRaw() {
        return WalletManager.getInstance().getWallet().getUnlockedBalance();
    }

    public long getTotalBalanceRaw() {
        return WalletManager.getInstance().getWallet().getBalance();
    }

    public long getLockedBalanceRaw() {
        return getTotalBalanceRaw() - getUnlockedBalanceRaw();
    }
}
