package net.mynero.wallet.service

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import net.mynero.wallet.data.Node
import net.mynero.wallet.livedata.SingleLiveEvent
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.util.Constants

class ProxyService(thread: MoneroHandlerThread) : ServiceBase(thread) {
    val proxyChangeEvents: SingleLiveEvent<String> = SingleLiveEvent()

    init {
        instance = this
    }

    fun updateProxy(proxyAddress: String, proxyPort: String) {
        var finalProxyAddress = proxyAddress
        var finalProxyPort = proxyPort
        val usesProxy = PrefService.instance?.getBoolean(Constants.PREF_USES_TOR, false) == true
        val curretNode = PrefService.instance?.node
        val isNodeLocalIp =
            curretNode?.address?.startsWith("10.") == true || curretNode?.address?.startsWith("192.168.") == true || curretNode?.address == "localhost" || curretNode?.address == "127.0.0.1"
        curretNode?.trusted?.let { WalletManager.instance?.wallet?.setTrustedDaemon(it) }
        if (!usesProxy || isNodeLocalIp) {
            // User is not using proxy, or is using local node currently, so we will disable proxy here.
            proxyChangeEvents.postValue("")
            return
        }
        // We are using proxy at this point, but user set them to empty. We will fallback to Tor defaults here.
        if (proxyAddress.isEmpty()) finalProxyAddress = "127.0.0.1"
        if (proxyPort.isEmpty()) finalProxyPort = "9050"
        val validIpAddress = Patterns.IP_ADDRESS.matcher(proxyAddress).matches()
        if (validIpAddress) {
            val proxy = "$finalProxyAddress:$finalProxyPort"
            PrefService.instance?.edit()?.putString(Constants.PREF_PROXY, proxy)?.apply()
            proxyChangeEvents.postValue(proxy)
        }
    }

    fun hasProxySet(): Boolean {
        val proxyString = proxy
        return proxyString?.contains(":") == true
    }

    val proxy: String?
        get() = PrefService.instance?.getString(Constants.PREF_PROXY, "")

    val proxyAddress: String
        get() {
            if (hasProxySet()) {
                val proxyString = proxy
                return proxyString?.split(":".toRegex())?.dropLastWhile { it.isEmpty() }
                    ?.toTypedArray()
                    ?.get(0) ?: ""
            }
            return ""
        }
    val proxyPort: String
        get() {
            if (hasProxySet()) {
                val proxyString = proxy
                return proxyString?.split(":".toRegex())?.dropLastWhile { it.isEmpty() }
                    ?.toTypedArray()
                    ?.get(1) ?: ""
            }
            return ""
        }

    companion object {
        var instance: ProxyService? = null
            private set
    }
}