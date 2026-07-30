package org.monfluo.wallet.lib.wallet

interface WalletSyncObserver {
    fun observe(state: WalletSyncState)
}

sealed interface WalletSyncState

class PreparingForSync : WalletSyncState

class StartingSync(
    val targetHeight: Int,
) : WalletSyncState

class Syncing(
    val targetHeight: Int,
) : WalletSyncState

class FinishedSyncing : WalletSyncState
