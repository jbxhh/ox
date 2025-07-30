package org.monfluo.wallet

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.adapter.TransactionInfoAdapter
import org.monfluo.wallet.data.Heights
import org.monfluo.wallet.livedata.combineLiveDatas
import org.monfluo.wallet.model.Balance
import org.monfluo.wallet.model.MoneroUnit
import org.monfluo.wallet.model.TransactionInfo
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.TextHelper
import org.monfluo.wallet.util.acitivity.MoneroActivity
import timber.log.Timber
import kotlin.math.max

class HomeActivity : MoneroActivity() {

    private val viewModel: HomeActivityViewModel by viewModels()

    private lateinit var layoutView: View

    private lateinit var walletCardLayoutView: View
    private lateinit var walletNameTextView: TextView
    private lateinit var accountNameTextView: TextView

    private lateinit var unlockedBalanceTextView: TextView
    private lateinit var lockedBalanceTextView: TextView
    private lateinit var frozenBalanceTextView: TextView

    private lateinit var settingsImageView: ImageView
    private lateinit var enotesImageView: ImageView

    private lateinit var noTransactionsLayoutView: View
    private lateinit var noTransactionsHeaderTextView: TextView
    private lateinit var receiveSendButtonsLayoutView: View
    private lateinit var sendButton: Button
    private lateinit var receiveButton: Button

    private lateinit var transactionHistoryRecyclerView: RecyclerView

