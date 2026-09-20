package org.monfluo.wallet.android.adapter

import android.content.Context
import android.graphics.Typeface
import android.icu.text.DateFormat
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.model.MoneroUnit
import org.monfluo.wallet.android.model.TransactionInfo
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.Helper
import org.monfluo.wallet.android.util.SimpleTextWatcher
import org.monfluo.wallet.android.util.TextHelper
import java.text.NumberFormat
import java.util.Date
import kotlin.math.max

private const val VIEW_TYPES: Int = 3

private const val VIEW_TYPE_HEADER: Int = 0
private const val VIEW_TYPE_TRANSFER: Int = 1
private const val VIEW_TYPE_FOOTER: Int = 2

class AdapterTransactionDetails(
    private val listener: Listener,
) : RecyclerView.Adapter<AdapterTransactionDetails.ViewHolder>() {
    private var transaction: TransactionInfo? = null
    private var streetMode: Boolean = false
    private var transactionDescriptionText: String = ""
    private var editingTransactionDescription: Boolean = false

    fun submitTransaction(transaction: TransactionInfo?) {
        if (this.transaction != transaction) {
            this.transaction = transaction
            notifyDataSetChanged()
        }
    }

    fun submitStreetMode(newStreetMode: Boolean) {
        if (streetMode != newStreetMode) {
            streetMode = newStreetMode
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(
        viewGroup: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val inflater = LayoutInflater.from(viewGroup.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.recycler_view_item_transaction_details_header, viewGroup, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_TRANSFER -> {
                val view = inflater.inflate(R.layout.recycler_view_item_transaction_details_transfer, viewGroup, false)
                TransferViewHolder(view)
            }
            VIEW_TYPE_FOOTER -> {
                val view = inflater.inflate(R.layout.recycler_view_item_transaction_details_footer, viewGroup, false)
                FooterViewHolder(view)
            }
            else -> {
                throw RuntimeException("Unknown view type")
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val transfersAmount = max(1, transaction!!.transfers.size)
        if (position == 0) {
            return VIEW_TYPE_HEADER
        } else if (position <= transfersAmount) {
            return VIEW_TYPE_TRANSFER
        } else {
            return VIEW_TYPE_FOOTER
        }
    }

    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        position: Int,
    ) {
        val transaction = this.transaction!!
        val transfersAmount = max(1, transaction.transfers.size)
        when (viewHolder) {
            is HeaderViewHolder -> {
                viewHolder.bind(transaction, transactionDescriptionText, editingTransactionDescription)
            }
            is TransferViewHolder -> {
                val transferIndex = position - VIEW_TYPE_TRANSFER
                val single = transfersAmount == 1
                viewHolder.bind(transaction, transferIndex, single, streetMode)
            }
            is FooterViewHolder -> {
                viewHolder.bind(transaction, streetMode)
            }
        }
    }

    override fun getItemCount(): Int {
        val transaction = this.transaction
        if (transaction == null) {
            return 0
        } else {
            val transfersAmount = max(1, transaction.transfers.size)
            // header + transfers + footer
            return 2 + transfersAmount
        }
    }

    fun editTransactionDescription() {
        this.editingTransactionDescription = true
    }

    fun finishEditingTransactionDescription() {
        this.editingTransactionDescription = false
        listener.onTransactionDescriptionUpdated(transaction!!, transactionDescriptionText)
    }

    interface Listener {
        fun onTransactionDescriptionUpdated(
            transactionInfo: TransactionInfo,
            description: String,
        )
    }

    sealed class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view)

    inner class HeaderViewHolder(
        view: View,
    ) : ViewHolder(view) {
        private val transactionTypeTextView: TextView = itemView.findViewById(R.id.transaction_type_textview)
        private val transactionDescriptionTextView: TextView = itemView.findViewById(R.id.transaction_description_textview)
        private val transactionDescriptionEditText: EditText = itemView.findViewById(R.id.transaction_description_edittext)

        fun bind(
            transaction: TransactionInfo,
            transactionDescriptionText: String,
            editingTransactionDescription: Boolean,
        ) {
            val transactionTypeString =
                when (transaction.direction) {
                    TransactionInfo.Direction.In -> {
                        if (transaction.isCoinbase) {
                            itemView.resources.getString(R.string.recycler_view_item_transaction_details_transaction_type_coinbase)
                        } else {
                            itemView.resources.getString(R.string.recycler_view_item_transaction_details_transaction_type_incoming)
                        }
                    }
                    TransactionInfo.Direction.Out -> itemView.resources.getString(R.string.recycler_view_item_transaction_details_transaction_type_outgoing)
                }
            transactionTypeTextView.text = transactionTypeString
            transactionTypeTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transaction type", transactionTypeString)
                return@setOnLongClickListener true
            }
            if (editingTransactionDescription) {
                transactionDescriptionTextView.visibility = View.GONE
                transactionDescriptionEditText.visibility = View.VISIBLE
            } else {
                transactionDescriptionTextView.visibility = View.VISIBLE
                transactionDescriptionEditText.visibility = View.GONE
            }
            val description = transaction.description.takeIf { !it.isNullOrBlank() }
            val descriptionText = transaction.description ?: itemView.resources.getString(R.string.recycler_view_item_transaction_details_transaction_description_none)
            transactionDescriptionTextView.setText(descriptionText)
            transactionDescriptionTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transaction description", descriptionText)
                return@setOnLongClickListener true
            }
            if (description != null) {
                transactionDescriptionTextView.setTypeface(null, Typeface.NORMAL)
            } else {
                transactionDescriptionTextView.setTypeface(null, Typeface.ITALIC)
            }
            transactionDescriptionTextView.setOnClickListener {
                transactionDescriptionTextView.visibility = View.GONE
                transactionDescriptionEditText.visibility = View.VISIBLE
                transactionDescriptionEditText.requestFocus()
                transactionDescriptionEditText.setSelection(transactionDescriptionEditText.text.length)
                val imm = itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(transactionDescriptionEditText, InputMethodManager.SHOW_IMPLICIT)
                editTransactionDescription()
            }
            transactionDescriptionEditText.setText(transactionDescriptionText)
            transactionDescriptionEditText.addTextChangedListener(
                object : SimpleTextWatcher() {
                    override fun afterTextChanged(s: Editable) {
                        this@AdapterTransactionDetails.transactionDescriptionText = s.toString()
                    }
                },
            )
            transactionDescriptionEditText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    transactionDescriptionTextView.visibility = View.VISIBLE
                    transactionDescriptionEditText.visibility = View.GONE
                    finishEditingTransactionDescription()
                    return@setOnEditorActionListener true
                }
                return@setOnEditorActionListener false
            }
        }
    }

    inner class TransferViewHolder(
        view: View,
    ) : ViewHolder(view) {
        private val amountLabelTextView: TextView = itemView.findViewById(R.id.amount_label_textview)
        private val amountTextView: TextView = itemView.findViewById(R.id.amount_textview)
        private val addressLabelTextView: TextView = itemView.findViewById(R.id.address_label_textview)
        private val addressTextView: TextView = itemView.findViewById(R.id.address_textview)

        fun bind(
            transaction: TransactionInfo,
            transferIndex: Int,
            single: Boolean,
            streetMode: Boolean,
        ) {
            val (amount, address) =
                when (transaction.direction) {
                    TransactionInfo.Direction.In -> {
                        val amount = transaction.amount
                        val address = transaction.addressValue!!
                        amountTextView.text = TextHelper.formatUnlockedBalance(itemView.context, transaction.amount, MoneroUnit.MONERO, streetMode, 0, ContextCompat.getColor(itemView.context, R.color.oled_positiveColor))
                        addressTextView.text = TextHelper.formatAddress(address)
                        Pair(amount, address)
                    }
                    TransactionInfo.Direction.Out -> {
                        val amount = transaction.transfers.getOrNull(transferIndex)?.amount ?: transaction.amount
                        val address = transaction.transfers.getOrNull(transferIndex)?.let { it.address }
                        amountTextView.text = TextHelper.formatUnlockedBalance(itemView.context, amount, MoneroUnit.MONERO, streetMode, 0, ContextCompat.getColor(itemView.context, R.color.oled_negativeColor))
                        addressTextView.text = address?.let { TextHelper.formatAddress(it) } ?: itemView.resources.getString(R.string.recycler_view_item_transaction_details_address_unavailable)
                        Pair(amount, address)
                    }
                }
            if (single) {
                amountLabelTextView.text = itemView.resources.getString(R.string.recycler_view_item_transaction_details_amount_label)
                addressLabelTextView.text = itemView.resources.getString(R.string.recycler_view_item_transaction_details_address_label)
            } else {
                amountLabelTextView.text = itemView.resources.getString(R.string.recycler_view_item_transaction_details_amount_with_position_label, transferIndex + 1)
                addressLabelTextView.text = itemView.resources.getString(R.string.recycler_view_item_transaction_details_address_with_position_label, transferIndex + 1)
            }
            amountTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transfer amount", TextHelper.getDisplayAmount(amount, MoneroUnit.MONERO, false))
                return@setOnLongClickListener true
            }
            addressTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transfer address", address ?: itemView.resources.getString(R.string.recycler_view_item_transaction_details_address_unavailable))
                return@setOnLongClickListener true
            }
        }
    }

    inner class FooterViewHolder(
        view: View,
    ) : ViewHolder(view) {
        private val totalLabelTextView: TextView = itemView.findViewById(R.id.total_label_textview)
        private val totalTextView: TextView = itemView.findViewById(R.id.total_textview)
        private val feeTextView: TextView = itemView.findViewById(R.id.fee_textview)
        private val grandTotalTextView: TextView = itemView.findViewById(R.id.grand_total_textview)
        private val transactionHashTextView: TextView = itemView.findViewById(R.id.transaction_hash_textview)
        private val transactionKeyLabelTextView: TextView = itemView.findViewById(R.id.transaction_key_label_textview)
        private val transactionKeyTextView: TextView = itemView.findViewById(R.id.transaction_key_textview)
        private val dateTextView: TextView = itemView.findViewById(R.id.date_textview)
        private val confirmationsTextView: TextView = itemView.findViewById(R.id.confirmations_textview)
        private val blockTextView: TextView = itemView.findViewById(R.id.block_textview)

        fun bind(
            transaction: TransactionInfo,
            streetMode: Boolean,
        ) {
            val single = transaction.transfers.size <= 1
            when (transaction.direction) {
                TransactionInfo.Direction.In -> {
                    transactionKeyLabelTextView.visibility = View.GONE
                    transactionKeyTextView.visibility = View.GONE
                    transactionKeyTextView.setOnLongClickListener(null)
                }
                TransactionInfo.Direction.Out -> {
                    transactionKeyLabelTextView.visibility = View.VISIBLE
                    transactionKeyTextView.visibility = View.VISIBLE
                    val transactionKey = if (transaction.key.isNullOrBlank()) itemView.resources.getString(R.string.recycler_view_item_transaction_details_transaction_key_unavailable) else transaction.key
                    transactionKeyTextView.text = if (streetMode) "#".repeat(transactionKey.length) else transactionKey
                    transactionKeyTextView.setOnLongClickListener {
                        Helper.clipBoardCopy(itemView.context, "transaction key", transactionKey, true)
                        return@setOnLongClickListener true
                    }
                }
            }
            val positiveColor = ContextCompat.getColor(itemView.context, R.color.oled_positiveColor)
            val negativeColor = ContextCompat.getColor(itemView.context, R.color.oled_negativeColor)
            val (totalColor, feeColor, grandTotalColor) =
                when (transaction.direction) {
                    TransactionInfo.Direction.In -> {
                        Triple(positiveColor, null, null)
                    }
                    TransactionInfo.Direction.Out -> {
                        Triple(negativeColor, negativeColor, negativeColor)
                    }
                }
            if (single) {
                totalLabelTextView.visibility = View.GONE
                totalTextView.visibility = View.GONE
                totalTextView.setOnLongClickListener(null)
            } else {
                totalLabelTextView.visibility = View.VISIBLE
                totalTextView.visibility = View.VISIBLE
                totalTextView.text = TextHelper.formatUnlockedBalance(itemView.context, transaction.amount, MoneroUnit.MONERO, streetMode, 0, totalColor)
                totalTextView.setOnLongClickListener {
                    Helper.clipBoardCopy(itemView.context, "transaction total amount", TextHelper.getDisplayAmount(transaction.amount, MoneroUnit.MONERO, false))
                    return@setOnLongClickListener true
                }
            }
            feeTextView.text = TextHelper.formatUnlockedBalance(itemView.context, transaction.fee, MoneroUnit.MONERO, streetMode, 0, feeColor)
            feeTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transaction fee", TextHelper.getDisplayAmount(transaction.fee, MoneroUnit.MONERO, false))
                return@setOnLongClickListener true
            }
            val grandTotal = transaction.amount + transaction.fee
            grandTotalTextView.text = TextHelper.formatUnlockedBalance(itemView.context, grandTotal, MoneroUnit.MONERO, streetMode, 0, grandTotalColor)
            grandTotalTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transaction grand total amount", TextHelper.getDisplayAmount(grandTotal, MoneroUnit.MONERO, false))
                return@setOnLongClickListener true
            }
            val transactionHash = transaction.hash
            transactionHashTextView.text = transactionHash
            transactionHashTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transaction hash", transactionHash)
                return@setOnLongClickListener true
            }
            val date = DateFormat.getInstance().format(Date(transaction.timestamp))
            dateTextView.text = date
            dateTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transaction date", date)
                return@setOnLongClickListener true
            }
            val (confirmations, block) =
                if (transaction.confirmations == 0L) {
                    val confirmations = itemView.resources.getString(R.string.recycler_view_item_transaction_details_confirmations_unconfirmed)
                    confirmationsTextView.text = confirmations
                    confirmationsTextView.setTypeface(null, Typeface.ITALIC)
                    val block = itemView.resources.getString(R.string.recycler_view_item_transaction_details_block_unconfirmed)
                    blockTextView.text = block
                    blockTextView.setTypeface(null, Typeface.ITALIC)
                    Pair(confirmations, block)
                } else {
                    val confirmations = transaction.confirmations
                    confirmationsTextView.text = itemView.resources.getString(R.string.recycler_view_item_transaction_details_confirmations, NumberFormat.getInstance().format(confirmations))
                    if (transaction.confirmations >= Constants.CONFIRMATIONS) {
                        confirmationsTextView.setTypeface(null, Typeface.NORMAL)
                    } else {
                        confirmationsTextView.setTypeface(null, Typeface.ITALIC)
                    }
                    val block = transaction.blockheight
                    blockTextView.text = itemView.resources.getString(R.string.recycler_view_item_transaction_details_block, NumberFormat.getInstance().format(transaction.blockheight))
                    blockTextView.setTypeface(null, Typeface.NORMAL)
                    Pair(confirmations, block)
                }
            confirmationsTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transaction confirmations", confirmations.toString())
                return@setOnLongClickListener true
            }
            blockTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transaction block height", block.toString())
                return@setOnLongClickListener true
            }
        }
    }
}
