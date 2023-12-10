package net.mynero.wallet

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.NavHostFragment
import net.mynero.wallet.fragment.dialog.PasswordBottomSheetDialog
import net.mynero.wallet.fragment.dialog.PasswordBottomSheetDialog.PasswordListener
import net.mynero.wallet.fragment.dialog.SendBottomSheetDialog
import net.mynero.wallet.livedata.SingleLiveEvent
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.AddressService
import net.mynero.wallet.service.BalanceService
import net.mynero.wallet.service.BlockchainService
import net.mynero.wallet.service.DaemonService
import net.mynero.wallet.service.HistoryService
import net.mynero.wallet.service.MoneroHandlerThread
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.ProxyService
import net.mynero.wallet.service.TxService
import net.mynero.wallet.service.UTXOService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.UriData
import java.io.File

class MainActivity : AppCompatActivity(), MoneroHandlerThread.Listener, PasswordListener {
    val restartEvents: SingleLiveEvent<*> = SingleLiveEvent<Any?>()
    var thread: MoneroHandlerThread? = null
        private set
    private var balanceService: BalanceService? = null
    private var addressService: AddressService? = null
    private var historyService: HistoryService? = null
    private var proxyService: ProxyService? = null
    private var daemonService: DaemonService? = null
    private var blockchainService: BlockchainService? = null
    private var utxoService: UTXOService? = null
    private var proceedToSend = false
    private var uriData: UriData? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val walletFile = File(applicationInfo.dataDir, Constants.WALLET_NAME)
        val walletKeysFile = File(applicationInfo.dataDir, Constants.WALLET_NAME + ".keys")
        if (walletKeysFile.exists()) {
            val promptPassword =
                PrefService.instance?.getBoolean(Constants.PREF_USES_PASSWORD, false) == true
            if (!promptPassword) {
                init(walletFile, "")
            } else {
                val passwordDialog = PasswordBottomSheetDialog()
                passwordDialog.listener = this
                passwordDialog.show(supportFragmentManager, "password_dialog")
            }
            val intent = intent
            val uri = intent.data
            if (uri != null) {
                uriData = UriData.parse(uri.toString())
                if (uriData != null) {
                    proceedToSend = true
                }
            }
        } else {
            navigate(R.id.onboarding_fragment)
        }
    }

    private fun navigate(destination: Int) {
        val activity: FragmentActivity = this
        val fm = activity.supportFragmentManager
        val navHostFragment = fm.findFragmentById(R.id.nav_host_fragment) as NavHostFragment?
        navHostFragment?.navController?.navigate(destination)
    }

    fun init(walletFile: File, password: String?) {
        val wallet = WalletManager.instance?.openWallet(walletFile.absolutePath, password ?: "")
        thread = wallet?.let { MoneroHandlerThread("WalletService", this, it) }
        thread?.let { thread ->
            TxService(thread)
            balanceService = BalanceService(thread)
            addressService = AddressService(thread)
            historyService = HistoryService(thread)
            blockchainService = BlockchainService(thread)
            daemonService = DaemonService(thread)
            utxoService = UTXOService(thread)
            thread.start()
        }
    }

    override fun onRefresh(walletSynced: Boolean) {
        if (walletSynced) utxoService?.refreshUtxos()
        historyService?.refreshHistory()
        balanceService?.refreshBalance()
        blockchainService?.refreshBlockchain()
        addressService?.refreshAddresses()
    }

    override fun onConnectionFail() {
        runOnUiThread {
            Toast.makeText(application, R.string.connection_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPasswordSuccess(password: String) {
        val walletFile = File(applicationInfo.dataDir, Constants.WALLET_NAME)
        init(walletFile, password)
        restartEvents.call()
        if (proceedToSend) {
            val sendDialog = SendBottomSheetDialog()
            sendDialog.uriData = uriData
            sendDialog.show(supportFragmentManager, null)
        }
    }

    override fun onPasswordFail() {
        runOnUiThread {
            Toast.makeText(application, R.string.bad_password, Toast.LENGTH_SHORT).show()
        }
    }
}