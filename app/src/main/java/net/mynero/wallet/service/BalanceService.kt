package net.mynero.wallet.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import net.mynero.wallet.model.BalanceInfo

class BalanceService {
    private val _balanceInfo = MutableLiveData<BalanceInfo?>(null)
    var balanceInfo: LiveData<BalanceInfo?> = _balanceInfo

    init {
        instance = this
    }

    fun refreshBalance() {
        val rawUnlocked = unlockedBalanceRaw
        val rawLocked = lockedBalanceRaw
        _balanceInfo.postValue(BalanceInfo(rawUnlocked, rawLocked))
    }

    val unlockedBalanceRaw: Long
        get() {
            var unlocked: Long = 0
            val utxos = UTXOService.instance?.getUtxos() ?: emptyList()
            for (coinsInfo in utxos) {
                if (!coinsInfo.isSpent && !coinsInfo.isFrozen && coinsInfo.isUnlocked && UTXOService.instance?.isCoinFrozen(
                        coinsInfo
                    ) == false
                ) {
                    unlocked += coinsInfo.amount
                }
            }
            return unlocked
        }
    val totalBalanceRaw: Long
        get() {
            var total: Long = 0
            val utxos = UTXOService.instance?.getUtxos() ?: emptyList()
            for (coinsInfo in utxos) {
                if (!coinsInfo.isSpent && !coinsInfo.isFrozen && UTXOService.instance?.isCoinFrozen(
                        coinsInfo
                    ) == false
                ) {
                    total += coinsInfo.amount
                }
            }
            return total
        }
    val lockedBalanceRaw: Long
        get() = totalBalanceRaw - unlockedBalanceRaw

    companion object {
        var instance: BalanceService? = null
    }
}