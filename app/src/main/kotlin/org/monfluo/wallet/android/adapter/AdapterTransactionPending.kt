package org.monfluo.wallet.android.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.model.MoneroUnit
import org.monfluo.wallet.android.model.TransactionDestinationResolvedFrom
import org.monfluo.wallet.android.util.TextHelper
import org.monfluo.wallet.lib.data.TransactionView

private const val VIEW_TYPE_HEADER: Int = 0
private const val VIEW_TYPE_TRANSFER: Int = 1
private const val VIEW_TYPE_FOOTER: Int = 2

class AdapterTransactionPending(
    private var pendingTransaction: TransactionView? = null,
) : RecyclerView.Adapter<AdapterTransactionPending.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.recycler_view_item_transaction_pending_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_TRANSFER -> {
                val view = inflater.inflate(R.layout.recycler_view_item_transaction_pending_transfer, parent, false)
                TransferViewHolder(view)
            }
            VIEW_TYPE_FOOTER -> {
                val view = inflater.inflate(R.layout.recycler_view_item_transaction_pending_footer, parent, false)
                FooterViewHolder(view)
            }
            else -> {
                throw RuntimeException("Unknown view type")
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val transactionHasDescription = false // pendingTransaction?.description != null
        val itemCount = getItemCount()
        return if (transactionHasDescription && position == 0) {
            VIEW_TYPE_HEADER
        } else if (position == itemCount - 1) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_TRANSFER
        }
    }

    override fun getItemCount(): Int {
//        val transactionDescriptionCount = if (pendingTransaction?.description != null) 1 else 0
//        return when (val destination = pendingTransaction?.destination) {
//            null -> 0
//            is PendingTransactionDestination.Default -> destination.value.size + 1 + transactionDescriptionCount
//            is PendingTransactionDestination.Sweep -> 2 + transactionDescriptionCount
//        }
        if (pendingTransaction == null) {
            return 0
        }
        return 1 + (pendingTransaction?.nonChangeSpends?.size ?: 0)
    }

    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        position: Int,
    ) {
        val pendingTransaction = pendingTransaction!!
        val transactionDescriptionCount = 0 // if (pendingTransaction.description != null) 1 else 0
        when (viewHolder) {
            is HeaderViewHolder -> {
                TODO()
//                viewHolder.bind(pendingTransaction.description!!)
            }
            is TransferViewHolder -> {
                val adjustedPosition = position - transactionDescriptionCount
                val value = pendingTransaction.nonChangeSpends[adjustedPosition]
                val single = pendingTransaction.nonChangeSpends.size == 1
                viewHolder.bind(value.amount, value.address!!, null, adjustedPosition, single)
            }
            is FooterViewHolder -> {
                val amount = pendingTransaction.nonChangeSpendAmount
                val fee = pendingTransaction.fee
                val single = pendingTransaction.nonChangeSpends.size == 1
                viewHolder.bind(amount, fee, single)
            }
        }
    }

    fun setPendingTransaction(pendingTransaction: TransactionView) {
        this.pendingTransaction = pendingTransaction
        notifyDataSetChanged()
    }

    sealed class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view)

    inner class HeaderViewHolder(
        view: View,
    ) : ViewHolder(view) {
        private val transactionDescriptionTextView: TextView = itemView.findViewById(R.id.transaction_description_textview)

        fun bind(description: String) {
            transactionDescriptionTextView.setText(description)
        }
    }

    inner class TransferViewHolder(
        view: View,
    ) : ViewHolder(view) {
        private val amountLabelTextView: TextView = itemView.findViewById(R.id.amount_label_textview)
        private val amountTextView: TextView = itemView.findViewById(R.id.amount_textview)
        private val addressLabelTextView: TextView = itemView.findViewById(R.id.address_label_textview)
        private val addressTextView: TextView = itemView.findViewById(R.id.address_textview)
        private val addressResolvedFromLabelTextView: TextView = itemView.findViewById(R.id.address_resolved_from_label_textview)
        private val addressResolvedFromTextView: TextView = itemView.findViewById(R.id.address_resolved_from_textview)
        private val dnssecWarningIconImageView: ImageView = itemView.findViewById(R.id.dnssec_warning_icon_imageview)

        private val transactionDestinationPendingAmountLabel: CharSequence = itemView.resources.getString(R.string.recycler_view_item_transaction_pending_amount_label)
        private val transactionDestinationPendingAmountWithPositionLabel: (Int) -> CharSequence = { position: Int ->
            itemView.resources.getString(R.string.recycler_view_item_transaction_pending_amount_with_position_label, position)
        }
        private val transactionDestinationPendingAddressLabel: CharSequence = itemView.resources.getString(R.string.recycler_view_item_transaction_pending_address_label)
        private val transactionDestinationPendingAddressWithPositionLabel: (Int) -> CharSequence = { position: Int ->
            itemView.resources.getString(R.string.recycler_view_item_transaction_pending_address_with_position_label, position)
        }

        fun bind(
            amount: Long,
            address: String,
            resolvedFrom: TransactionDestinationResolvedFrom?,
            index: Int,
            single: Boolean,
        ) {
            if (single) {
                amountLabelTextView.setText(transactionDestinationPendingAmountLabel)
                addressLabelTextView.setText(transactionDestinationPendingAddressLabel)
            } else {
                amountLabelTextView.setText(transactionDestinationPendingAmountWithPositionLabel(index + 1))
                addressLabelTextView.setText(transactionDestinationPendingAddressWithPositionLabel(index + 1))
            }
            amountTextView.setText(TextHelper.formatUnlockedBalance(itemView.context, amount, MoneroUnit.MONERO, false, 0))
            addressTextView.setText(TextHelper.formatAddress(address))
            if (resolvedFrom != null) {
                addressResolvedFromLabelTextView.visibility = View.VISIBLE
                addressResolvedFromTextView.visibility = View.VISIBLE
                addressResolvedFromTextView.setText(resolvedFrom.value)
                if (resolvedFrom.dnssec) {
                    dnssecWarningIconImageView.visibility = View.GONE
                } else {
                    dnssecWarningIconImageView.visibility = View.VISIBLE
                }
            } else {
                addressResolvedFromLabelTextView.visibility = View.GONE
                addressResolvedFromTextView.visibility = View.GONE
                dnssecWarningIconImageView.visibility = View.GONE
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

        fun bind(
            totalAmount: Long,
            feeAmount: Long,
            single: Boolean,
        ) {
            if (single) {
                totalLabelTextView.visibility = View.GONE
                totalTextView.visibility = View.GONE
            } else {
                totalLabelTextView.visibility = View.VISIBLE
                totalTextView.visibility = View.VISIBLE
                totalTextView.setText(TextHelper.formatUnlockedBalance(itemView.context, totalAmount, MoneroUnit.MONERO, false, 0))
            }
            feeTextView.setText(TextHelper.formatUnlockedBalance(itemView.context, feeAmount, MoneroUnit.MONERO, false, 0))
            grandTotalTextView.setText(TextHelper.formatUnlockedBalance(itemView.context, totalAmount + feeAmount, MoneroUnit.MONERO, false, 0))
        }
    }
}
