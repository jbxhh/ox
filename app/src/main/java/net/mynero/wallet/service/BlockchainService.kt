package net.mynero.wallet.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import net.mynero.wallet.model.Wallet.ConnectionStatus
import net.mynero.wallet.model.WalletManager

class BlockchainService {
    private val _currentHeight = MutableLiveData(0L)
    var height: LiveData<Long> = _currentHeight
    var daemonHeight: Long = 0
        set(height) {
            val t = System.currentTimeMillis()
            if (height > 0) {
                field = height
                lastDaemonHeightUpdateTimeMs = t
            } else {
                if (t - lastDaemonHeightUpdateTimeMs > 15000) {
                    field = WalletManager.instance?.wallet?.getDaemonBlockChainHeight() ?: return
                    lastDaemonHeightUpdateTimeMs = t
                }
            }
        }
    private var lastDaemonHeightUpdateTimeMs: Long = 0

    init {
        instance = this
    }

    fun refreshBlockchain() {
        _currentHeight.postValue(currentHeight)
    }

    private val currentHeight: Long
        get() = WalletManager.instance?.wallet?.getBlockChainHeight() ?: -1

    companion object {
        var instance: BlockchainService? = null
    }
}