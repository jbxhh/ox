package org.monfluo.wallet.android.util.component

import android.os.Bundle
import org.monfluo.wallet.android.service.wallet.WalletService
import timber.log.Timber

abstract class ActivityMonero :
    ActivityBase(),
    ComponentMonero {
    override val componentName: String = "activity"
    override var walletService: WalletService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.onComponentMoneroCreate(applicationContext)
    }

    override fun onWalletServiceNotFound() {
        Timber.d("Finishing activity ${this@ActivityMonero.javaClass.simpleName} because wallet service is not found")
        finish()
    }

    override fun onWalletServiceDisconnected() {
        Timber.d("Finishing activity ${this@ActivityMonero.javaClass.simpleName} because wallet service disconnected")
        finish()
    }
}
