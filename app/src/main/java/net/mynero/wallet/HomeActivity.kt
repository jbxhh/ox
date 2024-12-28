package net.mynero.wallet

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.mynero.wallet.adapter.TransactionInfoAdapter
import net.mynero.wallet.model.Balance
import net.mynero.wallet.model.TransactionInfo
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.service.wallet.WalletService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.PreferenceUtils
import net.mynero.wallet.util.acitivity.MoneroActivity

class HomeActivity : MoneroActivity() {

    private val viewModel: HomeActivityViewModel by viewModels()

    private lateinit var walletAndAccountTextView: TextView
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

        settingsImageView = findViewById(R.id.settings_imageview)
        sendButton = findViewById(R.id.send_button)
        receiveButton = findViewById(R.id.receive_button)

        txHistoryRecyclerView = findViewById(R.id.transaction_history_recyclerview)
        unlockedBalanceTextView = findViewById(R.id.balance_unlocked_textview)
        frozenBalanceTextView = findViewById(R.id.balance_frozen_textview)
        lockedBalanceTextView = findViewById(R.id.balance_locked_textview)

        walletAndAccountTextView = findViewById(R.id.wallet_and_account_textview)
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                stop()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // label, balances and transaction history must be updated here because street mode may have been changed by another activity
        updateWalletAndAccountLabel(walletService?.getWallet())
        updateBalances(viewModel.balance.value)
        updateTransactionHistory(viewModel.transactions.value)
    }

    override fun onWalletServiceBound(walletService: WalletService) {
        updateState(walletService, walletService.getWallet())
    }

    override fun onWalletServiceNotFound() {
        stop()
    }

    private fun stop() {
        walletService?.closeWallet() // TODO: just stop the service instead?
        finish()
        if (PreferenceUtils.isMultiAccountMode(this@HomeActivity)) {
            startActivity(Intent(this@HomeActivity, WalletActivity::class.java))
        } else {
            startActivity(Intent(this@HomeActivity, StartActivity::class.java))
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

    private fun updateState(walletService: WalletService, wallet: Wallet?) {
        wallet?.let {
            updateWalletAndAccountLabel(wallet)
        }
        updateSynchronizationProgress(walletService, wallet)
        updateBalances(wallet?.getBalance())
        updateTransactionHistory(wallet?.getHistory())
    }

    override fun onWalletUpdated(wallet: Wallet) {
        runOnUiThread {
            updateState(walletService!!, wallet)
        }
    }

    override fun onBlockchainHeightFetched(height: Long) {
        val walletService = walletService!!
        runOnUiThread {
            updateSynchronizationProgress(walletService, walletService.getWallet())
        }
    }

    private fun updateWalletAndAccountLabel(wallet: Wallet?) {
        val isMultiWalletMode = PreferenceUtils.isMultiWalletMode(this)
        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(this)
        if (wallet != null && (isMultiWalletMode || isMultiAccountMode)) {
            walletAndAccountTextView.visibility = View.VISIBLE
            walletAndAccountTextView.text = "${wallet.name} / ${wallet.getAccountIndex()}"
        } else {
            walletAndAccountTextView.visibility = View.GONE
        }
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
            val textResId: Int = if (wallet != null) {
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

    private fun updateSynchronizationProgress(walletService: WalletService, wallet: Wallet?) {
        wallet?.let {
            val walletBeginSyncHeight = walletService.getWalletBeginSyncHeight()
            val walletHeight = wallet.getBlockChainHeightJ()
            val daemonHeight = walletService.getDaemonHeight()
            val diff = daemonHeight - walletHeight
            val synchronized = daemonHeight > 0 && diff == 0L
            val walletDisplayHeight = (walletHeight - 1).toString()
            val daemonDisplayHeight = if (daemonHeight > 0) (daemonHeight - 1).toString() else "???"
            progressBarText.visibility = View.VISIBLE
            if (synchronized) {
                progressBar.visibility = View.INVISIBLE
                progressBarText.text = "Synchronized at $walletDisplayHeight"
            } else {
                progressBar.isIndeterminate = false
                // Google employs the very rare kind of engineers to work on Android - engineers with negative fucking IQ
                // try switching the order of setting max and min here to see their brilliance and true ingenuity
                // and after you do that, try to find an explanation (or even a mention) of this behaviour in the docs:
                // https://developer.android.com/reference/android/widget/ProgressBar.html
                progressBar.max = daemonHeight.toInt()
                progressBar.min = walletBeginSyncHeight.toInt()
                progressBar.setProgress(walletHeight.toInt(), true)
                progressBar.visibility = View.VISIBLE
                if (walletHeight > walletBeginSyncHeight) {
                    progressBarText.text = "Synchronizing: $walletDisplayHeight / $daemonDisplayHeight ($diff blocks remaining)"
                } else {
                    progressBarText.text = "Starting wallet synchronization..."
                }
            }
        }
    }
}

internal class HomeActivityViewModel : ViewModel() {
    private val _transactions = MutableLiveData<List<TransactionInfo>>()
    val transactions: LiveData<List<TransactionInfo>> = _transactions

    private val _balance: MutableLiveData<Balance> = MutableLiveData()
    val balance: LiveData<Balance> = _balance

    fun updateTransactions(newTransactions: List<TransactionInfo>) {
        _transactions.postValue(newTransactions)
    }

    fun updateBalance(newBalance: Balance) {
        _balance.postValue(newBalance)
    }
}
