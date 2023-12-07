package net.mynero.wallet.util

import net.mynero.wallet.model.Wallet.Companion.getPaymentIdFromAddress
import net.mynero.wallet.model.Wallet.Companion.isAddressValid
import net.mynero.wallet.model.WalletManager.Companion.instance

class UriData(val address: String, val params: HashMap<String, String>) {

    fun hasPaymentId(): Boolean {
        return instance?.wallet?.nettype()?.let { getPaymentIdFromAddress(address, it) }
            ?.isNotEmpty() == true
    }

    val amount: String?
        get() = params[Constants.URI_ARG_AMOUNT]
            ?: params[Constants.URI_ARG_AMOUNT2]

    fun hasAmount(): Boolean {
        return params.containsKey(Constants.URI_ARG_AMOUNT) || params.containsKey(Constants.URI_ARG_AMOUNT2)
    }

    companion object {
        fun parse(uri: String): UriData? {
            if (uri.isEmpty()) return null
            val params = HashMap<String, String>()
            val uriParts = uri.replace(Constants.URI_PREFIX, "").split("\\?".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()

            if (uriParts.isEmpty()) return null
            val finalAddress = uriParts[0]
            var queryParams = ""
            if (uriParts.size > 1) {
                queryParams = uriParts[1]
                val queryParts =
                    queryParams.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (param in queryParts) {
                    val paramParts =
                        param.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (paramParts.size == 2) {
                        val variable = paramParts[0]
                        val value = paramParts[1]
                        params[variable] = value
                    }
                }
            }
            val valid = isAddressValid(finalAddress)
            return if (valid) {
                UriData(finalAddress, params)
            } else {
                null
            }
        }
    }
}