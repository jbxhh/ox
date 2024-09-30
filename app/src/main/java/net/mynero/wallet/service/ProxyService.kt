package net.mynero.wallet.service

import android.app.Application
import android.util.Log
import android.util.Patterns
import net.mynero.wallet.livedata.SingleLiveEvent
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.util.Constants

class ProxyService(application: Application) : ServiceBase(null) {
    val proxyChangeEvents: SingleLiveEvent<String> = SingleLiveEvent()
    var samouraiTorManager: SamouraiTorManager? = null
    var usingProxy: Boolean = false
        get() = PrefService.instance.getBoolean(Constants.PREF_USES_PROXY, false)
        set(enabled) {
            PrefService.instance.edit().putBoolean(Constants.PREF_USES_PROXY, enabled).apply()
            field = enabled
        }
    var useBundledTor: Boolean = false
        get() = PrefService.instance.getBoolean(Constants.PREF_USE_BUNDLED_TOR, false)
        set(enabled) {
            PrefService.instance.edit().putBoolean(Constants.PREF_USE_BUNDLED_TOR, enabled).apply()
            field = enabled
        }

    init {
        instance = this
        samouraiTorManager = SamouraiTorManager(application, TorKmpManager(application))

        if (useBundledTor && usingProxy) {
            samouraiTorManager?.start()
        }
    }

    fun updateProxy(proxyAddress: String, proxyPort: String) {
        var finalProxyAddress = proxyAddress
        var finalProxyPort = proxyPort
        val curretNode = PrefService.instance.node
        val isNodeLocalIp =
            curretNode?.address?.startsWith("10.") == true || curretNode?.address?.startsWith("192.168.") == true || curretNode?.address == "localhost" || curretNode?.address == "127.0.0.1"
        curretNode?.trusted?.let { WalletManager.instance?.wallet?.setTrustedDaemon(it) }
        if (!usingProxy || isNodeLocalIp) {
            // User is not using proxy, or is using local node currently, so we will disable proxy here.
            proxyChangeEvents.postValue("")
            Log.d("ProxyService", "Not using proxy...")
            return
        }
        // We are using proxy at this point, but user set them to empty. We will fallback to Tor defaults here.
        if (proxyAddress.isEmpty()) finalProxyAddress = "127.0.0.1"
        if (proxyPort.isEmpty()) finalProxyPort = "9050"
        val validIpAddress = Patterns.IP_ADDRESS.matcher(finalProxyAddress).matches()
        if (validIpAddress) {
            val proxy = PrefService.instance.saveProxy(finalProxyAddress, finalProxyPort)
            proxy?.let { proxyChangeEvents.postValue(it) }
        }
    }

    private fun hasProxySet(): Boolean {
        val proxyString = proxy
        return proxyString?.contains(":") == true
    }

    val proxy: String?
        get() = PrefService.instance.getString(Constants.PREF_PROXY, "")

    val proxyAddress: String
        get() {
            if (hasProxySet() && usingProxy) {
                val proxyString = proxy
                return proxyString?.split(":".toRegex())?.dropLastWhile { it.isEmpty() }
                    ?.toTypedArray()
                    ?.get(0) ?: ""
            }
            return ""
        }
    val proxyPort: String
        get() {
            if (hasProxySet() && usingProxy) {
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