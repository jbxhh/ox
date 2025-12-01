package org.monfluo.wallet.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.R
import org.monfluo.wallet.model.MoneroUnit
import org.monfluo.wallet.model.PendingTransaction
import org.monfluo.wallet.model.PendingTransactionDestination
import org.monfluo.wallet.model.TransactionDestinationResolvedFrom
import org.monfluo.wallet.util.TextHelper

private const val VIEW_TYPE_TRANSACTION_DESTINATION: Int = 0
private const val VIEW_TYPE_TRANSACTION_SUMMARY: Int = 1

class AdapterTransactionDestinationPending(
    private var pendingTransaction: PendingTransaction? = null
) : RecyclerView.Adapter<AdapterTransactionDestinationPending.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TRANSACTION_DESTINATION -> {
                val view = inflater.inflate(R.layout.recycler_view_item_transaction_destination_pending, parent, false)
                ViewHolderTransactionDestination(view)
            }
            VIEW_TYPE_TRANSACTION_SUMMARY -> {
                val view = inflater.inflate(R.layout.recycler_view_item_transaction_destination_pending_summary, parent, false)
                ViewHolderTransactionDestails(view)
            }
            else -> {
                throw RuntimeException("Unknown view type")
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val transactionDestinations = when (val destination = pendingTransaction!!.destination) {
            is PendingTransactionDestination.Default -> destination.value.size
            is PendingTransactionDestination.Sweep -> 1
        }
        return if (position < transactionDestinations) {
            VIEW_TYPE_TRANSACTION_DESTINATION
        } else {
            VIEW_TYPE_TRANSACTION_SUMMARY
        }
    }

    override fun getItemCount(): Int {
        return when (val destination = pendingTransaction?.destination) {
            null -> 0
            is PendingTransactionDestination.Default -> destination.value.size + 1
            is PendingTransactionDestination.Sweep -> 2
        }
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val pendingTransaction = pendingTransaction!!
        when (viewHolder) {
            is ViewHolderTransactionDestination -> {
                when (val destination = pendingTransaction.destination) {
                    is PendingTransactionDestination.Default -> {
                        val value = destination.value[position]
                        val single = destination.value.size == 1
                        viewHolder.bind(value.amount, value.address, value.resolvedFrom, position, single)
                    }
                    is PendingTransactionDestination.Sweep -> {
                        viewHolder.bind(pendingTransaction.getAmount(), destination.value.address, destination.value.resolvedFrom, position, true)
                    }
                }
            }
            is ViewHolderTransactionDestails -> {
                val amount = pendingTransaction.getAmount()
                val fee = pendingTransaction.getFee()
                val dust = pendingTransaction.getDust()
                viewHolder.bind(fee, dust, amount + fee)
            }
        }
    }

    fun setPendingTransaction(pendingTransaction: PendingTransaction) {
        this.pendingTransaction = pendingTransaction
        notifyDataSetChanged()
    }

    sealed class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class ViewHolderTransactionDestination(view: View) : ViewHolder(view) {

        private val amountLabelTextView: TextView = itemView.findViewById(R.id.amount_label_textview)
        private val amountTextView: TextView = itemView.findViewById(R.id.amount_textview)
        private val addressLabelTextView: TextView = itemView.findViewById(R.id.address_label_textview)
        private val addressTextView: TextView = itemView.findViewById(R.id.address_textview)
        private val addressResolvedFromLabelTextView: TextView = itemView.findViewById(R.id.address_resolved_from_label_textview)
        private val addressResolvedFromTextView: TextView = itemView.findViewById(R.id.address_resolved_from_textview)

        private val transactionDestinationPendingAmountLabel: CharSequence = itemView.resources.getString(R.string.recycler_view_item_transaction_destination_pending_amount_label)
        private val transactionDestinationPendingAmountWithPositionLabel: (Int) -> CharSequence = { position: Int ->
            itemView.resources.getString(R.string.recycler_view_item_transaction_destination_pending_amount_with_position_label, position)
        }
        private val transactionDestinationPendingAddressLabel: CharSequence = itemView.resources.getString(R.string.recycler_view_item_transaction_destination_pending_address_label)
        private val transactionDestinationPendingAddressWithPositionLabel: (Int) -> CharSequence = { position: Int ->
            itemView.resources.getString(R.string.recycler_view_item_transaction_destination_pending_address_with_position_label, position)
        }

        fun bind(
            amount: Long,
            address: String,
            resolvedFrom: TransactionDestinationResolvedFrom?,
            index: Int,
            single: Boolean
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
            } else {
                addressResolvedFromLabelTextView.visibility = View.GONE
                addressResolvedFromTextView.visibility = View.GONE
            }
        }
    }

    inner class ViewHolderTransactionDestails(view: View) : ViewHolder(view) {

        private val feeTextView: TextView = itemView.findViewById(R.id.fee_textview)
        private val dustLabelTextView: TextView = itemView.findViewById(R.id.dust_label_textview)
        private val dustTextView: TextView = itemView.findViewById(R.id.dust_textview)
        private val totalTextView: TextView = itemView.findViewById(R.id.total_textview)

        fun bind(feeAmount: Long, dustAmount: Long, totalAmount: Long) {
            feeTextView.setText(TextHelper.formatUnlockedBalance(itemView.context, feeAmount, MoneroUnit.MONERO, false, 0))
            if (dustAmount > 0) {
                dustLabelTextView.visibility = View.VISIBLE
                dustTextView.visibility = View.VISIBLE
                dustTextView.setText(TextHelper.formatUnlockedBalance(itemView.context, dustAmount, MoneroUnit.MONERO, false, 0))
            } else {
                dustLabelTextView.visibility = View.GONE
                dustTextView.visibility = View.GONE
            }
            totalTextView.setText(TextHelper.formatUnlockedBalance(itemView.context, totalAmount, MoneroUnit.MONERO, false, 0))
        }
    }
}
