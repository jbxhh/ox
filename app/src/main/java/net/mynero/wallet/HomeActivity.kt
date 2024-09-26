package net.mynero.wallet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.mynero.wallet.adapter.TransactionInfoAdapter
import net.mynero.wallet.model.TransactionInfo
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.BalanceService
import net.mynero.wallet.service.BlockchainService
import net.mynero.wallet.service.DaemonService
import net.mynero.wallet.service.HistoryService
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.ProxyService
import net.mynero.wallet.util.Constants

class HomeActivity : AppCompatActivity() {

    private var startHeight: Long = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val settingsImageView = findViewById<ImageView>(R.id.settings_imageview)
        val sendButton = findViewById<Button>(R.id.send_button)
        val receiveButton = findViewById<Button>(R.id.receive_button)

        val txHistoryRecyclerView =
            findViewById<RecyclerView>(R.id.transaction_history_recyclerview)
        val unlockedBalanceTextView = findViewById<TextView>(R.id.balance_unlocked_textview)
        val lockedBalanceTextView = findViewById<TextView>(R.id.balance_locked_textview)
        val balanceService = BalanceService.instance
        val historyService = HistoryService.instance
        val blockchainService = BlockchainService.instance


        settingsImageView.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        sendButton.setOnClickListener {
            startActivity(Intent(this, SendActivity::class.java))
        }
        receiveButton.setOnClickListener {
            startActivity(Intent(this, ReceiveActivity::class.java))
        }

        ProxyService.instance?.proxyChangeEvents?.observe(this) { proxy ->
            lifecycleScope.launch(Dispatchers.IO) {
                Log.d("HomeFragment", "Updating proxy, restarting wallet. proxy=$proxy")
                val finalProxy =
                    if (proxy.isNotEmpty() && ProxyService.instance?.usingProxy == true) proxy else ""
                WalletManager.instance?.setProxy(finalProxy)
                WalletManager.instance?.wallet?.setProxy(finalProxy)
                WalletManager.instance?.wallet?.init(0)
                WalletManager.instance?.wallet?.startRefresh()
            }
        }

        DaemonService.instance?.daemonChangeEvents?.observe(this) { daemon ->
            lifecycleScope.launch(Dispatchers.IO) {
                Log.d("HomeFragment", "Updating daemon, restarting wallet. daemon=$daemon")
                WalletManager.instance?.setDaemon(daemon)
                WalletManager.instance?.wallet?.init(0)
                WalletManager.instance?.wallet?.setTrustedDaemon(daemon.trusted)
                WalletManager.instance?.wallet?.startRefresh()
            }
        }

