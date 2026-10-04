package org.monfluo.wallet.android.activity

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.android.fragment.DialogFragmentPassword
import org.monfluo.wallet.android.service.wallet.WalletService
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.Helper
import org.monfluo.wallet.android.util.PreferenceUtils
import org.monfluo.wallet.android.util.component.ActivityBase
import org.monfluo.wallet.lib.Monfluo
import timber.log.Timber

class ActivityStart : ActivityBase() {
    private val startActivityToOpenWallet =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == 0) {
                Timber.i("Activity started to open wallet returned result code 0, finishing current activity")
                finish()
            } else {
                openWalletFromActivityResult(result)
            }
        }

    private val startActivityHome =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Timber.i("Home activity is finished, launching again")
            launch()
        }

    private val viewModel: StartActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setVisible(false)

        viewModel.uriPaymentRaw = intent?.getDataString()

        launch()
    }

    private fun launch() {
        val isMultiWalletMode = PreferenceUtils.isMultiWalletMode(this)
        val wallets = Monfluo.listWallets(Helper.getWalletRoot(this).absolutePath)
        if (wallets.isEmpty()) {
            Timber.d("No wallets found, launching onboarding activity")
            startActivityToOpenWallet.launch(Intent(this, ActivityWalletCreate::class.java))
        } else if (isMultiWalletMode) {
            Timber.d("${wallets.size} wallets found, launching wallet activity")
            startActivityToOpenWallet.launch(Intent(this, ActivityWalletList::class.java))
        } else {
            Timber.d("${wallets.size} wallets found but multi-wallet mode is not enabled, asking for password to open a wallet")
            val wallet = wallets.find { it == Constants.DEFAULT_WALLET_NAME } ?: wallets.first()
            val passwordDialog =
                DialogFragmentPassword(
                    wallet,
                    true,
                    object : DialogFragmentPassword.Listener {
                        override fun onCorrectPasswordSubmitted(
                            self: DialogFragmentPassword,
                            password: String,
                        ) {
                            self.dismiss()
                            openWallet(wallet, password, viewModel.takeUriPayment())
                        }
                    },
                )
            passwordDialog.show(supportFragmentManager, "password_dialog")
        }
    }

    private fun openWalletFromActivityResult(result: ActivityResult) {
        val data = result.data
        val wallet = data?.getStringExtra(Constants.EXTRA_WALLET_NAME)
        val walletPassword = data?.getStringExtra(Constants.EXTRA_WALLET_PASSWORD)
        Timber.i("Opening wallet \"$wallet\"")
        openWallet(wallet, walletPassword, viewModel.takeUriPayment())
    }

    private fun openWallet(
        wallet: String?,
        walletPassword: String?,
        uriPaymentRaw: String?,
    ) {
        val walletServiceIntent = Intent(applicationContext, WalletService::class.java)
        startForegroundService(walletServiceIntent)
        bindService(
            walletServiceIntent,
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    service: IBinder?,
                ) {
                    Timber.d("Wallet Service connected")
                    val walletService = (service as WalletService.WalletServiceBinder).service
                    if (wallet == null || walletPassword == null) {
                        Timber.e("Wallet path or password are null, not starting wallet")
                        walletService.closeWallet()
                        this@ActivityStart.recreate()
                        return
                    }
                    walletService.openWallet(wallet, walletPassword)

                    val activityHomeIntent = Intent(this@ActivityStart, ActivityHome::class.java)

                    if (uriPaymentRaw == null) {
                        // the app was NOT started with a monero uri payment data, proceed to the home activity
                        Timber.d("Uri payment data not present, launching home activity")
                        startActivityHome.launch(activityHomeIntent)
                    } else {
                        // the app was started with a monero uri payment data, we proceed to the send activity but launch the home activity as well
                        // so that when users press back button they go to home activity instead of closing the app
                        Timber.d("Uri payment data present, launching home and send activities")
                        activityHomeIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        startActivityHome.launch(activityHomeIntent)

                        val sendIntent = Intent(this@ActivityStart, ActivitySend::class.java)
                        sendIntent.putExtra(Constants.EXTRA_PAYMENT_URI, uriPaymentRaw)
                        startActivity(sendIntent)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    Timber.i("Wallet Service disconnected")
                }
            },
            0,
        )
    }
}

internal class StartActivityViewModel : ViewModel() {
    var uriPaymentRaw: String? = null

    fun takeUriPayment(): String? {
        val uriPaymentRaw = this.uriPaymentRaw
        this.uriPaymentRaw = null
        return uriPaymentRaw
    }
}
