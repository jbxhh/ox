package org.monfluo.wallet.activity

import android.icu.text.DateFormat
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.livedata.combineLiveDatas
import org.monfluo.wallet.model.MoneroUnit
import org.monfluo.wallet.model.TransactionInfo
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.TextHelper
import org.monfluo.wallet.util.acitivity.MoneroActivity
import org.monfluo.wallet.R
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.max

class ActivityTransactionDetails : MoneroActivity() {

    private val viewModel: TransactionActivityViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView
    private lateinit var txActionTextView: TextView
    private lateinit var confLabel2: TextView
    private lateinit var txKeyLabelTextView: TextView
    private lateinit var txHashTextView: TextView
    private lateinit var txKeyTextView: TextView
    private lateinit var txConfTextView: TextView
    private lateinit var copyTxAddressImageButton: ImageButton
    private lateinit var copyTxHashImageButton: ImageButton
    private lateinit var copyTxKeyImageButton: ImageButton
    private lateinit var txDateTextView: TextView
    private lateinit var txAmountTextView: TextView
    private lateinit var blockHeightTextView: TextView
    private lateinit var addressTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction)

        layoutView = findViewById(R.id.transaction_activity_view)
        backImageView = findViewById(R.id.back_imageview)

        txActionTextView = findViewById(R.id.transaction_action_textview)
        confLabel2 = findViewById(R.id.transaction_conf_label2_textview)
        txKeyLabelTextView = findViewById(R.id.transaction_key_label_textview)
        txHashTextView = findViewById(R.id.transaction_hash_textview)
        txKeyTextView = findViewById(R.id.transaction_key_textview)
        txConfTextView = findViewById(R.id.transaction_conf_textview)
        copyTxAddressImageButton = findViewById(R.id.copy_txaddress_imagebutton)
        copyTxHashImageButton = findViewById(R.id.copy_txhash_imagebutton)
        copyTxKeyImageButton = findViewById(R.id.copy_key_imagebutton)
        txDateTextView = findViewById(R.id.transaction_date_textview)
        txAmountTextView = findViewById(R.id.transaction_amount_textview)
        blockHeightTextView = findViewById(R.id.tx_block_height_textview)
        addressTextView = findViewById(R.id.transaction_address_textview)

        if (viewModel.txHash == null) {
            viewModel.txHash = intent.extras?.getString(Constants.EXTRA_TRANSACTION_ID)
        }

        if (viewModel.txHash == null) {
            finish()
        }

        bindWindowInsetsListener()
        bindObservers()
        bindListeners()
    }

    private fun bindWindowInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(layoutView) { _, insetsCompat ->
            val insets = insetsCompat.getInsets(systemBars() or displayCutout())
            layoutView.updatePadding(
                left = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.left),
                top = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.top),
                right = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.right),
                bottom = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.bottom)
            )
            return@setOnApplyWindowInsetsListener insetsCompat
        }
    }

    private fun bindListeners() {
        backImageView.setOnClickListener {
            finish()
        }
        txAmountTextView.setOnClickListener {
            val streetMode = viewModel.streetMode.value!!
            val newStreetMode = !streetMode
            PreferenceUtils.setStreetMode(this, newStreetMode)
            viewModel.setStreetMode(newStreetMode)
        }
        copyTxHashImageButton.setOnClickListener {
            val txInfo = viewModel.transactionInfo.value
            if (txInfo != null) {
                Helper.clipBoardCopy(this, "transaction_hash", txInfo.hash)
            }
        }
        copyTxKeyImageButton.setOnClickListener {
            val txInfo = viewModel.transactionInfo.value
            if (txInfo != null) {
                val key = txKeyTextView.text.toString()
                Helper.clipBoardCopy(this, "transaction_key", key)
            }
        }
        copyTxAddressImageButton.setOnClickListener {
            val txInfo = viewModel.transactionInfo.value
            if (txInfo != null) {
                val destination = addressTextView.text.toString()
                Helper.clipBoardCopy(this, "transaction_address", destination)
            }
        }
    }

    private fun bindObservers() {
        viewModel.streetModeAndTransactionInfo.observe(this) { (streetMode, transactionInfo) ->
            if (transactionInfo == null) {
                return@observe
            }
            val wallet = walletService?.getWalletOrLog() ?: return@observe
            txHashTextView.text = transactionInfo.hash
            val txKey = wallet.getTxKey(transactionInfo.hash)
            if (txKey.isNotEmpty()) {
                txKeyTextView.text = txKey
                txKeyLabelTextView.visibility = View.VISIBLE
                txKeyTextView.visibility = View.VISIBLE
                copyTxKeyImageButton.visibility = View.VISIBLE
            } else {
                txKeyLabelTextView.visibility = View.GONE
                txKeyTextView.visibility = View.GONE
                copyTxKeyImageButton.visibility = View.GONE
            }
            txConfTextView.text = NumberFormat.getInstance().format(transactionInfo.confirmations)
            txDateTextView.text = DateFormat.getInstance().format(Date(transactionInfo.timestamp * 1000))
            if (transactionInfo.confirmations > 1) {
                confLabel2.text = getString(R.string.transaction_conf_desc2_confirmed)
                blockHeightTextView.text = NumberFormat.getInstance().format(transactionInfo.blockheight)
                blockHeightTextView.visibility = View.VISIBLE
            } else if (transactionInfo.confirmations == 1L) {
                confLabel2.text = getString(R.string.transaction_conf_1_desc2_confirmed)
                blockHeightTextView.text = NumberFormat.getInstance().format(transactionInfo.blockheight)
                blockHeightTextView.visibility = View.VISIBLE
            } else {
                blockHeightTextView.visibility = View.GONE
                confLabel2.text = getString(R.string.transaction_conf_desc2_unconfirmed)
            }
            val balanceString = TextHelper.getDisplayAmount(transactionInfo.amount, MoneroUnit.MONERO, streetMode)
            if (transactionInfo.direction === TransactionInfo.Direction.In) {
                txActionTextView.text = getString(R.string.transaction_action_recv)
                txAmountTextView.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.oled_positiveColor
                    )
                )
            } else {
                txActionTextView.text = getString(R.string.transaction_action_sent)
                txAmountTextView.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.oled_negativeColor
                    )
                )
            }
            txAmountTextView.text = balanceString
            when (transactionInfo.direction) {
                TransactionInfo.Direction.In -> {
                    addressTextView.text = wallet.getSubaddress(transactionInfo.accountIndex, transactionInfo.addressIndex)
                    copyTxAddressImageButton.visibility = View.VISIBLE
                }
                TransactionInfo.Direction.Out -> {
                    if (transactionInfo.transfers.isEmpty()) {
                        addressTextView.text = getString(R.string.unknown_recipient)
                        copyTxAddressImageButton.visibility = View.INVISIBLE
                    } else if (transactionInfo.transfers.size == 1) {
                        addressTextView.text = transactionInfo.transfers[0].address
                        copyTxAddressImageButton.visibility = View.VISIBLE
                    } else {
                        addressTextView.text = getString(R.string.multiple_addresses)
                        copyTxAddressImageButton.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val isStreetModeMode = PreferenceUtils.isStreetMode(this)

        viewModel.setStreetMode(isStreetModeMode)
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService.getWallet()?.let { wallet ->
            updateState(wallet)
        }
    }

    private fun updateState(wallet: Wallet) {
        val transactions = wallet.getHistory()
        val newTransactionInfo = transactions.find { it.hash == viewModel.txHash }
        viewModel.updateTransactionInfo(newTransactionInfo)
    }

    override fun onWalletUpdated(wallet: Wallet) {
        updateState(wallet)
    }
}

internal class TransactionActivityViewModel : ViewModel() {
    var txHash: String? = null

    private val _streetMode = MutableLiveData(false)
    val streetMode: LiveData<Boolean> = _streetMode

    private val _transactionInfo: MutableLiveData<TransactionInfo?> = MutableLiveData()
    val transactionInfo: LiveData<TransactionInfo?> = _transactionInfo

    val streetModeAndTransactionInfo = combineLiveDatas(
        _streetMode,
        _transactionInfo,
    ) { streetMode, transactionInfo ->
        return@combineLiveDatas Pair(streetMode!!, transactionInfo)
    }


    fun setStreetMode(streetMode: Boolean) {
        _streetMode.postValue(streetMode)
    }

    fun updateTransactionInfo(newTransactionInfo: TransactionInfo?) {
        _transactionInfo.postValue(newTransactionInfo)
    }
}