        balanceService?.balanceInfo?.observe(this) { balanceInfo ->
            if (balanceInfo != null) {
                unlockedBalanceTextView.text = balanceInfo.unlockedDisplay
                if (balanceInfo.lockedDisplay == Constants.STREET_MODE_BALANCE || balanceInfo.isLockedBalanceZero) {
                    lockedBalanceTextView.visibility = View.INVISIBLE
                } else {
                    lockedBalanceTextView.text = getString(
                        R.string.wallet_locked_balance_text,
                        balanceInfo.lockedDisplay
                    )
                    lockedBalanceTextView.visibility = View.VISIBLE
                }
            }
        }
        val progressBar = findViewById<ProgressBar>(R.id.sync_progress_bar)
        val progressBarText = findViewById<TextView>(R.id.sync_progress_text)
        blockchainService?.height?.observe(this) { height: Long ->
            val wallet = WalletManager.instance?.wallet
            if (wallet?.isSynchronized == false) {
                if (startHeight == 0L && height != 1L) {
                    startHeight = height
                }
                val daemonHeight = blockchainService.daemonHeight
                val n = daemonHeight - height
                val x = Math.round(100 - (100f * n / (1f * daemonHeight - startHeight)))
                progressBar.isIndeterminate = height <= 1 || daemonHeight <= 0
                if (height > 1 && daemonHeight > 1) {
                    progressBar.progress = x
                    progressBarText.visibility = View.VISIBLE
                    progressBarText.text = "Syncing... $n blocks remaining"
                } else {
                    progressBarText.visibility = View.GONE
                }
            } else {
                progressBar.visibility = View.INVISIBLE
                progressBarText.visibility = View.VISIBLE
                progressBarText.text = "Synchronized at $height"
            }
        }
        val activity = this
        val adapter = TransactionInfoAdapter(object : TransactionInfoAdapter.TxInfoAdapterListener {
            override fun onClickTransaction(txInfo: TransactionInfo?) {
                val intent = Intent(activity, TransactionActivity::class.java)
                intent.putExtra(Constants.NAV_ARG_TXINFO, txInfo)
                startActivity(intent)
            }
        })
        txHistoryRecyclerView.layoutManager = LinearLayoutManager(this)
        txHistoryRecyclerView.adapter = adapter
        historyService?.history?.observe(this) { history: List<TransactionInfo> ->
            if (history.isEmpty()) {
                // DISPLAYING EMPTY WALLET HISTORY
                val wallet = WalletManager.instance?.wallet
                val textResId: Int
                val botImgResId = if (wallet != null && wallet.isSynchronized) {
                    textResId = R.string.no_history_nget_some_monero_in_here
                    R.drawable.xmrchan_empty2 // img for synchronized
                } else {
                    textResId = R.string.no_history_loading
                    R.drawable.xmrchan_loading2 // img for loading
                }
                txHistoryRecyclerView.visibility = View.GONE
                displayEmptyHistory(true, this, textResId, botImgResId)
            } else {
                // POPULATED WALLET HISTORY
                val sortedHistory = history.sortedByDescending { it.timestamp }
                if (sortedHistory.size > 100) {
                    adapter.submitList(sortedHistory.subList(0, 99))
                } else {
                    adapter.submitList(sortedHistory)
                }
                txHistoryRecyclerView.visibility = View.VISIBLE
                displayEmptyHistory(
                    false,
                    this,
                    R.string.no_history_nget_some_monero_in_here,
                    R.drawable.xmrchan_loading2
                )
            }
        }

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        samouraiTorManager?.getTorStateLiveData()?.observe(this) {
            samouraiTorManager.getProxy()?.address()?.let { socketAddress ->
                if (socketAddress.toString().isEmpty()) return@let
                if (ProxyService.instance?.usingProxy == true && ProxyService.instance?.useBundledTor == true) {
                    val proxyString = socketAddress.toString().substring(1)
                    val address = proxyString.split(":")[0]
                    val port = proxyString.split(":")[1]

                    if (WalletManager.instance?.proxy != proxyString)
                        refreshProxy(address, port)
                }
            }
        }

    }

    private fun refreshProxy(proxyAddress: String, proxyPort: String) {
        val cachedProxyAddress = ProxyService.instance?.proxyAddress
        val cachedProxyPort = ProxyService.instance?.proxyPort
        val currentWalletProxy = WalletManager.instance?.proxy
        val newProxy = "$proxyAddress:$proxyPort"
        if ((proxyAddress != cachedProxyAddress) || (proxyPort != cachedProxyPort) || (newProxy != currentWalletProxy && newProxy != ":")) {
            ProxyService.instance?.updateProxy(proxyAddress, proxyPort)
        }
    }

    private fun displayEmptyHistory(
        display: Boolean,
        view: HomeActivity,
        textResId: Int,
        botImgResId: Int
    ) {
        val mnrjTextView = view.findViewById<TextView>(R.id.monerochan_empty_tx_textview)
        val textView = view.findViewById<TextView>(R.id.empty_tx_textview)
        val botImageView = view.findViewById<ImageView>(R.id.monerochan_imageview)
        view.findViewById<View>(R.id.no_history_layout).visibility =
            if (display) View.VISIBLE else View.GONE
        val displayMonerochan =
            PrefService.instance?.getBoolean(Constants.PREF_MONEROCHAN, Constants.DEFAULT_PREF_MONEROCHAN) == true
        if (displayMonerochan) {
            botImageView.visibility = View.VISIBLE
            mnrjTextView.visibility = View.VISIBLE
            textView.visibility = View.GONE
        } else {
            botImageView.visibility = View.GONE
            mnrjTextView.visibility = View.GONE
            textView.visibility = View.VISIBLE
        }
        botImageView.setImageResource(botImgResId)
        mnrjTextView.setText(textResId)
        textView.setText(textResId)
    }
}