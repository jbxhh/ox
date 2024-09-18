package net.mynero.wallet.service

import net.mynero.wallet.data.Subaddress
import net.mynero.wallet.model.WalletManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddressService(thread: MoneroHandlerThread) : ServiceBase(thread) {
    var numAddresses = 1
        private set

    init {
        instance = this
    }

    fun refreshAddresses() {
        WalletManager.instance?.wallet?.numSubaddresses?.let { numAddresses = it }
    }

    fun freshSubaddress(): Subaddress? {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.US).format(Date())
        val wallet = WalletManager.instance?.wallet
        wallet?.addSubaddress(wallet.getAccountIndex(), timeStamp)
        refreshAddresses()
        wallet?.store()
        return wallet?.getSubaddressObject(numAddresses - 1)
    }

    fun currentSubaddress(): Subaddress? {
        val wallet = WalletManager.instance?.wallet
        return wallet?.getSubaddressObject(numAddresses - 1)
    }

    companion object {
        var instance: AddressService? = null
    }
}