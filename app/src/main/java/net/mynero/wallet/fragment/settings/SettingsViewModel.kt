package net.mynero.wallet.fragment.settings

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import net.mynero.wallet.MoneroApplication
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.ProxyService
import net.mynero.wallet.util.Constants

class SettingsViewModel : ViewModel() {
    private val _useProxy = MutableLiveData(false)
    val useProxy: LiveData<Boolean> = _useProxy
    private val _useBundledTor = MutableLiveData(false)
    val useBundledTor: LiveData<Boolean> = _useBundledTor

    init {
        _useProxy.value = ProxyService.instance?.usingProxy
        _useBundledTor.value = ProxyService.instance?.useBundledTor
    }

    fun setUseProxy(use: Boolean) {
        _useProxy.value = use
        PrefService.instance?.edit()?.putBoolean(Constants.PREF_USES_PROXY, use)?.apply()

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        if(use && useBundledTor.value == true) {
            samouraiTorManager?.start()
        } else {
            samouraiTorManager?.stop()
        }
    }

    fun setUseBundledTor(use: Boolean) {
        PrefService.instance?.edit()?.putBoolean(Constants.PREF_USE_BUNDLED_TOR, use)?.apply()
        _useBundledTor.value = use

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        if(use && useProxy.value == true) {
            samouraiTorManager?.start()
        } else {
            samouraiTorManager?.stop()
        }
    }
}