package org.monfluo.wallet.lib.wallet

import org.monfluo.wallet.lib.data.NodeInfo

class Daemon(
    @Suppress("unused") private val _handle: Long,
) {
    external fun getStatus(): DaemonStatus

    external fun fetchNodeInfo(): NodeInfo
}

enum class DaemonStatus
