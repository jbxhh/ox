package net.mynero.wallet

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.mynero.wallet.adapter.TransactionInfoAdapter
import net.mynero.wallet.data.DefaultNode
import net.mynero.wallet.model.Balance
import net.mynero.wallet.model.CoinsInfo
import net.mynero.wallet.model.TransactionInfo
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.ProxyService
import net.mynero.wallet.service.wallet.WalletServiceObserver
import net.mynero.wallet.service.wallet.WalletService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.PreferenceUtils
import timber.log.Timber

class HomeActivity : AppCompatActivity(), WalletServiceObserver {

    private val viewModel: HomeActivityViewModel by viewModels()

    private var walletService: WalletService? = null

    private lateinit var walletName: String
    private lateinit var walletPassword: String

    private lateinit var progressBar: ProgressBar
    private lateinit var progressBarText: TextView

    private lateinit var settingsImageView: ImageView
    private lateinit var sendButton: Button
    private lateinit var receiveButton: Button

    private lateinit var txHistoryRecyclerView: RecyclerView
    private lateinit var unlockedBalanceTextView: TextView
    private lateinit var frozenBalanceTextView: TextView
    private lateinit var lockedBalanceTextView: TextView

    private lateinit var transactionInfoAdapter: TransactionInfoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        walletName = intent.extras?.getString(Constants.EXTRA_WALLET_NAME)!!
        walletPassword = intent.extras?.getString(Constants.EXTRA_WALLET_PASSWORD)!!

        settingsImageView = findViewById(R.id.settings_imageview)
        sendButton = findViewById(R.id.send_button)
        receiveButton = findViewById(R.id.receive_button)

        txHistoryRecyclerView = findViewById(R.id.transaction_history_recyclerview)
        unlockedBalanceTextView = findViewById(R.id.balance_unlocked_textview)
        frozenBalanceTextView = findViewById(R.id.balance_frozen_textview)
        lockedBalanceTextView = findViewById(R.id.balance_locked_textview)

        progressBar = findViewById(R.id.sync_progress_bar)
        progressBarText = findViewById(R.id.sync_progress_text)

        val streetMode = PreferenceUtils.isStreetMode(this)
        transactionInfoAdapter = TransactionInfoAdapter(streetMode, object : TransactionInfoAdapter.TxInfoAdapterListener {
            override fun onClickTransaction(txInfo: TransactionInfo) {
                val intent = Intent(this@HomeActivity, TransactionActivity::class.java)
                intent.putExtra(Constants.NAV_ARG_TXINFO, txInfo.hash)
                startActivity(intent)
            }
        })

        txHistoryRecyclerView.layoutManager = LinearLayoutManager(this)
        txHistoryRecyclerView.adapter = transactionInfoAdapter

        settingsImageView.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        sendButton.setOnClickListener {
            startActivity(Intent(this, SendActivity::class.java))
        }
        receiveButton.setOnClickListener {
            startActivity(Intent(this, ReceiveActivity::class.java))
        }

        viewModel.balance.observe(this) { balance ->
            updateBalances(balance)
        }

        viewModel.transactions.observe(this) { history ->
            updateTransactionHistory(history)
        }

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        samouraiTorManager?.getTorStateLiveData()?.observe(this) {
            samouraiTorManager.getProxy()?.address()?.let { socketAddress ->
                if (socketAddress.toString().isEmpty()) return@let
                if (ProxyService.instance?.usingProxy == true && ProxyService.instance?.useBundledTor == true) {
                    val proxyString = socketAddress.toString().substring(1)
                    val address = proxyString.split(":")[0]
                    val port = proxyString.split(":")[1]

                    if (WalletManager.instance.proxy != proxyString)
                        refreshProxy(address, port)
                }
            }
        }

