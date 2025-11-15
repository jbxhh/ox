package org.monfluo.wallet.util

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.monfluo.wallet.model.MoneroNetworkType
import org.monfluo.wallet.model.Wallet.Companion.getPaymentIdFromAddress
import org.monfluo.wallet.model.Wallet.Companion.isAddressValid
import org.monfluo.wallet.model.TransactionDestinationRaw
import timber.log.Timber

private const val URI_SCHEMA = "monero"
private const val SCHEMA_SEPARATOR = ':'
private const val VALUE_SEPARATOR = ';'
private const val QUERY_SEPARATOR = '?'
private const val PARAMETERS_SEPARATOR = '&'
private const val KEY_VALUE_SEPARATOR = '='
private val URI_AMOUNT_KEYS = arrayOf("tx_amount", "amount")
private const val URI_RECIPIENT_NAME_KEY = "recipient_name"
private val URI_DESCRIPTION_KEYS = arrayOf("tx_description", "description", "message")

data class UriPayment(
    val addresses: List<String>,
    val amounts: List<String>,
    val recipientNames: List<String>,
    val description: String?,
) {

    companion object {
        fun parse(string: String): UriPayment? {
            val schemaSplit = string.split(SCHEMA_SEPARATOR, limit = 2)
            if (schemaSplit.size == 2 && schemaSplit.get(0) != URI_SCHEMA) {
                Timber.w("Unexpected URI schema: ${schemaSplit.get(0)}")
                return null
            }
            val withoutSchema = schemaSplit.last()
            val addressesAndParams = withoutSchema.split(QUERY_SEPARATOR, limit = 2);
            if (addressesAndParams.isEmpty()) {
                Timber.w("Addresses and params are empty")
                return null
            }
            val addressesRaw = addressesAndParams.get(0)
            val paramsRaw = addressesAndParams.getOrNull(1)
            val addresses = addressesRaw.split(VALUE_SEPARATOR)
            var amounts: List<String> = emptyList()
            var recipientNames: List<String> = emptyList()
            var description: String? = null
            if (paramsRaw != null) {
                val paramsRawSplit = paramsRaw.split(PARAMETERS_SEPARATOR)
                for (paramRaw in paramsRawSplit) {
                    val keyAndValue = paramRaw.split(KEY_VALUE_SEPARATOR, limit = 2)
                    if (keyAndValue.size != 2) {
                        Timber.w("Bad parameter: ${paramRaw}")
                        continue;
                    }
                    val key = keyAndValue.get(0)
                    val value = keyAndValue.get(1)
                    if (URI_AMOUNT_KEYS.contains(key)) {
                        amounts = value.split(VALUE_SEPARATOR)
                    } else if (URI_RECIPIENT_NAME_KEY == key) {
                        recipientNames = value.split(VALUE_SEPARATOR).map {
                            URLDecoder.decode(it, StandardCharsets.UTF_8)
                        }
                    } else if (URI_DESCRIPTION_KEYS.contains(key)) {
                        description = URLDecoder.decode(value, StandardCharsets.UTF_8)
                    } else {
                        Timber.w("Unknown payment uri key: ${key}")
                    }
                }
            }
            return UriPayment(addresses, amounts, recipientNames, description)
        }
    }

    fun toUriString(): String {
        val builder = StringBuilder()

        builder.append(URI_SCHEMA)
        builder.append(SCHEMA_SEPARATOR)
        builder.append(addresses.joinToString(separator = VALUE_SEPARATOR.toString()))

        val parameters = mutableListOf<String>()

        if (amounts.isNotEmpty()) {
            val key = URI_AMOUNT_KEYS[0]
            val amountsEncoded = amounts.joinToString(separator = VALUE_SEPARATOR.toString())
            parameters.add("$key$KEY_VALUE_SEPARATOR$amountsEncoded")
        }

        if (recipientNames.isNotEmpty()) {
            val key = URI_RECIPIENT_NAME_KEY
            val recipientNamesEncoded = recipientNames.joinToString(separator = VALUE_SEPARATOR.toString()) {
                URLEncoder.encode(it, StandardCharsets.UTF_8)
            }
            parameters.add("$key$KEY_VALUE_SEPARATOR$recipientNamesEncoded")
        }

        if (!description.isNullOrBlank()) {
            val key = URI_DESCRIPTION_KEYS[0]
            val descriptionEncoded = URLEncoder.encode(description, StandardCharsets.UTF_8)
            parameters.add("$key$KEY_VALUE_SEPARATOR$descriptionEncoded")
        }

        if (parameters.isNotEmpty()) {
            builder.append(QUERY_SEPARATOR)
            builder.append(parameters.joinToString(separator = PARAMETERS_SEPARATOR.toString()))
        }

        return builder.toString()
    }

    fun toTransactionDestinationRawList(): List<TransactionDestinationRaw> {
        return (0 until maxOf(addresses.size, amounts.size, recipientNames.size)).map { i ->
            TransactionDestinationRaw(
                addresses.getOrNull(i) ?: "",
                amounts.getOrNull(i) ?: "",
                recipientNames.getOrNull(i) ?: "",
            )
        }
    }
}
