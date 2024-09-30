package net.mynero.wallet.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import net.mynero.wallet.MoneroApplication
import net.mynero.wallet.data.DefaultNodes
import net.mynero.wallet.data.Node
import net.mynero.wallet.data.Node.Companion.fromJson
import net.mynero.wallet.data.Node.Companion.fromString
import net.mynero.wallet.util.Constants
import org.json.JSONException
import org.json.JSONObject

class PrefService(application: MoneroApplication) : ServiceBase(null) {

    private val preferences = application.getSharedPreferences(
        application.applicationInfo.packageName,
        Context.MODE_PRIVATE
    )

    init {
        instance = this
    }

    fun edit(): SharedPreferences.Editor {
        return preferences.edit()
    }

    val node: Node?
        get() {
            val usesProxy = ProxyService.instance?.usingProxy == true
            var defaultNode = DefaultNodes.MONERUJO
            if (usesProxy) {
                val proxyPort = ProxyService.instance?.proxyPort
                if (proxyPort?.isNotEmpty() == true) {
                    val port = proxyPort.toInt()
                    if (port == 9050) {
                        defaultNode = DefaultNodes.MONERUJO_ONION
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
                edit().putString(Constants.PREF_NODE_2, node.toJson().toString())?.apply()
                return node
            }
        }
        return null
    }

    fun getString(key: String?, defaultValue: String): String? {
        val value = preferences.getString(key, "")
        if (value?.isEmpty() == true && defaultValue.isNotEmpty()) {
            edit().putString(key, defaultValue)?.apply()
            return defaultValue
        }
        return value
    }

    fun getBoolean(key: String?, defaultValue: Boolean): Boolean {
        val containsKey = preferences.contains(key)
        val value = preferences.getBoolean(key, false)
        if (!value && defaultValue && !containsKey) {
            edit().putBoolean(key, true)?.apply()
            return true
        }
        return value
    }

    fun saveProxy(address: String, port: String): String? {
        if (address.isEmpty() || port.isEmpty()) {
            deleteProxy()
            return null
        }
        val proxyAddress = "$address:$port"
        if (proxyAddress == ":") return null
        Log.d("PrefService", "Setting proxy. proxyAddress=$proxyAddress")
        edit().putString(Constants.PREF_PROXY, proxyAddress)?.apply()
        return proxyAddress
    }

    fun deleteProxy() {
        Log.d("PrefService", "Deleting proxy...")
        edit().putString(Constants.PREF_PROXY, "")?.apply()
    }

    companion object {
        lateinit var instance: PrefService
            private set
    }
}