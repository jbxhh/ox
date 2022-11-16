package net.mynero.wallet.model;

import net.mynero.wallet.service.PrefService;
import net.mynero.wallet.util.Constants;

public class BalanceInfo {
    private final long rawUnlocked;
    private final long rawLocked;

    public BalanceInfo(long rawUnlocked, long rawLocked) {
        this.rawUnlocked = rawUnlocked;
        this.rawLocked = rawLocked;
    }

    public long getRawLocked() {
        return rawLocked;
    }

    public long getRawUnlocked() {
        return rawUnlocked;
    }

    public boolean isUnlockedBalanceZero() {
        return rawUnlocked == 0;
    }

    public boolean isLockedBalanceZero() {
        return rawLocked == 0;
    }

    public String getUnlockedDisplay() {
        boolean streetModeEnabled = PrefService.getInstance().getBoolean(Constants.PREF_STREET_MODE, false);
        if(streetModeEnabled) {
            return Constants.STREET_MODE_BALANCE;
        } else {
            return Wallet.getDisplayAmount(rawUnlocked);
        }
    }

    public String getLockedDisplay() {
        boolean streetModeEnabled = PrefService.getInstance().getBoolean(Constants.PREF_STREET_MODE, false);
        if(streetModeEnabled) {
            return Constants.STREET_MODE_BALANCE;
        } else {
            return Wallet.getDisplayAmount(rawLocked);
        }
    }
}