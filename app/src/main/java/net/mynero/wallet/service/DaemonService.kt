package net.mynero.wallet.service

import net.mynero.wallet.data.Node
import net.mynero.wallet.livedata.SingleLiveEvent

class DaemonService {
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