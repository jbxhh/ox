package net.mynero.wallet.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import net.mynero.wallet.MoneroApplication
import net.mynero.wallet.data.DefaultNode
import net.mynero.wallet.data.Node
import net.mynero.wallet.data.Node.Companion.fromJson
import net.mynero.wallet.util.Constants
import org.json.JSONException
import org.json.JSONObject

class PrefService(application: MoneroApplication) {

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

    fun deleteProxy() {
        Log.d("PrefService", "Deleting proxy...")
        edit().putString(Constants.PREF_PROXY, "")?.apply()
    }

    companion object {
        lateinit var instance: PrefService
            private set
    }
}