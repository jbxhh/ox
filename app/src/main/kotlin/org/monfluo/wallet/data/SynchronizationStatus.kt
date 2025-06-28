package org.monfluo.wallet.data

import android.content.Context
import org.monfluo.wallet.R
import java.text.NumberFormat

sealed interface SynchronizationStatus {

    fun getText(context: Context): String

    companion object {
        fun fromHeights(heights: Heights): SynchronizationStatus {
            val (walletBeginSyncHeight, walletHeight, daemonHeight) = heights
            val diff = daemonHeight - walletHeight
            val synchronized = daemonHeight > 0 && diff == 0L
            val walletDisplayHeight = NumberFormat.getInstance().format(walletHeight - 1)
            val daemonDisplayHeight = if (daemonHeight > 0) NumberFormat.getInstance().format(daemonHeight - 1) else "???"
            val diffDisplay = NumberFormat.getInstance().format(diff)
            return if (synchronized) {
                Synchronized(walletDisplayHeight)
            } else if (walletHeight > walletBeginSyncHeight) {
                Synchronizing(walletDisplayHeight, daemonDisplayHeight, diffDisplay)
            } else {
                StartingSynchronization
            }
        }
    }

    data class Synchronized(val walletDisplayHeight: String) : SynchronizationStatus {
        override fun getText(context: Context): String {
            return context.getString(R.string.synchronized_at_label, walletDisplayHeight)
        }
    }

    data class Synchronizing(val walletDisplayHeight: String, val daemonDisplayHeight: String, val diffDisplay: String) : SynchronizationStatus {
        override fun getText(context: Context): String  {
            return context.getString(R.string.synchronizing_blocks_remaining, walletDisplayHeight, daemonDisplayHeight, diffDisplay)
        }
    }

    data object StartingSynchronization : SynchronizationStatus {
        override fun getText(context: Context): String  {
            return context.getString(R.string.starting_wallet_synchronization)
        }
    }
}