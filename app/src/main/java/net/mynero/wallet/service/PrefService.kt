package net.mynero.wallet.service

import android.content.Context
import android.content.SharedPreferences
import net.mynero.wallet.MoneroApplication
import net.mynero.wallet.data.DefaultNodes
import net.mynero.wallet.data.Node
import net.mynero.wallet.data.Node.Companion.fromJson
import net.mynero.wallet.data.Node.Companion.fromString
import net.mynero.wallet.util.Constants
import org.json.JSONException
import org.json.JSONObject

class PrefService(application: MoneroApplication) : ServiceBase(null) {
    init {
        preferences = application.getSharedPreferences(
            application.applicationInfo.packageName,
            Context.MODE_PRIVATE
        )
        instance = this
    }

    fun edit(): SharedPreferences.Editor? {
        return preferences?.edit()
    }

    val node: Node?
        get() {
            val usesProxy = getBoolean(Constants.PREF_USES_TOR, false)
            var defaultNode = DefaultNodes.SAMOURAI
            if (usesProxy) {
                val proxyPort = proxyPort
                if (proxyPort.isNotEmpty()) {
                    val port = proxyPort.toInt()
                    defaultNode = if (port == 4447) {
                        DefaultNodes.MYNERO_I2P
                    } else {
                        DefaultNodes.MYNERO_ONION
                    }
                }
            }
            val nodeString = getString(Constants.PREF_NODE_2, defaultNode.nodeString)
            return try {
                val nodeJson = nodeString?.let { JSONObject(it) }
                fromJson(nodeJson)
            } catch (e: JSONException) {
                // stored node is not json format, upgrade if possible
                nodeString?.let { upgradeOldNode(it) }
            }
        }

    private fun upgradeOldNode(nodeString: String): Node? {
        if (nodeString.isNotEmpty()) {
            val node = fromString(nodeString)
            if (node != null) {
                edit()?.putString(Constants.PREF_NODE_2, node.toJson().toString())?.apply()
                return node
            }
        }
        return null
    }

    val proxy: String?
        get() = instance?.getString(Constants.PREF_PROXY, "")

    fun hasProxySet(): Boolean {
        val proxyString = proxy
        return proxyString?.contains(":") == true
    }

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

    fun getString(key: String?, defaultValue: String): String? {
        val value = preferences?.getString(key, "")
        if (value?.isEmpty() == true && defaultValue.isNotEmpty()) {
            edit()?.putString(key, defaultValue)?.apply()
            return defaultValue
        }
        return value
    }

    fun getBoolean(key: String?, defaultValue: Boolean): Boolean {
        val containsKey = preferences?.contains(key)
        val value = preferences?.getBoolean(key, false)
        if (value == false && defaultValue && containsKey == false) {
            edit()?.putBoolean(key, true)?.apply()
            return true
        }
        return value == true
    }

    companion object {
        private var preferences: SharedPreferences? = null

        @JvmStatic
        var instance: PrefService? = null
            private set
    }
}