package net.mynero.wallet.model

import net.mynero.wallet.service.PrefService
import net.mynero.wallet.util.Constants

class BalanceInfo(val rawUnlocked: Long, val rawLocked: Long) {

    val isUnlockedBalanceZero: Boolean
        get() = rawUnlocked == 0L
    val isLockedBalanceZero: Boolean
        get() = rawLocked == 0L
    val unlockedDisplay: String
        get() {
            val streetModeEnabled =
                PrefService.instance?.getBoolean(Constants.PREF_STREET_MODE, false)
            return if (streetModeEnabled == true) {
                Constants.STREET_MODE_BALANCE
            } else {
                Wallet.getDisplayAmount(rawUnlocked)
            }
        }
    val lockedDisplay: String
        get() {
            val streetModeEnabled =
                PrefService.instance?.getBoolean(Constants.PREF_STREET_MODE, false)
            return if (streetModeEnabled == true) {
                Constants.STREET_MODE_BALANCE
            } else {
                Wallet.getDisplayAmount(rawLocked)
            }
        }
}