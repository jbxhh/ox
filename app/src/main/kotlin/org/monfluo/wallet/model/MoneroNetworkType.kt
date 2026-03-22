/*
 * Copyright (c) 2018 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.monfluo.wallet.model

import org.monfluo.wallet.R

@Suppress("ktlint:standard:enum-entry-name-case")
enum class MoneroNetworkType(
    val value: Int,
    val string: String,
    val resourceId: Int,
) {
    NetworkType_Mainnet(0, "mainnet", R.string.monero_network_mainnet),
    NetworkType_Testnet(1, "testnet", R.string.monero_network_testnet),
    NetworkType_Stagenet(2, "stagenet", R.string.monero_network_stagenet),
    ;

    companion object {
        fun fromInteger(n: Int): MoneroNetworkType? = entries.find { it.value == n }

        fun fromString(s: String): MoneroNetworkType? = entries.find { it.string == s }
    }

    fun next(): MoneroNetworkType =
        when (this) {
            MoneroNetworkType.NetworkType_Mainnet -> MoneroNetworkType.NetworkType_Stagenet
            MoneroNetworkType.NetworkType_Stagenet -> MoneroNetworkType.NetworkType_Testnet
            MoneroNetworkType.NetworkType_Testnet -> MoneroNetworkType.NetworkType_Mainnet
        }
}
