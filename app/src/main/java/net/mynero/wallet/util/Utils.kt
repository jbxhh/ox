package net.mynero.wallet.util

import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.ProxyService

// Collection of various utilities, probably to be refactored later
object Utils {
    fun refreshProxy(proxyAddress: String, proxyPort: String) {
        val savedProxyAddress = ProxyService.instance?.proxyAddress
        val savedProxyPort = ProxyService.instance?.proxyPort
        val currentWalletProxy = WalletManager.instance?.proxy
        val newProxy = "$proxyAddress:$proxyPort"
        if (proxyAddress != savedProxyAddress || proxyPort != savedProxyPort || (newProxy != currentWalletProxy && newProxy != ":"))
            ProxyService.instance?.updateProxy(proxyAddress, proxyPort)
    }
}