package org.monfluo.wallet

import android.content.Intent
import android.graphics.Typeface
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
import org.monfluo.wallet.adapter.TransactionInfoAdapter
import org.monfluo.wallet.livedata.combineLiveDatas
import org.monfluo.wallet.model.Balance
import org.monfluo.wallet.model.TransactionInfo
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.acitivity.MoneroActivity
import timber.log.Timber
import java.text.NumberFormat

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

    private var isFinishing: Boolean = false

    private val transactionInfoAdapter: TransactionInfoAdapter = TransactionInfoAdapter(false, object : TransactionInfoAdapter.Listener {
        override fun onClickTransaction(txInfo: TransactionInfo) {
            val intent = Intent(this@HomeActivity, TransactionActivity::class.java)
            intent.putExtra(Constants.NAV_ARG_TXINFO, txInfo.hash)
            startActivity(intent)
        }
    })

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

        txHistoryRecyclerView.layoutManager = LinearLayoutManager(this)
        txHistoryRecyclerView.adapter = transactionInfoAdapter

        bindListeners()
        bindObservers()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.d("Back pressed")
                stopService(Intent(this@HomeActivity, WalletService::class.java))
                stop()
            }
        })
    }

    private fun bindListeners() {
        unlockedBalanceTextView.setOnClickListener {
            val streetMode = viewModel.streetMode.value!!
            val newStreetMode = !streetMode
            PreferenceUtils.setStreetMode(this, newStreetMode)
            viewModel.setStreetMode(newStreetMode)
        }
        settingsImageView.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        sendButton.setOnClickListener {
            startActivity(Intent(this, SendActivity::class.java))
        }
        receiveButton.setOnClickListener {
            startActivity(Intent(this, ReceiveActivity::class.java))
        }
    }

    private fun bindObservers() {
        viewModel.showWalletLabel.observe(this) { isShowWalletLabel ->
            if (isShowWalletLabel) {
                walletService?.getWallet()?.let { wallet ->
                    walletAndAccountTextView.visibility = View.VISIBLE
                    walletAndAccountTextView.text = getString(R.string.wallet_name_and_account_label, wallet.name(), wallet.getAccountIndex())
                }
            } else {
                walletAndAccountTextView.visibility = View.GONE
            }
        }

        viewModel.syncProgress.observe(this) { (walletConnected, heights) ->
            if (walletConnected) {
                val (walletBeginSyncHeight, walletHeight, daemonHeight) = heights
                val diff = daemonHeight - walletHeight
                val synchronized = daemonHeight > 0 && diff == 0L
                val walletDisplayHeight = NumberFormat.getInstance().format(walletHeight - 1)
                val daemonDisplayHeight = if (daemonHeight > 0) NumberFormat.getInstance().format(daemonHeight - 1) else "???"
                val diffDisplay = NumberFormat.getInstance().format(diff)
                progressBarText.visibility = View.VISIBLE
                if (synchronized) {
                    progressBar.visibility = View.INVISIBLE
                    progressBarText.text = getString(R.string.synchronized_at_label, walletDisplayHeight)
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
                        progressBarText.text = getString(R.string.synchronizing_blocks_remaining, walletDisplayHeight, daemonDisplayHeight, diffDisplay)
                    } else {
                        progressBarText.text = getString(R.string.starting_wallet_synchronization)
                    }
                }
            }
        }

        viewModel.balance.observe(this) { (isSynchronized, balance, streetMode) ->
            if (balance != null) {
                val typeface = if (isSynchronized) Typeface.BOLD else Typeface.BOLD_ITALIC
                unlockedBalanceTextView.setTypeface(null, typeface)
                frozenBalanceTextView.setTypeface(null, typeface)
                lockedBalanceTextView.setTypeface(null, typeface)

                unlockedBalanceTextView.text = if (!streetMode) Wallet.getDisplayAmount(balance.unlocked) else Constants.STREET_MODE_BALANCE
                if (balance.frozen != 0L) {
                    val textFrozenBalance = if (!streetMode) Wallet.getDisplayAmount(balance.frozen) else Constants.STREET_MODE_BALANCE
                    val formattedFrozenBalance = if (balance.frozen < 0) "- $textFrozenBalance" else "+ $textFrozenBalance"
                    frozenBalanceTextView.text = getString(R.string.frozen_amount, formattedFrozenBalance)
                    frozenBalanceTextView.visibility = View.VISIBLE
                } else {
                    frozenBalanceTextView.visibility = View.GONE
                }
                if (balance.pending != 0L) {
                    val textUnconfirmedBalance = if (!streetMode) Wallet.getDisplayAmount(balance.pending) else Constants.STREET_MODE_BALANCE
                    val formattedUnconfirmedBalance = if (balance.pending < 0) "- $textUnconfirmedBalance" else "+ $textUnconfirmedBalance"
                    lockedBalanceTextView.text = getString(R.string.unconfirmed_amount, formattedUnconfirmedBalance)
                    lockedBalanceTextView.visibility = View.VISIBLE
                } else {
                    lockedBalanceTextView.visibility = View.GONE
                }
            }
        }

        viewModel.transactions.observe(this) { transactions ->
            if (transactions.isNullOrEmpty()) {
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
                val sortedHistory = transactions.sortedByDescending { it.timestamp }
                if (sortedHistory.size > 100) {
                    transactionInfoAdapter.submitList(sortedHistory.subList(0, 99))
                } else {
                    transactionInfoAdapter.submitList(sortedHistory)
                }
                txHistoryRecyclerView.visibility = View.VISIBLE
                displayEmptyHistory(
                    false,
                    this,
                    R.string.no_history_nget_some_monero_in_here,
                )
            }
        }
        viewModel.streetMode.observe(this) { streetMode ->
            transactionInfoAdapter.submitStreetMode(streetMode)
        }
    }

    override fun onResume() {
        super.onResume()

        val isMultiWalletMode = PreferenceUtils.isMultiWalletMode(this)
        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(this)
        val isStreetMode = PreferenceUtils.isStreetMode(this)

        viewModel.setMultiWalletMode(isMultiWalletMode)
        viewModel.setMultiAccountMode(isMultiAccountMode)
        viewModel.setStreetMode(isStreetMode)
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        val wallet = walletService.getWallet()
        updateHeights(walletService, wallet)
        updateState(wallet)
    }

    override fun onWalletServiceDisconnected() {
        stop()
    }

    override fun onWalletServiceNotFound() {
        stop()
    }

    override fun finish() {
        // When HomeActivity is finishing, we should (once!) start either WalletActivity or StartActivity
        if (!isFinishing) {
            isFinishing = true
            if (PreferenceUtils.isMultiAccountMode(this@HomeActivity)) {
                startActivity(Intent(this@HomeActivity, WalletActivity::class.java))
            } else {
                startActivity(Intent(this@HomeActivity, StartActivity::class.java))
            }
        }
        super.finish()
    }

    private fun stop() {
        Timber.d("Stopping")
        walletService?.closeWallet()
        finish()
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

    override fun onWalletUpdated(wallet: Wallet) {
        updateState(wallet)
    }

    override fun onNewBlock(wallet: Wallet) {
        walletService?.let { walletService ->
            updateHeights(walletService, wallet)
        }
    }

    override fun onBlockchainHeightFetched(height: Long) {
        walletService?.let { walletService ->
            updateHeights(walletService, walletService.getWallet())
        }
    }

    private fun updateHeights(walletService: WalletService, wallet: Wallet?) {
        viewModel.setHeights(Heights(walletService.getWalletBeginSyncHeight(), wallet?.getBlockChainHeightJ() ?: 0, walletService.getDaemonHeight()))
    }

    private fun updateState(wallet: Wallet?) {
        viewModel.setWalletConnected(wallet != null)
        viewModel.setBalance(wallet?.getBalance())
        viewModel.setTransactions(wallet?.getHistory())
    }
}

