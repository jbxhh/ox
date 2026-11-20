package org.monfluo.wallet.android.data

import org.monfluo.wallet.android.R
import org.monfluo.wallet.lib.data.Network

fun Network.next(): Network =
    when (this) {
        Network.Mainnet -> Network.Stagenet
        Network.Stagenet -> Network.Testnet
        Network.Testnet -> Network.Mainnet
    }

fun Network.resourceId(): Int =
    when (this) {
        Network.Mainnet -> R.string.monero_network_mainnet
        Network.Stagenet -> R.string.monero_network_stagenet
        Network.Testnet -> R.string.monero_network_testnet
    }
