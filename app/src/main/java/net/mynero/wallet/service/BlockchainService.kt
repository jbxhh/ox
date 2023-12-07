package net.mynero.wallet.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import net.mynero.wallet.model.Wallet.ConnectionStatus
import net.mynero.wallet.model.WalletManager

class BlockchainService(thread: MoneroHandlerThread) : ServiceBase(thread) {
    private val _currentHeight = MutableLiveData(0L)
    private val _connectionStatus = MutableLiveData(ConnectionStatus.ConnectionStatus_Disconnected)
    var height: LiveData<Long> = _currentHeight
    var connectionStatus: LiveData<ConnectionStatus> = _connectionStatus
    var daemonHeight: Long = 0
        set(height) {
            val t = System.currentTimeMillis()
            if (height > 0) {
                field = height
                lastDaemonHeightUpdateTimeMs = t
            } else {
                if (t - lastDaemonHeightUpdateTimeMs > 120000) {
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

    fun setConnectionStatus(status: ConnectionStatus) {
        _connectionStatus.postValue(status)
    }

    companion object {
        var instance: BlockchainService? = null
    }
}