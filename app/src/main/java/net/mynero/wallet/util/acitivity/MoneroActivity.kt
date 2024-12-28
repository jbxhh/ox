package net.mynero.wallet.util.acitivity

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import net.mynero.wallet.service.wallet.WalletService
import net.mynero.wallet.service.wallet.WalletServiceObserver
import timber.log.Timber

abstract class MoneroActivity : LoggingActivity(), WalletServiceObserver {

    protected var walletService: WalletService? = null

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Timber.d("Wallet Service connected for activity ${this@MoneroActivity.javaClass.simpleName}")
            val walletService = (service as WalletService.WalletServiceBinder).service
            this@MoneroActivity.walletService = walletService
            walletService.addObserver(this@MoneroActivity)
            onWalletServiceBound(walletService)
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Timber.i("Wallet Service disconnected for activity ${this@MoneroActivity.javaClass.simpleName}")
            walletService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!WalletService.RUNNING.get()) {
            onWalletServiceNotFound()
        } else {
            bindService(Intent(applicationContext, WalletService::class.java), connection, 0)
        }
    }

    open fun onWalletServiceBound(walletService: WalletService) {}

    open fun onWalletServiceNotFound() {
        Timber.i("Finishing activity because wallet service is not running yet")
        finish()
    }
}