internal class HomeActivityViewModel : ViewModel() {
    private val _walletConnected = MutableLiveData(false)

    private val _multiWalletMode = MutableLiveData(false)

    private val _multiAccountMode = MutableLiveData(false)

    private val _streetMode = MutableLiveData(false)
    val streetMode: LiveData<Boolean> = _streetMode

    private val _heights = MutableLiveData(Heights(0L, 0L, 0L))

    private val _balance: MutableLiveData<Balance?> = MutableLiveData(null)

    private val _transactions = MutableLiveData<List<TransactionInfo>?>(null)
    val transactions: LiveData<List<TransactionInfo>?> = _transactions

    val showWalletLabel = combineLiveDatas(
        _walletConnected,
        _multiWalletMode,
        _multiAccountMode,
    ) { walletConnected, multiWalletMode, multiAccountMode ->
        return@combineLiveDatas walletConnected!! && (multiWalletMode!! || multiAccountMode!!)
    }

    val syncProgress = combineLiveDatas(
        _walletConnected,
        _heights,
    ) { walletConnected, heights ->
        Pair(walletConnected!!, heights!!)
    }

    val balance = combineLiveDatas(
        syncProgress,
        _balance,
        _streetMode,
    ) { syncProgress, balance, streetMode ->
        val (walletConnected, heights) = syncProgress!!
        val isSynchronized = if (walletConnected) {
            val (_, walletHeight, daemonHeight) = heights
            val diff = daemonHeight - walletHeight
            daemonHeight > 0 && diff == 0L
        } else {
            false
        }
        Triple(isSynchronized, balance, streetMode!!)
    }

    fun setWalletConnected(walletConnected: Boolean) {
        _walletConnected.postValue(walletConnected)
    }

    fun setMultiWalletMode(multiWalletMode: Boolean) {
        _multiWalletMode.postValue(multiWalletMode)
    }

    fun setMultiAccountMode(multiAccountMode: Boolean) {
        _multiAccountMode.postValue(multiAccountMode)
    }

    fun setStreetMode(streetMode: Boolean) {
        _streetMode.postValue(streetMode)
    }

    fun setHeights(heights: Heights) {
        _heights.postValue(heights)
    }

    fun setBalance(newBalance: Balance?) {
        _balance.postValue(newBalance)
    }

    fun setTransactions(newTransactions: List<TransactionInfo>?) {
        _transactions.postValue(newTransactions)
    }
}

internal data class Heights(val walletBeginSyncHeight: Long, val walletHeight: Long, val daemonHeight: Long)