        connectWalletService()
    }

    override fun onResume() {
        super.onResume()
        updateBalances(viewModel.balance.value)
        updateTransactionHistory(viewModel.transactions.value)
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val walletService = (service as WalletService.WalletServiceBinder).service
            this@HomeActivity.walletService = walletService
            walletService.addObserver(this@HomeActivity)
            val node = PreferenceUtils.getOrSetDefaultNode(this@HomeActivity, DefaultNode.defaultNode())
            walletService.openWallet(walletName, walletPassword, node.address, node.username ?: "", node.password ?: "", node.trusted, PreferenceUtils.getProxyIfEnabled(applicationContext) ?: "")
        }

        override fun onServiceDisconnected(className: ComponentName) {
            walletService = null
        }
    }

    private fun connectWalletService() {
        val intent = Intent(applicationContext, WalletService::class.java)
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    private fun refreshProxy(proxyAddress: String, proxyPort: String) {
        val cachedProxyAddress = ProxyService.instance?.proxyAddress
        val cachedProxyPort = ProxyService.instance?.proxyPort
        val currentWalletProxy = WalletManager.instance.proxy
        val newProxy = "$proxyAddress:$proxyPort"
        if ((proxyAddress != cachedProxyAddress) || (proxyPort != cachedProxyPort) || (newProxy != currentWalletProxy && newProxy != ":")) {
//            ProxyService.instance?.updateProxy(proxyAddress, proxyPort)
        }
    }

    private fun displayEmptyHistory(
        display: Boolean,
        view: HomeActivity,
        textResId: Int,
    ) {
        val textView = view.findViewById<TextView>(R.id.empty_tx_textview)
        view.findViewById<View>(R.id.no_history_layout).visibility =
            if (display) View.VISIBLE else View.GONE
            textView.visibility = View.VISIBLE
        textView.setText(textResId)
    }

    override fun onBlockchainHeightFetched(height: Long) {
        updateSynchronizationProgress()
    }

    override fun onEnotesRefreshed(enotes: List<CoinsInfo>, balance: Balance) {
        viewModel.updateBalance(balance)
    }

    override fun onWalletHistoryRefreshed(transactions: List<TransactionInfo>) {
        viewModel.updateTransactions(transactions)
    }

    override fun onRefreshed() {
        updateSynchronizationProgress()
    }

    override fun onNewBlock(height: Long) {
        updateSynchronizationProgress()
    }

    private fun updateBalances(balance: Balance?) {
        if (balance != null) {
            val streetMode = PreferenceUtils.isStreetMode(applicationContext)
            unlockedBalanceTextView.text = if (!streetMode) Wallet.getDisplayAmount(balance.unlocked) else Constants.STREET_MODE_BALANCE
            if (balance.frozen != 0L) {
                val textFrozenBalance = if (!streetMode) Wallet.getDisplayAmount(balance.frozen) else Constants.STREET_MODE_BALANCE
                val formattedFrozenBalance = if (balance.frozen < 0) "- $textFrozenBalance" else "+ $textFrozenBalance"
                frozenBalanceTextView.text = "$formattedFrozenBalance frozen"
                frozenBalanceTextView.visibility = View.VISIBLE
            } else {
                frozenBalanceTextView.visibility = View.GONE
            }
            if (balance.pending != 0L) {
                val textUnconfirmedBalance = if (!streetMode) Wallet.getDisplayAmount(balance.pending) else Constants.STREET_MODE_BALANCE
                val formattedUnconfirmedBalance = if (balance.pending < 0) "- $textUnconfirmedBalance" else "+ $textUnconfirmedBalance"
                lockedBalanceTextView.text = "$formattedUnconfirmedBalance unconfirmed"
                lockedBalanceTextView.visibility = View.VISIBLE
            } else {
                lockedBalanceTextView.visibility = View.GONE
            }
        }
    }

    private fun updateTransactionHistory(history: List<TransactionInfo>?) {
        if (history.isNullOrEmpty()) {
            val wallet = walletService?.getWallet()
            val textResId: Int = if (wallet != null && wallet.isSynchronized) {
                R.string.no_history_nget_some_monero_in_here
            } else {
                R.string.no_history_loading
            }
            txHistoryRecyclerView.visibility = View.GONE
            displayEmptyHistory(true, this, textResId)
        } else {
            // POPULATED WALLET HISTORY
            val sortedHistory = history.sortedByDescending { it.timestamp }
            if (sortedHistory.size > 100) {
                transactionInfoAdapter.submitList(sortedHistory.subList(0, 99))
            } else {
                transactionInfoAdapter.submitList(sortedHistory)
            }
            transactionInfoAdapter.submitStreetMode(PreferenceUtils.isStreetMode(applicationContext))
            txHistoryRecyclerView.visibility = View.VISIBLE
            displayEmptyHistory(
                false,
                this,
                R.string.no_history_nget_some_monero_in_here,
            )
        }
    }

    private fun updateSynchronizationProgress() {
        walletService?.let { walletService ->
            val walletBeginSyncHeight = walletService.getWalletBeginSyncHeight()
            val walletHeight = walletService.getWalletOrThrow().getBlockChainHeightJ()
            val daemonHeight = walletService.getDaemonHeight()
            Timber.e("walletHeight = $walletHeight, daemonHeight = $daemonHeight")
            val diff = daemonHeight - walletHeight
            val synchronized = daemonHeight > 0 && diff < 2
            runOnUiThread {
                progressBarText.visibility = View.VISIBLE
                if (synchronized) {
                    progressBar.visibility = View.INVISIBLE
                    progressBarText.text = "Synchronized at ${walletHeight}"
                } else if (daemonHeight > 0) {
                    progressBar.isIndeterminate = false
                    // Google employs the very rare kind of engineers to work on Android - engineers with negative fucking IQ
                    // try switching the order of setting max and min here to see their brilliance and true ingenuity
                    // and after you do that, try to find an explanation (or even a mention) of this behaviour in the docs:
                    // https://developer.android.com/reference/android/widget/ProgressBar.html
                    progressBar.max = daemonHeight.toInt()
                    progressBar.min = walletBeginSyncHeight.toInt()
                    progressBar.setProgress(walletHeight.toInt(), true)
                    progressBar.visibility = View.VISIBLE
                    progressBarText.text = "Synchronizing! $walletHeight / $daemonHeight ($diff blocks remaining)"
                } else {
                    progressBar.visibility = View.INVISIBLE
                    progressBar.isIndeterminate = true
                    progressBarText.text = "Connecting..."
                }
            }
        }
    }
}

internal class HomeActivityViewModel : ViewModel() {
    val walletPassword: String = ""

    private val _height: MutableLiveData<HeightInfo> = MutableLiveData()
    val height: LiveData<HeightInfo> = _height

    private val _transactions = MutableLiveData<List<TransactionInfo>>()
    val transactions: LiveData<List<TransactionInfo>> = _transactions

    private val _balance: MutableLiveData<Balance> = MutableLiveData()
    val balance: LiveData<Balance> = _balance

    fun updateHeight(newHeightInfo: HeightInfo) {
        _height.postValue(newHeightInfo)
    }

    fun updateTransactions(newTransactions: List<TransactionInfo>) {
        _transactions.postValue(newTransactions)
    }

    fun updateBalance(newBalance: Balance) {
        _balance.postValue(newBalance)
    }
}

internal data class HeightInfo(val daemon: Long, val wallet: Long)
