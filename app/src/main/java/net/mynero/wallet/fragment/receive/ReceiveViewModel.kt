package net.mynero.wallet.fragment.receive

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import net.mynero.wallet.data.Subaddress
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.AddressService
import java.util.Collections

class ReceiveViewModel : ViewModel() {
    private val _address = MutableLiveData<Subaddress?>()
    private val _addresses = MutableLiveData<List<Subaddress>>()
    val address: LiveData<Subaddress?> = _address
    val addresses: LiveData<List<Subaddress>> = _addresses

    fun init() {
        _addresses.value = subaddresses
        _address.value = addresses.value?.lastOrNull()
    }

    private val subaddresses: List<Subaddress>
        get() {
            val wallet = WalletManager.instance?.wallet
            val subaddresses = ArrayList<Subaddress>()
            val numAddresses = AddressService.instance?.numAddresses ?: 1
            for (i in 0 until numAddresses) {
                wallet?.getSubaddressObject(i)?.let { subaddresses.add(it) }
            }
            return Collections.unmodifiableList(subaddresses)
        }

    val freshSubaddress: Unit
        get() {
            _address.value = AddressService.instance?.freshSubaddress()
            _addresses.value = subaddresses
        }

    fun selectAddress(subaddress: Subaddress?) {
        _address.value = subaddress
    }
}