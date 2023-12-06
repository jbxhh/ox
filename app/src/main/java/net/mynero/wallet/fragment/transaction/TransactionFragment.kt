package net.mynero.wallet.fragment.transaction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import net.mynero.wallet.R
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

class TransactionFragment : Fragment() {
    private var mViewModel: TransactionViewModel? = null
    private var transactionInfo: TransactionInfo? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_transaction, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val cal = Calendar.getInstance()
        val tz = cal.timeZone //get the local time zone.
        DateHelper.DATETIME_FORMATTER.timeZone = tz
        mViewModel = ViewModelProvider(this)[TransactionViewModel::class.java]
        val args = arguments
        if (args != null) {
            transactionInfo = arguments?.getParcelable(Constants.NAV_ARG_TXINFO)
        }
        bindObservers(view)
        bindListeners(view)
    }

    private fun bindListeners(view: View) {
        val copyTxHashImageButton = view.findViewById<ImageButton>(R.id.copy_txhash_imagebutton)
        copyTxHashImageButton.setOnClickListener {
            val txInfo = transactionInfo
            if (txInfo != null) {
                Helper.clipBoardCopy(context, "transaction_hash", txInfo.hash)
            }
        }
        val copyTxAddressImageButton =
            view.findViewById<ImageButton>(R.id.copy_txaddress_imagebutton)
        val addressTextView = view.findViewById<TextView>(R.id.transaction_address_textview)
        copyTxAddressImageButton.setOnClickListener {
            val txInfo = transactionInfo
            if (txInfo != null) {
                val destination = addressTextView.text.toString()
                Helper.clipBoardCopy(context, "transaction_address", destination)
            }
        }
    }

    private fun bindObservers(view: View) {
        val txActionTextView = view.findViewById<TextView>(R.id.transaction_action_textview)
        val confLabel2 = view.findViewById<TextView>(R.id.transaction_conf_label2_textview)
        val txHashTextView = view.findViewById<TextView>(R.id.transaction_hash_textview)
        val txConfTextView = view.findViewById<TextView>(R.id.transaction_conf_textview)
        val txAddressTextView = view.findViewById<TextView>(R.id.transaction_address_textview)
        val copyTxAddressImageButton =
            view.findViewById<ImageButton>(R.id.copy_txaddress_imagebutton)
        val txDateTextView = view.findViewById<TextView>(R.id.transaction_date_textview)
        val txAmountTextView = view.findViewById<TextView>(R.id.transaction_amount_textview)
        val blockHeightTextView = view.findViewById<TextView>(R.id.tx_block_height_textview)
        HistoryService.instance?.history?.observe(viewLifecycleOwner) { transactionInfos: List<TransactionInfo> ->
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
            val ctx = context
            if (ctx != null) {
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
                            ctx,
                            R.color.oled_positiveColor
                        )
                    )
                } else {
                    txActionTextView.text = getString(R.string.transaction_action_sent)
                    txAmountTextView.setTextColor(
                        ContextCompat.getColor(
                            ctx,
                            R.color.oled_negativeColor
                        )
                    )
                }
                txAmountTextView.text = balanceString
            }
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