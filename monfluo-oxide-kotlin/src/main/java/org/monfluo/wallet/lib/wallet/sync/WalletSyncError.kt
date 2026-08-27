package org.monfluo.wallet.lib.wallet.sync

import org.monfluo.wallet.lib.data.NodeInfo

sealed interface WalletSyncError {
    class CouldNotFetchNodeInfo

    data class NodeNotSynced(
        val nodeInfo: NodeInfo,
    )

    class CouldNotFetchBlocks
}
