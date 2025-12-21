package org.monfluo.wallet.adapter

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.icu.text.DateFormat
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.R
import org.monfluo.wallet.model.MoneroUnit
import org.monfluo.wallet.model.TransactionInfo
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.TextHelper
import org.monfluo.wallet.util.Constants
import java.util.Date
import java.text.NumberFormat
import kotlin.math.max

private const val VIEW_TYPES: Int = 3

private const val VIEW_TYPE_HEADER: Int = 0
private const val VIEW_TYPE_TRANSFER: Int = 1
private const val VIEW_TYPE_FOOTER: Int = 2

class AdapterTransactionDetails : RecyclerView.Adapter<AdapterTransactionDetails.ViewHolder>() {

    private var transaction: TransactionInfo? = null
    private var streetMode: Boolean = false

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

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
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

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val transaction = this.transaction!!
        val transfersAmount = max(1, transaction.transfers.size)
        when (viewHolder) {
            is HeaderViewHolder -> {
                viewHolder.bind(transaction)
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

    sealed class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    class HeaderViewHolder(view: View) : ViewHolder(view) {

        private val transactionTypeTextView: TextView = itemView.findViewById(R.id.transaction_type_textview)

        fun bind(transaction: TransactionInfo) {
            val transactionTypeString = when (transaction.direction) {
                TransactionInfo.Direction.In -> itemView.resources.getString(R.string.recycler_view_item_transaction_details_transaction_type_incoming)
                TransactionInfo.Direction.Out -> itemView.resources.getString(R.string.recycler_view_item_transaction_details_transaction_type_outgoing)
            }
            transactionTypeTextView.text = transactionTypeString
            transactionTypeTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transaction type", transactionTypeString)
                return@setOnLongClickListener true
            }
        }
    }

    class TransferViewHolder(view: View) : ViewHolder(view) {

        private val amountLabelTextView: TextView = itemView.findViewById(R.id.amount_label_textview)
        private val amountTextView: TextView = itemView.findViewById(R.id.amount_textview)
        private val addressLabelTextView: TextView = itemView.findViewById(R.id.address_label_textview)
        private val addressTextView: TextView = itemView.findViewById(R.id.address_textview)

        fun bind(transaction: TransactionInfo, transferIndex: Int, single: Boolean, streetMode: Boolean) {
            val (amount, address) = when (transaction.direction) {
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

    class FooterViewHolder(view: View) : ViewHolder(view) {

        private val feeTextView: TextView = itemView.findViewById(R.id.fee_textview)
        private val transactionHashTextView: TextView = itemView.findViewById(R.id.transaction_hash_textview)
        private val transactionKeyLabelTextView: TextView = itemView.findViewById(R.id.transaction_key_label_textview)
        private val transactionKeyTextView: TextView = itemView.findViewById(R.id.transaction_key_textview)
        private val dateTextView: TextView = itemView.findViewById(R.id.date_textview)
        private val confirmationsTextView: TextView = itemView.findViewById(R.id.confirmations_textview)
        private val blockTextView: TextView = itemView.findViewById(R.id.block_textview)

        fun bind(transaction: TransactionInfo, streetMode: Boolean) {
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
            feeTextView.text = TextHelper.formatUnlockedBalance(itemView.context, transaction.fee, MoneroUnit.MONERO, streetMode, 0)
            feeTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transaction fee", TextHelper.getDisplayAmount(transaction.fee, MoneroUnit.MONERO, false))
                return@setOnLongClickListener true
            }
            val transactionHash = transaction.hash
            transactionHashTextView.text = transactionHash
            transactionHashTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transaction hash", transactionHash)
                return@setOnLongClickListener true
            }
            val date = DateFormat.getInstance().format(Date(transaction.timestamp * 1000))
            dateTextView.text = date
            dateTextView.setOnLongClickListener {
                Helper.clipBoardCopy(itemView.context, "transaction date", date)
                return@setOnLongClickListener true
            }
            val (confirmations, block) = if (transaction.confirmations == 0L) {
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