    private val transactionInfoAdapter: TransactionInfoAdapter = TransactionInfoAdapter(object : TransactionInfoAdapter.Listener {
        override fun onClickTransaction(transactionInfo: TransactionInfo) {
            val intent = Intent(this@HomeActivity, TransactionActivity::class.java)
            intent.putExtra(Constants.EXTRA_TRANSACTION_ID, transactionInfo.hash)
            startActivity(intent)
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        layoutView = findViewById(R.id.home_activity_main_view)
        walletCardLayoutView = findViewById(R.id.wallet_card_layout)
        walletNameTextView = findViewById(R.id.wallet_name_textview)
        accountNameTextView = findViewById(R.id.account_name_textview)
        unlockedBalanceTextView = findViewById(R.id.balance_unlocked_textview)
        lockedBalanceTextView = findViewById(R.id.balance_locked_textview)
        frozenBalanceTextView = findViewById(R.id.balance_frozen_textview)
        settingsImageView = findViewById(R.id.settings_imageview)
        enotesImageView = findViewById(R.id.enotes_imageview)
        noTransactionsLayoutView = findViewById(R.id.no_transactions_layout)
        noTransactionsHeaderTextView  = findViewById(R.id.no_transactions_header_text_view)
        receiveSendButtonsLayoutView = findViewById(R.id.receive_send_buttons_layout)
        sendButton = findViewById(R.id.send_button)
        receiveButton = findViewById(R.id.receive_button)
        transactionHistoryRecyclerView = findViewById(R.id.transaction_history_recyclerview)

        transactionHistoryRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionHistoryRecyclerView.adapter = transactionInfoAdapter

        bindWindowInsetsListener()
        bindBackPressedCallback()
        bindListeners()
        bindObservers()
    }

    private fun bindWindowInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(layoutView) { _, insetsCompat ->
            val insets = insetsCompat.getInsets(systemBars() or displayCutout())
            layoutView.updatePadding(
                left = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.left),
                top = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.top),
                right = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.right),
            )
            receiveSendButtonsLayoutView.updatePadding(bottom = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.bottom) + resources.getDimensionPixelOffset(R.dimen.additional_padding))
            transactionInfoAdapter.setVoidHeight(resources.getDimensionPixelOffset(R.dimen.activity_home_recycler_view_void_height) + insets.bottom)
            return@setOnApplyWindowInsetsListener insetsCompat
        }
    }

    private fun bindBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.d("Back pressed")
                stopService(Intent(this@HomeActivity, WalletService::class.java))
                stop()
            }
        })
    }

    private fun bindListeners() {
        walletNameTextView.setOnClickListener {
            startActivity(Intent(this, WalletSettingsActivity::class.java))
        }
        accountNameTextView.setOnClickListener {
            startActivity(Intent(this, AccountsActivity::class.java))
        }
        unlockedBalanceTextView.setOnClickListener {
            if (viewModel.balanceTapTogglesStreetMode.value!!) {
                val streetMode = viewModel.streetMode.value!!
                val newStreetMode = !streetMode
                PreferenceUtils.setStreetMode(this, newStreetMode)
                viewModel.setStreetMode(newStreetMode)
            }
        }
        settingsImageView.setOnClickListener {
            startActivity(Intent(this, ApplicationSettingsActivity::class.java))
        }
        sendButton.setOnClickListener {
            startActivity(Intent(this, SendActivity::class.java))
        }
        receiveButton.setOnClickListener {
            startActivity(Intent(this, ReceiveActivity::class.java))
        }
        enotesImageView.setOnClickListener {
            startActivity(Intent(this, EnotesActivity::class.java))
        }
    }

    private fun bindObservers() {
        viewModel.walletCardInfo.observe(this) { (walletConnected, isSynchronized, streetMode, multiAccountMode, enableEnoteManagement, balance) ->
            val walletName = walletService?.getWalletName()

            if (walletName != null) {
                walletNameTextView.text = walletName
            }

            if (walletConnected) {
                if (multiAccountMode) {
                    accountNameTextView.visibility = View.VISIBLE

                    walletService?.getWalletOrLog()?.let { wallet ->
                        val accountName = wallet.getAccountLabel(wallet.getAccountIndex())
                        accountNameTextView.text = accountName
                    }
                } else {
                    accountNameTextView.visibility = View.INVISIBLE
                }

                if (balance != null) {
                    val balanceTextStyle = if (isSynchronized) Typeface.BOLD else Typeface.BOLD_ITALIC

                    unlockedBalanceTextView.text = TextHelper.formatUnlockedBalance(this, balance.unlocked, MoneroUnit.MONERO, streetMode, balanceTextStyle)
                    unlockedBalanceTextView.visibility = View.VISIBLE

                    if (balance.locked != 0L) {
                        lockedBalanceTextView.text = TextHelper.formatLockedBalance(this, balance.locked, MoneroUnit.MONERO, streetMode, balanceTextStyle)
                        lockedBalanceTextView.visibility = View.VISIBLE
                    } else {
                        lockedBalanceTextView.visibility = View.INVISIBLE
                    }
                    if (balance.frozen != 0L) {
                        frozenBalanceTextView.text = TextHelper.formatFrozenBalance(this, balance.frozen, MoneroUnit.MONERO, streetMode, balanceTextStyle)
                        frozenBalanceTextView.visibility = View.VISIBLE
                    } else {
                        frozenBalanceTextView.visibility = View.INVISIBLE
                    }
                } else {
                    unlockedBalanceTextView.visibility = View.INVISIBLE
                    lockedBalanceTextView.visibility = View.INVISIBLE
                    frozenBalanceTextView.visibility = View.INVISIBLE
                }

                if (enableEnoteManagement) {
                    enotesImageView.visibility = View.VISIBLE
                } else {
                    enotesImageView.visibility = View.INVISIBLE
                }
            } else {
                accountNameTextView.visibility = View.INVISIBLE
                unlockedBalanceTextView.visibility = View.INVISIBLE
                lockedBalanceTextView.visibility = View.INVISIBLE
                frozenBalanceTextView.visibility = View.INVISIBLE
                enotesImageView.visibility = View.INVISIBLE
            }
        }

        viewModel.transactions.observe(this) { transactions ->
            if (transactions == null) {
                noTransactionsLayoutView.visibility = View.VISIBLE
                noTransactionsHeaderTextView.text = getString(R.string.loading_wallet_header)
                transactionHistoryRecyclerView.visibility = View.GONE
            } else if (transactions.isEmpty()) {
                noTransactionsLayoutView.visibility = View.VISIBLE
                noTransactionsHeaderTextView.text = getString(R.string.no_transactions_header)
                transactionHistoryRecyclerView.visibility = View.GONE
            } else {
                val sortedHistory = transactions.sortedByDescending { it.timestamp }
                if (sortedHistory.size > 100) {
                    transactionInfoAdapter.submitList(sortedHistory.subList(0, 99))
                } else {
                    transactionInfoAdapter.submitList(sortedHistory)
                }
                noTransactionsLayoutView.visibility = View.GONE
                transactionHistoryRecyclerView.visibility = View.VISIBLE
            }
        }

        viewModel.streetMode.observe(this) { streetMode ->
            transactionInfoAdapter.submitStreetMode(streetMode)
        }

        viewModel.enoteManagement.observe(this) { enableEnoteManagement ->
            if (enableEnoteManagement) {
                enotesImageView.visibility = View.VISIBLE
            } else {
                enotesImageView.visibility = View.INVISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val isMultiWalletMode = PreferenceUtils.isMultiWalletMode(this)
        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(this)
        val isStreetMode = PreferenceUtils.isStreetMode(this)
        val isEnableEnoteManagement = PreferenceUtils.isEnableEnoteManagement(this)
        val isBalanceTapTogglesStreetMode = PreferenceUtils.isBalanceTapTogglesStreetMode(this)

        viewModel.setMultiWalletMode(isMultiWalletMode)
        viewModel.setMultiAccountMode(isMultiAccountMode)
        viewModel.setStreetMode(isStreetMode)
        viewModel.setEnoteManagement(isEnableEnoteManagement)
        viewModel.setBalanceTapTogglesStreetMode(isBalanceTapTogglesStreetMode)
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

    private fun stop() {
        Timber.d("Stopping")
        walletService?.closeWallet()
        finish()
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
    private val _enoteManagement = MutableLiveData(false)
    val enoteManagement: LiveData<Boolean> = _enoteManagement
    private val _balanceTapTogglesStreetMode = MutableLiveData(false)
    val balanceTapTogglesStreetMode: LiveData<Boolean> = _balanceTapTogglesStreetMode

    private val _streetMode = MutableLiveData(false)
    val streetMode: LiveData<Boolean> = _streetMode

    private val _heights = MutableLiveData(Heights(0L, 0L, 0L))

    private val _balance: MutableLiveData<Balance?> = MutableLiveData(null)

    private val _transactions = MutableLiveData<List<TransactionInfo>?>(null)
    val transactions: LiveData<List<TransactionInfo>?> = _transactions

    private val syncProgress = combineLiveDatas(
        _walletConnected,
        _heights,
    ) { walletConnected, heights ->
        Pair(walletConnected!!, heights!!)
    }

    val walletCardInfo = combineLiveDatas(
        syncProgress,
        _balance,
        _streetMode,
        _multiAccountMode,
        _enoteManagement,
    ) { syncProgress, balance, streetMode, multiAccountMode, enableEnoteManagement ->
        val (walletConnected, heights) = syncProgress!!
        val isSynchronized = if (walletConnected) {
            val (_, walletHeight, daemonHeight) = heights
            val diff = daemonHeight - walletHeight
            daemonHeight > 0 && diff == 0L
        } else {
            false
        }
        WalletCardState(
            walletConnected = walletConnected,
            isSynchronized = isSynchronized,
            streetMode = streetMode!!,
            multiAccountMode = multiAccountMode!!,
            enableEnoteManagement = enableEnoteManagement!!,
            balance = balance
        )
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

    fun setEnoteManagement(enoteManagement: Boolean) {
        _enoteManagement.postValue(enoteManagement)
    }

    fun setBalanceTapTogglesStreetMode(balanceTapTogglesStreetMode: Boolean) {
        _balanceTapTogglesStreetMode.postValue(balanceTapTogglesStreetMode)
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

internal data class WalletCardState(
    val walletConnected: Boolean,
    val isSynchronized: Boolean,
    val streetMode: Boolean,
    val multiAccountMode: Boolean,
    val enableEnoteManagement: Boolean,
    val balance: Balance?,
)