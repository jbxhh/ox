package org.monfluo.wallet.android.activity

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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.adapter.AdapterTransactionInfo
import org.monfluo.wallet.android.livedata.combineLiveDatas
import org.monfluo.wallet.android.model.MoneroUnit
import org.monfluo.wallet.android.model.Wallet
import org.monfluo.wallet.android.service.wallet.State
import org.monfluo.wallet.android.service.wallet.WalletService
import org.monfluo.wallet.android.service.wallet.WalletWithData
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.Helper
import org.monfluo.wallet.android.util.PreferenceUtils
import org.monfluo.wallet.android.util.TextHelper
import org.monfluo.wallet.android.util.component.ActivityMonero
import org.monfluo.wallet.lib.data.Balance
import org.monfluo.wallet.lib.data.TransactionView
import timber.log.Timber
import kotlin.math.max

class ActivityHome : ActivityMonero() {
    private val viewModel: ActivityHomeViewModel by viewModels()

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

    private val transactionInfoAdapter: AdapterTransactionInfo =
        AdapterTransactionInfo(
            object : AdapterTransactionInfo.Listener {
                override fun onClickTransaction(transaction: TransactionView) {
                    val intent = Intent(this@ActivityHome, ActivityTransactionDetails::class.java)
                    intent.putExtra(Constants.EXTRA_TRANSACTION_ID, transaction.transactionHash.toString())
                    startActivity(intent)
                }
            },
        )

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
        noTransactionsHeaderTextView = findViewById(R.id.no_transactions_header_text_view)
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
                left = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.left),
                top = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.top),
                right = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.right),
            )
            receiveSendButtonsLayoutView.updatePadding(bottom = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.bottom) + resources.getDimensionPixelOffset(R.dimen.padding_small))
            transactionInfoAdapter.setVoidHeight(resources.getDimensionPixelOffset(R.dimen.activity_home_recycler_view_void_height) + insets.bottom)
            return@setOnApplyWindowInsetsListener insetsCompat
        }
    }

    private fun bindBackPressedCallback() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Timber.d("Back pressed")
                    stopService(Intent(this@ActivityHome, WalletService::class.java))
                    stop()
                }
            },
        )
    }

    private fun bindListeners() {
        walletNameTextView.setOnClickListener {
            startActivity(Intent(this, ActivityWalletSettings::class.java))
        }
        accountNameTextView.setOnClickListener {
            startActivity(Intent(this, ActivityAccountList::class.java))
        }
        unlockedBalanceTextView.setOnClickListener {
            if (viewModel.balanceTapTogglesStreetMode.value!!) {
                val streetMode = viewModel.streetMode.value!!
                val newStreetMode = !streetMode
                PreferenceUtils.setStreetMode(this, newStreetMode)
                viewModel.streetMode.postValue(newStreetMode)
            }
        }
        unlockedBalanceTextView.setOnLongClickListener {
            viewModel.walletCardInfo.value?.data?.balance?.let { balance ->
                Helper.clipBoardCopy(this, "unlocked wallet balance", TextHelper.getDisplayAmount(balance.unlocked, MoneroUnit.MONERO, false))
            }
            return@setOnLongClickListener true
        }
        lockedBalanceTextView.setOnLongClickListener {
            viewModel.walletCardInfo.value?.data?.balance?.let { balance ->
                Helper.clipBoardCopy(this, "locked wallet balance", TextHelper.getDisplayAmount(balance.pending, MoneroUnit.MONERO, false))
            }
            return@setOnLongClickListener true
        }
        frozenBalanceTextView.setOnLongClickListener {
            viewModel.walletCardInfo.value?.data?.balance?.let { balance ->
                Helper.clipBoardCopy(this, "frozen wallet balance", TextHelper.getDisplayAmount(balance.frozen, MoneroUnit.MONERO, false))
            }
            return@setOnLongClickListener true
        }
        settingsImageView.setOnClickListener {
            startActivity(Intent(this, ActivityApplicationSettings::class.java))
        }
        sendButton.setOnClickListener {
            startActivity(Intent(this, ActivitySend::class.java))
        }
        receiveButton.setOnClickListener {
            startActivity(Intent(this, ActivityReceive::class.java))
        }
        enotesImageView.setOnClickListener {
            startActivity(Intent(this, ActivityEnoteList::class.java))
        }
    }

    private fun bindObservers() {
        viewModel.walletCardInfo.observe(this) { (streetMode, multiAccountMode, enableEnoteManagement, walletCard) ->
            if (walletCard != null) {
                walletNameTextView.text = walletCard.name
                walletNameTextView.contentDescription = getString(R.string.activity_home_wallet_name_content_description, walletCard.name)
                walletNameTextView.visibility = View.VISIBLE

                if (multiAccountMode) {
                    accountNameTextView.visibility = View.VISIBLE

//                    walletService?.getWalletOrLog()?.let { wallet ->
//                        val accountName = wallet.getAccountLabel(wallet.getAccountIndex())
//                        accountNameTextView.text = accountName
//                        accountNameTextView.contentDescription = getString(R.string.activity_home_account_name_content_description, accountName)
//                    }
                } else {
                    accountNameTextView.visibility = View.INVISIBLE
                }

                val balanceTextStyle = if (walletCard.isSynchronized) Typeface.BOLD else Typeface.BOLD_ITALIC

                unlockedBalanceTextView.text = TextHelper.formatUnlockedBalance(this, walletCard.balance.unlocked, MoneroUnit.MONERO, streetMode, balanceTextStyle)
                unlockedBalanceTextView.visibility = View.VISIBLE

                if (walletCard.balance.pending != 0L) {
                    lockedBalanceTextView.text = TextHelper.formatLockedBalance(this, walletCard.balance.pending, MoneroUnit.MONERO, streetMode, balanceTextStyle)
                    lockedBalanceTextView.visibility = View.VISIBLE
                } else {
                    lockedBalanceTextView.visibility = View.INVISIBLE
                }
                if (walletCard.balance.frozen != 0L) {
                    frozenBalanceTextView.text = TextHelper.formatFrozenBalance(this, walletCard.balance.frozen, MoneroUnit.MONERO, streetMode, balanceTextStyle)
                    frozenBalanceTextView.visibility = View.VISIBLE
                } else {
                    frozenBalanceTextView.visibility = View.INVISIBLE
                }

                if (enableEnoteManagement) {
                    enotesImageView.visibility = View.VISIBLE
                } else {
                    enotesImageView.visibility = View.INVISIBLE
                }
            } else {
                walletNameTextView.visibility = View.INVISIBLE
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
                transactionInfoAdapter.submitList(sortedHistory)
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

        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(this)
        val isStreetMode = PreferenceUtils.isStreetMode(this)
        val isEnableEnoteManagement = PreferenceUtils.isEnableEnoteManagement(this)
        val isBalanceTapTogglesStreetMode = PreferenceUtils.isBalanceTapTogglesStreetMode(this)

        viewModel.multiAccountMode.postValue(isMultiAccountMode)
        viewModel.streetMode.postValue(isStreetMode)
        viewModel.enoteManagement.postValue(isEnableEnoteManagement)
        viewModel.balanceTapTogglesStreetMode.postValue(isBalanceTapTogglesStreetMode)
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        val wallet = walletService.getNewWalletOrLog()
        viewModel.state.postValue(walletService.getState())
        updateNewWallet(wallet!!)
    }

    override fun onWalletServiceDisconnected() {
        stop()
    }

    override fun onWalletServiceNotFound() {
        stop()
    }

    private fun stop() {
        Timber.d("Stopping")
        walletService?.closeNewWallet()
        finish()
    }

    override fun onWalletUpdated(wallet: Wallet) {
        walletService?.let { walletService ->
            viewModel.state.postValue(walletService.getState())
        }
//        updateWallet(wallet)
    }

    override fun onNewWalletUpdated(
        walletWithData: WalletWithData,
    ) {
        updateNewWallet(walletWithData)
    }

    override fun onNewBlock(wallet: Wallet) {
        walletService?.let { walletService ->
            viewModel.state.postValue(walletService.getState())
        }
    }

    override fun onBlockchainAndTargetHeightsFetched(
        blockchainHeight: Long,
        targetHeight: Long,
    ) {
        walletService?.let { walletService ->
            viewModel.state.postValue(walletService.getState())
        }
    }

    private fun updateNewWallet(
        walletWithData: WalletWithData,
    ) {
        val (wallet, data) = walletWithData
        val name = wallet.getName()
        val isSynchronized = data.syncCompleted
        val balance = wallet.getBalance()
        val transactions = wallet.getTransactionViews()
        viewModel.cardData.postValue(WalletCardData(name, isSynchronized, balance))
        viewModel.transactions.postValue(transactions)
    }
}

internal class ActivityHomeViewModel : ViewModel() {
    val state: MutableLiveData<State> = MutableLiveData(null)

    val multiAccountMode: MutableLiveData<Boolean> = MutableLiveData(false)
    val enoteManagement: MutableLiveData<Boolean> = MutableLiveData(false)
    val balanceTapTogglesStreetMode: MutableLiveData<Boolean> = MutableLiveData(false)
    val streetMode: MutableLiveData<Boolean> = MutableLiveData(false)

    val cardData: MutableLiveData<WalletCardData?> = MutableLiveData(null)
    val transactions = MutableLiveData<List<TransactionView>?>(null)

    val walletCardInfo =
        combineLiveDatas(
            state,
            cardData,
            streetMode,
            multiAccountMode,
            enoteManagement,
        ) { state, cardData, streetMode, multiAccountMode, enableEnoteManagement ->
            WalletCardState(
                streetMode = streetMode!!,
                multiAccountMode = multiAccountMode!!,
                enableEnoteManagement = enableEnoteManagement!!,
                cardData,
            )
        }
}

internal data class WalletCardState(
    val streetMode: Boolean,
    val multiAccountMode: Boolean,
    val enableEnoteManagement: Boolean,
    val data: WalletCardData?,
)

internal data class WalletCardData(
    val name: String,
    val isSynchronized: Boolean,
    val balance: Balance,
)
