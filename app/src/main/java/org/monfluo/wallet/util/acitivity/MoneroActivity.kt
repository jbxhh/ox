package org.monfluo.wallet.util.acitivity

import android.os.Bundle
import org.monfluo.wallet.service.wallet.WalletService
import timber.log.Timber

abstract class MoneroActivity : LoggingActivity(), MoneroComponent {

    override val componentName: String = "activity"
    override var walletService: WalletService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.onMoneroComponentCreate(applicationContext)
    }

    override fun onWalletServiceNotFound() {
        Timber.d("Finishing activity ${this@MoneroActivity.javaClass.simpleName} because wallet service is not found")
        finish()
    }

    override fun onWalletServiceDisconnected() {
        Timber.d("Finishing activity ${this@MoneroActivity.javaClass.simpleName} because wallet service disconnected")
        finish()
    }
}