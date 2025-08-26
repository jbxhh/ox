package org.monfluo.wallet.data

import android.content.Context
import org.monfluo.wallet.R
import org.monfluo.wallet.model.WalletStatus
import org.monfluo.wallet.service.wallet.State
import java.text.NumberFormat

sealed interface SynchronizationStatus {

    fun getText(context: Context): String

    companion object {
        fun from(state: State): SynchronizationStatus {
            val initialWalletStatus = state.initialWalletStatus
            if (initialWalletStatus != null && initialWalletStatus.status == WalletStatus.Critical) {
                return WalletError(initialWalletStatus.error)
            }
            val (walletBeginSyncHeight, walletHeight, blockchainHeight, targetHeight) = state.heights
            if (walletHeight == 0L) {
                return OpeningWallet
            }
            val walletDiff = blockchainHeight - walletHeight
            val nodeDiff = targetHeight - blockchainHeight
            val synchronized = blockchainHeight > 0 && walletDiff == 0L

            val walletDisplayHeight = NumberFormat.getInstance().format(walletHeight - 1)
            val blockchainDisplayHeight = if (blockchainHeight > 0) NumberFormat.getInstance().format(blockchainHeight - 1) else "???"
            val targetDisplayHeight = if (targetHeight > 0) NumberFormat.getInstance().format(targetHeight - 1) else "???"

            val walletDiffDisplay = NumberFormat.getInstance().format(walletDiff)
            val nodeDiffDisplay = NumberFormat.getInstance().format(nodeDiff)

            return if (synchronized) {
                Synchronized(walletDisplayHeight)
            } else if (walletHeight > walletBeginSyncHeight) {
                WalletSynchronization(walletDisplayHeight, blockchainDisplayHeight, walletDiffDisplay)
            } else if (blockchainHeight < targetHeight) {
                NodeSynchronization(blockchainDisplayHeight, targetDisplayHeight, nodeDiffDisplay)
            } else {
                StartingSynchronization
            }
        }
    }

    data object OpeningWallet : SynchronizationStatus {
        override fun getText(context: Context): String  {
            return context.getString(R.string.starting_wallet_synchronization)
        }
    }

    data object StartingSynchronization : SynchronizationStatus {
        override fun getText(context: Context): String  {
            return context.getString(R.string.starting_wallet_synchronization)
        }
    }

    data class NodeSynchronization(val blockchainDisplayHeight: String, val targetDisplayHeight: String, val diffDisplay: String) : SynchronizationStatus {
        override fun getText(context: Context): String  {
            return context.getString(R.string.node_synchronization_blocks_remaining, blockchainDisplayHeight, targetDisplayHeight, diffDisplay)
        }
    }

    data class WalletSynchronization(val walletDisplayHeight: String, val blockchainDisplayHeight: String, val diffDisplay: String) : SynchronizationStatus {
        override fun getText(context: Context): String  {
            return context.getString(R.string.wallet_synchronization_blocks_remaining, walletDisplayHeight, blockchainDisplayHeight, diffDisplay)
        }
    }

    data class Synchronized(val walletDisplayHeight: String) : SynchronizationStatus {
        override fun getText(context: Context): String {
            return context.getString(R.string.synchronized_at_label, walletDisplayHeight)
        }
    }

    data class WalletError(val error: String) : SynchronizationStatus {
        override fun getText(context: Context): String {
            return context.getString(R.string.wallet_error, error)
        }
    }
}