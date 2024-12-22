package net.mynero.wallet

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import net.mynero.wallet.model.TransactionInfo
import net.mynero.wallet.service.wallet.WalletServiceObserver
import net.mynero.wallet.service.wallet.WalletService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.DateHelper
import net.mynero.wallet.util.Helper
import net.mynero.wallet.util.PreferenceUtils
import java.util.Calendar
import java.util.Date
import java.util.Objects

class TransactionActivity : AppCompatActivity(), WalletServiceObserver {

    private val viewModel: TransactionActivityViewModel by viewModels()

    private var walletService: WalletService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction)

        val cal = Calendar.getInstance()
        val tz = cal.timeZone //get the local time zone.
        DateHelper.DATETIME_FORMATTER.timeZone = tz

        if (viewModel.txHash == null) {
            viewModel.txHash = intent.extras?.getString(Constants.NAV_ARG_TXINFO)
        }

        if (viewModel.txHash == null) {
            finish()
        }

        bindObservers()
        bindListeners()

        bindService(Intent(applicationContext, WalletService::class.java), connection, 0)
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val walletService = (service as WalletService.WalletServiceBinder).service
            this@TransactionActivity.walletService = walletService
            walletService.addObserver(this@TransactionActivity)
            walletService.refreshTransactionsHistory()
        }

        override fun onServiceDisconnected(className: ComponentName) {}
    }

    private fun bindListeners() {
        val copyTxHashImageButton = findViewById<ImageButton>(R.id.copy_txhash_imagebutton)
        copyTxHashImageButton.setOnClickListener {
            val txInfo = viewModel.transactionInfo.value
            if (txInfo != null) {
                Helper.clipBoardCopy(this, "transaction_hash", txInfo.hash)
            }
        }
        val copyTxAddressImageButton =
            findViewById<ImageButton>(R.id.copy_txaddress_imagebutton)
        val addressTextView = findViewById<TextView>(R.id.transaction_address_textview)
        copyTxAddressImageButton.setOnClickListener {
            val txInfo = viewModel.transactionInfo.value
            if (txInfo != null) {
                val destination = addressTextView.text.toString()
                Helper.clipBoardCopy(this, "transaction_address", destination)
            }
        }
    }

    private fun bindObservers() {
        val txActionTextView = findViewById<TextView>(R.id.transaction_action_textview)
        val confLabel2 = findViewById<TextView>(R.id.transaction_conf_label2_textview)
        val txHashTextView = findViewById<TextView>(R.id.transaction_hash_textview)
        val txConfTextView = findViewById<TextView>(R.id.transaction_conf_textview)
        val txAddressTextView = findViewById<TextView>(R.id.transaction_address_textview)
        val copyTxAddressImageButton =
            findViewById<ImageButton>(R.id.copy_txaddress_imagebutton)
        val txDateTextView = findViewById<TextView>(R.id.transaction_date_textview)
        val txAmountTextView = findViewById<TextView>(R.id.transaction_amount_textview)
        val blockHeightTextView = findViewById<TextView>(R.id.tx_block_height_textview)
        viewModel.transactionInfo.observe(this) { transactionInfo: TransactionInfo? ->
            if (transactionInfo == null) {
                return@observe
            }
            txHashTextView.text = transactionInfo.hash
            txConfTextView.text = "${transactionInfo.confirmations}"
            txDateTextView.text = getDateTime(transactionInfo.timestamp)
            if (transactionInfo.confirmations > 1) {
                confLabel2.text = getString(R.string.transaction_conf_desc2_confirmed)
                blockHeightTextView.text = "${transactionInfo.blockheight}"
                blockHeightTextView.visibility = View.VISIBLE
            } else if (transactionInfo.confirmations == 1L) {
                confLabel2.text = getString(R.string.transaction_conf_1_desc2_confirmed)
                blockHeightTextView.text = "${transactionInfo.blockheight}"
                blockHeightTextView.visibility = View.VISIBLE
            } else {
                blockHeightTextView.visibility = View.GONE
                confLabel2.text = getString(R.string.transaction_conf_desc2_unconfirmed)
            }
            val streetModeEnabled = PreferenceUtils.isStreetMode(applicationContext)
            val balanceString =
                if (streetModeEnabled) Constants.STREET_MODE_BALANCE else Helper.getDisplayAmount(
                    transactionInfo.amount,
                    12
                )
            if (transactionInfo.direction === TransactionInfo.Direction.Direction_In) {
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
            var destination: String? = "-"
            val wallet = walletService!!.getWalletOrThrow()
            if (transactionInfo.txKey == null) {
                transactionInfo.txKey = wallet.getTxKey(transactionInfo.hash)
            }
            if (transactionInfo.address == null && transactionInfo.direction === TransactionInfo.Direction.Direction_In) {
                destination = wallet.getSubaddress(
                    transactionInfo.accountIndex,
                    transactionInfo.addressIndex
                )
            } else if (transactionInfo.address != null && transactionInfo.direction === TransactionInfo.Direction.Direction_In) {
                destination = transactionInfo.address
            } else if (transactionInfo.transfers != null && transactionInfo.direction === TransactionInfo.Direction.Direction_Out) {
                if (transactionInfo.transfers?.size == 1) {
                    destination = transactionInfo.transfers?.get(0)?.address
                }
            }
            txAddressTextView.text = Objects.requireNonNullElse(destination, "-")
            if (destination == null) {
                copyTxAddressImageButton.visibility = View.INVISIBLE
            }
        }
    }

    private fun getDateTime(time: Long): String {
        return DateHelper.DATETIME_FORMATTER.format(Date(time * 1000))
    }

    override fun onWalletHistoryRefreshed(transactions: List<TransactionInfo>) {
        val newTransactionInfo = transactions.find { it.hash != null && it.hash == viewModel.txHash }
        viewModel.updateTransactionInfo(newTransactionInfo)
    }
}

internal class TransactionActivityViewModel : ViewModel() {
    var txHash: String? = null

    private val _transactionInfo: MutableLiveData<TransactionInfo?> = MutableLiveData()
    val transactionInfo: LiveData<TransactionInfo?> = _transactionInfo

    fun updateTransactionInfo(newTransactionInfo: TransactionInfo?) {
        _transactionInfo.postValue(newTransactionInfo)
    }
}