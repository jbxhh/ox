package net.mynero.wallet.service

import android.app.Application
import net.mynero.wallet.util.Constants

class ProxyService(application: Application) {
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