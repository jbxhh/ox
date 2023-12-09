package net.mynero.wallet.service

import android.app.Application
import androidx.lifecycle.MutableLiveData
import net.mynero.wallet.model.TorState
import java.net.Proxy

class SamouraiTorManager(private val appContext: Application?, private val torKmpManager: TorKmpManager) {
    fun getTorStateLiveData(): MutableLiveData<TorState> {
        return torKmpManager.torStateLiveData
    }

    fun getTorState(): TorState {
        return torKmpManager.torState
    }

    fun isConnected(): Boolean {
        return torKmpManager.isConnected()
    }

    fun isStarting(): Boolean {
        return torKmpManager.isStarting()
    }

    fun stop() {
        torKmpManager.torOperationManager.stopQuietly()
    }

    fun start() {
        torKmpManager.torOperationManager.startQuietly()
    }

    fun getProxy(): Proxy? {
        return torKmpManager.proxy
    }

    fun newIdentity() {
        appContext?.let { torKmpManager.newIdentity(it) }
    }

    companion object {
        private const val TAG = "SamouraiTorManager"
    }
}