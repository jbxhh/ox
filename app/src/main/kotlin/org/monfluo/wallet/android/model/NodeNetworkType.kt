package org.monfluo.wallet.android.model

enum class NodeNetworkType {
    CLEARNET,
    TOR,
    I2P,
    ;

    companion object {
        private val ADDRESS_REGEX = Regex("^([a-zA-Z0-9-_+]+://)?((\\[.*])|([^:]+))(:(\\d+))?$")

        fun fromAddress(address: String): NodeNetworkType? =
            ADDRESS_REGEX.matchEntire(address)?.let { matchResult ->
                val destination = matchResult.groupValues[2]
                if (destination.endsWith(".onion")) {
                    TOR
                } else if (destination.endsWith(".i2p") || destination.endsWith(".i2p.alt")) {
                    I2P
                } else {
                    CLEARNET
                }
            }
    }
}
