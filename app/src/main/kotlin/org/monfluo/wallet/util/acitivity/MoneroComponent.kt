package org.monfluo.wallet.util.acitivity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.service.wallet.WalletServiceObserver
import timber.log.Timber

interface MoneroComponent : WalletServiceObserver {

    val componentName: String
    var walletService: WalletService?

    private fun getMoneroServiceConnection(): ServiceConnection {
        return object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                Timber.d("Wallet Service connected for $componentName ${this@MoneroComponent.javaClass.simpleName}")
                val walletService = (service as WalletService.WalletServiceBinder).service
                this@MoneroComponent.walletService = walletService
                walletService.addObserver(this@MoneroComponent)
                onWalletServiceConnected(walletService)
            }

            override fun onServiceDisconnected(className: ComponentName) {
                Timber.d("Wallet Service disconnected for $componentName ${this@MoneroComponent.javaClass.simpleName}")
                onWalletServiceDisconnected()
                this@MoneroComponent.walletService = null
            }
        }
    }

    fun onMoneroComponentCreate(context: Context) {
        if (!WalletService.RUNNING.get()) {
            Timber.d("Wallet Service not found for $componentName ${this@MoneroComponent.javaClass.simpleName}")
            onWalletServiceNotFound()
        } else {
            context.bindService(Intent(context, WalletService::class.java), getMoneroServiceConnection(), 0)
        }
    }

    fun onWalletServiceConnected(walletService: WalletService) {}

    fun onWalletServiceNotFound()

    fun onWalletServiceDisconnected()
}