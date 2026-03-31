package org.monfluo.wallet.android.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class NodeNetworkTypeTest {
    @ParameterizedTest
    @MethodSource("nodeAddresses")
    fun `should guess correct node network type by node address`(
        nodeAddress: String,
        expectedNodeType: NodeNetworkType?,
    ) {
        assertEquals(expectedNodeType, NodeNetworkType.fromAddress(nodeAddress))
    }

    companion object {
        @JvmStatic
        fun nodeAddresses(): List<Arguments> =
            listOf(
                Arguments.of("my.node.local", NodeNetworkType.CLEARNET),
                Arguments.of("my.node.local:18081", NodeNetworkType.CLEARNET),
                Arguments.of("http://my.node.local", NodeNetworkType.CLEARNET),
                Arguments.of("http://my.node.local:18081", NodeNetworkType.CLEARNET),
                Arguments.of("127.0.0.1", NodeNetworkType.CLEARNET),
                Arguments.of("127.0.0.1:18081", NodeNetworkType.CLEARNET),
                Arguments.of("http://127.0.0.1", NodeNetworkType.CLEARNET),
                Arguments.of("http://127.0.0.1:18081", NodeNetworkType.CLEARNET),
                Arguments.of("[fe80:cafe:babe::1]", NodeNetworkType.CLEARNET),
                Arguments.of("[fe80:cafe:babe::1]:18081", NodeNetworkType.CLEARNET),
                Arguments.of("http://[fe80:cafe:babe::1]", NodeNetworkType.CLEARNET),
                Arguments.of("http://[fe80:cafe:babe::1]:18081", NodeNetworkType.CLEARNET),
                Arguments.of("my.node.onion", NodeNetworkType.TOR),
                Arguments.of("my.node.onion:18081", NodeNetworkType.TOR),
                Arguments.of("http://my.node.onion", NodeNetworkType.TOR),
                Arguments.of("http://my.node.onion:18081", NodeNetworkType.TOR),
                Arguments.of("my.node.i2p", NodeNetworkType.I2P),
                Arguments.of("my.node.i2p:18081", NodeNetworkType.I2P),
                Arguments.of("http://my.node.i2p", NodeNetworkType.I2P),
                Arguments.of("http://my.node.i2p:18081", NodeNetworkType.I2P),
                Arguments.of("my.node.i2p.alt", NodeNetworkType.I2P),
                Arguments.of("my.node.i2p.alt:18081", NodeNetworkType.I2P),
                Arguments.of("http://my.node.i2p.alt", NodeNetworkType.I2P),
                Arguments.of("http://my.node.i2p.alt:18081", NodeNetworkType.I2P),
            )
    }
}
