package net.mynero.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import net.mynero.wallet.model.TransactionInfo
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.HistoryService
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.DateHelper
import net.mynero.wallet.util.Helper
import java.util.Calendar
import java.util.Date
import java.util.Objects

class TransactionActivity : AppCompatActivity() {
    private var transactionInfo: TransactionInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction)

        val cal = Calendar.getInstance()
        val tz = cal.timeZone //get the local time zone.
        DateHelper.DATETIME_FORMATTER.timeZone = tz

        transactionInfo = intent.extras?.getParcelable(Constants.NAV_ARG_TXINFO)

        bindObservers()
        bindListeners()
    }

    private fun bindListeners() {
        val copyTxHashImageButton = findViewById<ImageButton>(R.id.copy_txhash_imagebutton)
        copyTxHashImageButton.setOnClickListener {
            val txInfo = transactionInfo
            if (txInfo != null) {
                Helper.clipBoardCopy(this, "transaction_hash", txInfo.hash)
            }
        }
        val copyTxAddressImageButton =
            findViewById<ImageButton>(R.id.copy_txaddress_imagebutton)
        val addressTextView = findViewById<TextView>(R.id.transaction_address_textview)
        copyTxAddressImageButton.setOnClickListener {
            val txInfo = transactionInfo
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
        HistoryService.instance?.history?.observe(this) { transactionInfos: List<TransactionInfo> ->
            val newTransactionInfo = findNewestVersionOfTransaction(
                transactionInfo, transactionInfos
            )
                ?: return@observe
            txHashTextView.text = newTransactionInfo.hash
            txConfTextView.text = "${newTransactionInfo.confirmations}"
            txDateTextView.text = getDateTime(newTransactionInfo.timestamp)
            if (newTransactionInfo.confirmations > 1) {
                confLabel2.text = getString(R.string.transaction_conf_desc2_confirmed)
                blockHeightTextView.text = "${newTransactionInfo.blockheight}"
                blockHeightTextView.visibility = View.VISIBLE
            } else if (newTransactionInfo.confirmations == 1L) {
                confLabel2.text = getString(R.string.transaction_conf_1_desc2_confirmed)
                blockHeightTextView.text = "${newTransactionInfo.blockheight}"
                blockHeightTextView.visibility = View.VISIBLE
            } else {
                blockHeightTextView.visibility = View.GONE
                confLabel2.text = getString(R.string.transaction_conf_desc2_unconfirmed)
            }
            val streetModeEnabled =
                PrefService.instance?.getBoolean(Constants.PREF_STREET_MODE, false) == true
            val balanceString =
                if (streetModeEnabled) Constants.STREET_MODE_BALANCE else Helper.getDisplayAmount(
                    newTransactionInfo.amount,
                    12
                )
            if (newTransactionInfo.direction === TransactionInfo.Direction.Direction_In) {
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
            val wallet = WalletManager.instance?.wallet
            if (newTransactionInfo.txKey == null) {
                newTransactionInfo.txKey = wallet?.getTxKey(newTransactionInfo.hash)
            }
            if (newTransactionInfo.address == null && newTransactionInfo.direction === TransactionInfo.Direction.Direction_In) {
                destination = wallet?.getSubaddress(
                    newTransactionInfo.accountIndex,
                    newTransactionInfo.addressIndex
                )
            } else if (newTransactionInfo.address != null && newTransactionInfo.direction === TransactionInfo.Direction.Direction_In) {
                destination = newTransactionInfo.address
            } else if (newTransactionInfo.transfers != null && newTransactionInfo.direction === TransactionInfo.Direction.Direction_Out) {
                if (newTransactionInfo.transfers?.size == 1) {
                    destination = newTransactionInfo.transfers?.get(0)?.address
                }
            }
            txAddressTextView.text = Objects.requireNonNullElse(destination, "-")
            if (destination == null) {
                copyTxAddressImageButton.visibility = View.INVISIBLE
            }
        }
    }

    private fun findNewestVersionOfTransaction(
        oldTransactionInfo: TransactionInfo?,
        transactionInfoList: List<TransactionInfo>
    ): TransactionInfo? {
        for (transactionInfo in transactionInfoList) {
            if (transactionInfo.hash == oldTransactionInfo?.hash) {
                this.transactionInfo = transactionInfo
                return this.transactionInfo
            }
        }
        return null
    }

    private fun getDateTime(time: Long): String {
        return DateHelper.DATETIME_FORMATTER.format(Date(time * 1000))
    }
}