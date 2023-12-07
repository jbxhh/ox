package net.mynero.wallet.service

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import net.mynero.wallet.data.Node
import net.mynero.wallet.livedata.SingleLiveEvent
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.util.Constants

class DaemonService(thread: MoneroHandlerThread) : ServiceBase(thread) {
    val daemonChangeEvents: SingleLiveEvent<Node> = SingleLiveEvent()


    init {
        instance = this
    }

    fun setDaemon(daemon: Node) {
        daemonChangeEvents.postValue(daemon)
    }

    companion object {
        var instance: DaemonService? = null
            private set
    }
}