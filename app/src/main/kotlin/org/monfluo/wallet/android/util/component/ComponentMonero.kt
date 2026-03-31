package org.monfluo.wallet.android.util.component

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import org.monfluo.wallet.android.service.wallet.WalletService
import org.monfluo.wallet.android.service.wallet.WalletServiceObserver
import timber.log.Timber

interface ComponentMonero : WalletServiceObserver {
    val componentName: String
    var walletService: WalletService?

    private fun getMoneroServiceConnection(): ServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                className: ComponentName,
                service: IBinder,
            ) {
                Timber.d("Wallet Service connected for $componentName ${this@ComponentMonero.javaClass.simpleName}")
                val walletService = (service as WalletService.WalletServiceBinder).service
                this@ComponentMonero.walletService = walletService
                walletService.addObserver(this@ComponentMonero)
                onWalletServiceConnected(walletService)
            }

            override fun onServiceDisconnected(className: ComponentName) {
                Timber.d("Wallet Service disconnected for $componentName ${this@ComponentMonero.javaClass.simpleName}")
                onWalletServiceDisconnected()
                this@ComponentMonero.walletService = null
            }
        }

    fun onComponentMoneroCreate(context: Context) {
        if (!WalletService.RUNNING.get()) {
            Timber.d("Wallet Service not found for $componentName ${this@ComponentMonero.javaClass.simpleName}")
            onWalletServiceNotFound()
        } else {
            context.bindService(Intent(context, WalletService::class.java), getMoneroServiceConnection(), 0)
        }
    }

    fun onWalletServiceConnected(walletService: WalletService) {}

    fun onWalletServiceNotFound()

    fun onWalletServiceDisconnected()
}
