package org.monfluo.wallet.adapter

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.icu.text.DateFormat
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
import org.monfluo.wallet.util.TextHelper
import java.util.Date

private const val VIEW_TYPE_ITEM: Int = 0

// empty view at the end
private const val VIEW_TYPE_VOID: Int = 2

class AdapterTransactionInfo(
    private val listener: Listener,
) : RecyclerView.Adapter<AdapterTransactionInfo.ViewHolder>() {
    private var transactions: List<TransactionInfo> = emptyList()
    private var streetMode: Boolean = false
    private var voidHeight: Int = 0

    fun submitList(transactions: List<TransactionInfo>) {
        if (this.transactions != transactions) {
            this.transactions = transactions
            notifyDataSetChanged()
        }
    }

    fun submitStreetMode(newStreetMode: Boolean) {
        if (streetMode != newStreetMode) {
            streetMode = newStreetMode
            notifyDataSetChanged()
        }
    }

    fun setVoidHeight(voidHeight: Int) {
        this.voidHeight = voidHeight
        notifyItemChanged(transactions.size)
    }

    override fun onCreateViewHolder(
        viewGroup: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val inflater = LayoutInflater.from(viewGroup.context)
        return when (viewType) {
            VIEW_TYPE_ITEM -> {
                val view = inflater.inflate(R.layout.recycler_view_item_transaction, viewGroup, false)
                ItemViewHolder(listener, view)
            }
            VIEW_TYPE_VOID -> {
                val view = LinearLayoutCompat(viewGroup.context)
                view.orientation = LinearLayoutCompat.HORIZONTAL
                VoidViewHolder(view)
            }
            else -> {
                throw RuntimeException("Unknown view type")
            }
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (position < transactions.size) {
            VIEW_TYPE_ITEM
        } else {
            VIEW_TYPE_VOID
        }

    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        position: Int,
    ) {
        when (viewHolder) {
            is ItemViewHolder -> {
                val transaction = transactions[position]
                viewHolder.bind(transaction, streetMode)
            }
            is VoidViewHolder -> {
                viewHolder.itemView.layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        voidHeight,
                    )
            }
        }
    }

    override fun getItemCount(): Int {
        // we have an empty view at the end
        return transactions.size + 1
    }

    interface Listener {
        fun onClickTransaction(transactionInfo: TransactionInfo)
    }

    sealed class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view)

    class ItemViewHolder(
        val listener: Listener,
        view: View,
    ) : ViewHolder(view) {
        private val sentOrReceivedIconImageView: ImageView = itemView.findViewById(R.id.send_or_receive_icon_image_view)
        private val sentOrReceivedLabelTextView: TextView = itemView.findViewById(R.id.sent_or_received_label_text_view)
        private val amountTextView: TextView = itemView.findViewById(R.id.transaction_amount_text_view)
        private val confirmationsTextView: TextView = itemView.findViewById(R.id.transaction_confirmations_text_view)
        private val txDateTimeTextView: TextView = itemView.findViewById(R.id.transaction_date_text_view)

        private val receiveIconDrawable: Drawable = AppCompatResources.getDrawable(itemView.context, R.drawable.icon_receive)!!
        private val sendIconDrawable: Drawable = AppCompatResources.getDrawable(itemView.context, R.drawable.icon_send)!!
        private val transactionTypeReceivedText: CharSequence = itemView.resources.getText(R.string.transaction_type_received)
        private val transactionTypeMinedText: CharSequence = itemView.resources.getText(R.string.transaction_type_mined)
        private val transactionTypeSentText: CharSequence = itemView.resources.getText(R.string.transaction_type_sent)
        private val receivedAmountColor: Int = itemView.resources.getColor(R.color.oled_positiveColor)
        private val sentAmountColor: Int = itemView.resources.getColor(R.color.oled_negativeColor)
        private val transactionStatusFailedText: CharSequence = itemView.resources.getText(R.string.tx_list_failed_text)
        private val transactionStatusUnconfirmedText: CharSequence = itemView.resources.getText(R.string.transaction_unconfirmed)
        private val transactionStatusConfirmedText: CharSequence = itemView.resources.getText(R.string.transaction_confirmed)

        fun bind(
            transactionInfo: TransactionInfo,
            streetMode: Boolean,
        ) {
            when (transactionInfo.direction) {
                TransactionInfo.Direction.In -> {
                    sentOrReceivedIconImageView.setImageDrawable(receiveIconDrawable)
                    if (transactionInfo.isCoinbase) {
                        sentOrReceivedLabelTextView.text = transactionTypeMinedText
                    } else {
                        sentOrReceivedLabelTextView.text = transactionTypeReceivedText
                    }
                    amountTextView.setTextColor(receivedAmountColor)
                }
                TransactionInfo.Direction.Out -> {
                    sentOrReceivedIconImageView.setImageDrawable(sendIconDrawable)
                    sentOrReceivedLabelTextView.text = transactionTypeSentText
                    amountTextView.setTextColor(sentAmountColor)
                }
            }

            if (transactionInfo.isFailed) {
                confirmationsTextView.text = transactionStatusFailedText
                confirmationsTextView.setTypeface(null, Typeface.BOLD)
            } else if (transactionInfo.isPending) {
                confirmationsTextView.text = transactionStatusUnconfirmedText
                confirmationsTextView.setTypeface(null, Typeface.ITALIC)
            } else if (!transactionInfo.isConfirmed) {
                confirmationsTextView.text = itemView.resources.getString(R.string.transaction_confirming, transactionInfo.confirmations.toString())
                confirmationsTextView.setTypeface(null, Typeface.ITALIC)
            } else {
                confirmationsTextView.text = transactionStatusConfirmedText
                confirmationsTextView.setTypeface(null, Typeface.NORMAL)
            }

            amountTextView.text = TextHelper.formatUnlockedBalance(itemView.context, transactionInfo.amount, MoneroUnit.MONERO, streetMode, Typeface.NORMAL)
            val formattedDateTime = DateFormat.getInstance().format(Date(transactionInfo.timestamp * 1000))
            txDateTimeTextView.text = if (streetMode) "#".repeat(formattedDateTime.length) else formattedDateTime
            itemView.setOnClickListener { _ -> listener.onClickTransaction(transactionInfo) }
        }
    }

    class VoidViewHolder(
        view: View,
    ) : ViewHolder(view)
}
