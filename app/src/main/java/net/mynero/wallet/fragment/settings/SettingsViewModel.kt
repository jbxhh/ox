package net.mynero.wallet.fragment.settings

import android.util.Patterns
import androidx.lifecycle.ViewModel
import net.mynero.wallet.MoneroApplication
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.util.Constants

class SettingsViewModel : ViewModel() {
    private var proxyAddress = ""
    private var proxyPort = ""
    fun updateProxy(application: MoneroApplication) {
        application.executor?.execute {
            val usesProxy = PrefService.instance?.getBoolean(Constants.PREF_USES_TOR, false) == true
            val curretNode = PrefService.instance?.node
            val isNodeLocalIp =
                curretNode?.address?.startsWith("10.") == true || curretNode?.address?.startsWith("192.168.") == true || curretNode?.address == "localhost" || curretNode?.address == "127.0.0.1"
            if (!usesProxy || isNodeLocalIp) {
                WalletManager.instance?.setProxy("")
                WalletManager.instance?.wallet?.setProxy("")
                return@execute
            }
            if (proxyAddress.isEmpty()) proxyAddress = "127.0.0.1"
            if (proxyPort.isEmpty()) proxyPort = "9050"
            val validIpAddress = Patterns.IP_ADDRESS.matcher(proxyAddress).matches()
            if (validIpAddress) {
                val proxy = "$proxyAddress:$proxyPort"
                PrefService.instance?.edit()?.putString(Constants.PREF_PROXY, proxy)?.apply()
                WalletManager.instance?.setProxy(proxy)
                WalletManager.instance?.wallet?.setProxy(proxy)
            }
        }
    }

    fun setProxyAddress(address: String) {
        proxyAddress = address
    }

    fun setProxyPort(port: String) {
        proxyPort = port
    }
}