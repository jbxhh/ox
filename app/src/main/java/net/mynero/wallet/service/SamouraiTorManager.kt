package net.mynero.wallet.service

import android.app.Application
import android.util.Log
import androidx.lifecycle.MutableLiveData
import net.mynero.wallet.model.TorState
import org.json.JSONException
import org.json.JSONObject
import java.net.Proxy

class SamouraiTorManager(val appContext: Application?, val torKmpManager: TorKmpManager?) {
    fun getTorStateLiveData(): MutableLiveData<TorState> {
        return torKmpManager!!.torStateLiveData
    }

    fun getTorState(): TorState {
        return torKmpManager!!.torState
    }

    fun isConnected(): Boolean {
        return torKmpManager?.isConnected() ?: false
    }

    fun isStarting(): Boolean {
        return torKmpManager?.isStarting() ?: false
    }

    fun stop() {
        torKmpManager?.torOperationManager?.stopQuietly()
    }

    fun start() {
        torKmpManager?.torOperationManager?.startQuietly()
    }

    fun getProxy(): Proxy? {
        return torKmpManager?.proxy
    }

    fun newIdentity() {
        torKmpManager?.newIdentity(appContext!!)
    }

    companion object {
        private const val TAG = "SamouraiTorManager"
    }
}