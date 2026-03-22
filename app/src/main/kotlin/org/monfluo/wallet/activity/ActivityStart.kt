package org.monfluo.wallet.activity

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.R
import org.monfluo.wallet.data.DefaultNode
import org.monfluo.wallet.fragment.DialogFragmentPassword
import org.monfluo.wallet.model.MoneroNetworkType
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.component.ActivityLogging
import timber.log.Timber
import kotlin.io.path.Path
import kotlin.io.path.name

class ActivityStart : ActivityLogging() {
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
        val walletPaths = Helper.findWallets(Helper.getWalletRoot(this))
        if (walletPaths.isEmpty()) {
            Timber.d("No wallets found, launching onboarding activity")
            startActivityToOpenWallet.launch(Intent(this, ActivityWalletCreate::class.java))
        } else if (isMultiWalletMode) {
            Timber.d("${walletPaths.size} wallets found, launching wallet activity")
            startActivityToOpenWallet.launch(Intent(this, ActivityWalletList::class.java))
        } else {
            Timber.d("${walletPaths.size} wallets found but multi-wallet mode is not enabled, asking for password to open a wallet")
            val walletPath = walletPaths.find { it.endsWith(Constants.DEFAULT_WALLET_NAME) } ?: walletPaths.first()
            val passwordDialog =
                org.monfluo.wallet.fragment.DialogFragmentPassword(
                    walletPath,
                    true,
                    object : DialogFragmentPassword.Listener {
                        override fun onCorrectPasswordSubmitted(
                            self: DialogFragmentPassword,
                            password: String,
                        ) {
                            self.dismiss()
                            openWallet(walletPath, password, viewModel.takeUriPayment())
                        }
                    },
                )
            passwordDialog.show(supportFragmentManager, "password_dialog")
        }
    }

    private fun openWalletFromActivityResult(result: ActivityResult) {
        val data = result.data
        val walletPath = data?.getStringExtra(Constants.EXTRA_WALLET_PATH)
        val walletPassword = data?.getStringExtra(Constants.EXTRA_WALLET_PASSWORD)
        Timber.i("Opening wallet at path $walletPath")
        openWallet(walletPath, walletPassword, viewModel.takeUriPayment())
    }

    private fun openWallet(
        walletPath: String?,
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
                    if (walletPath != null && walletPassword != null) {
                        val networkType = MoneroNetworkType.fromInteger(PreferenceUtils.getWalletNetworkType(this@ActivityStart, Path(walletPath!!).name)) ?: MoneroNetworkType.NetworkType_Mainnet
                        val node = PreferenceUtils.getOrSetDefaultOrAnyNode(this@ActivityStart, networkType, DefaultNode.defaultNodes())
                        if (node != null) {
                            walletService.openWallet(walletPath, walletPassword, node.address, node.username ?: "", node.password ?: "", node.trusted, PreferenceUtils.getProxyIfEnabled(applicationContext) ?: "", networkType)
                        } else {
                            Timber.e("No node found for network $networkType, not starting wallet")
                            Toast.makeText(this@ActivityStart, this@ActivityStart.getString(R.string.toast_no_node_for_network, this@ActivityStart.getString(networkType.resourceId)), Toast.LENGTH_SHORT).show()
                            walletService.closeWallet()
                            this@ActivityStart.recreate()
                            return
                        }
                    } else {
                        Timber.i("Wallet path or password are null, initializing wallet instead of opening it")
                        val uninitWallet = walletService.getUninitWallet()
                        if (uninitWallet == null) {
                            Timber.e("No uninit wallet found!")
                            walletService.closeWallet()
                            this@ActivityStart.recreate()
                            return
                        }
                        val networkType = uninitWallet.networkType()
                        val node = PreferenceUtils.getOrSetDefaultOrAnyNode(this@ActivityStart, networkType, DefaultNode.defaultNodes())
                        if (node != null) {
                            walletService.initWallet(node.address, node.username ?: "", node.password ?: "", node.trusted, PreferenceUtils.getProxyIfEnabled(applicationContext) ?: "")
                        } else {
                            Timber.e("No node found for network $networkType, not starting wallet")
                            Toast.makeText(this@ActivityStart, this@ActivityStart.getString(R.string.toast_no_node_for_network, this@ActivityStart.getString(networkType.resourceId)), Toast.LENGTH_SHORT).show()
                            walletService.closeWallet()
                            this@ActivityStart.recreate()
                            return
                        }
                    }

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
