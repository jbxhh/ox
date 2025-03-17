package org.monfluo.wallet.util.acitivity

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import org.monfluo.wallet.HomeActivity
import org.monfluo.wallet.SendActivity
import org.monfluo.wallet.data.DefaultNode
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.UriData
import timber.log.Timber

open class WalletOpeningActivity : LoggingActivity() {

    fun openWallet(walletPath: String, walletPassword: String) {
        val uriData = intent.data?.let { UriData.parse(it.toString()) }
        val walletServiceIntent = Intent(applicationContext, WalletService::class.java)
        startForegroundService(walletServiceIntent)
        bindService(walletServiceIntent, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Timber.d("Wallet Service connected")
                val walletService = (service as WalletService.WalletServiceBinder).service
                val node = PreferenceUtils.getOrSetDefaultNode(this@WalletOpeningActivity, DefaultNode.defaultNode())
                walletService.openWallet(walletPath, walletPassword, node.address, node.username ?: "", node.password ?: "", node.trusted, PreferenceUtils.getProxyIfEnabled(applicationContext) ?: "")

                val homeActivityIntent = Intent(this@WalletOpeningActivity, HomeActivity::class.java)

                if (uriData == null) {
                    // the app was NOT started with a monero uri payment data, proceed to the home activity
                    Timber.d("Uri payment data not present, launching home activity")
                    startActivity(homeActivityIntent)
                    finish()
                } else {
                    // the app was started with a monero uri payment data, we proceed to the send activity but launch the home activity as well
                    // so that when users press back button they go to home activity instead of closing the app
                    Timber.d("Uri payment data present, launching home and send activities")
                    homeActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    startActivity(homeActivityIntent)

                    val sendIntent = Intent(this@WalletOpeningActivity, SendActivity::class.java)
                    sendIntent.putExtra(Constants.EXTRA_SEND_ADDRESS, uriData.address)
                    sendIntent.putExtra(Constants.EXTRA_SEND_AMOUNT, uriData.amount)
                    startActivity(sendIntent)

                    finish()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Timber.i("Wallet Service disconnected")
            }
        }, 0)
    }
}