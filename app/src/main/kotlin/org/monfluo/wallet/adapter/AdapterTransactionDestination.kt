package org.monfluo.wallet.adapter

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.icu.text.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.text.Editable
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import org.monfluo.wallet.R
import org.monfluo.wallet.model.MoneroUnit
import org.monfluo.wallet.model.TransactionDestinationRaw
import org.monfluo.wallet.model.PendingTransactionDestination
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.Helper.replaceElementWithList
import org.monfluo.wallet.util.TextHelper
import org.monfluo.wallet.util.SimpleTextWatcher
import java.util.Date
import timber.log.Timber

class AdapterTransactionDestination(
    val listener: Listener
) : RecyclerView.Adapter<AdapterTransactionDestination.ViewHolder>() {

    private var transactionDestinations: List<TransactionDestinationRaw> = listOf(TransactionDestinationRaw())
    private var sendAll: MutableLiveData<Boolean> = MutableLiveData(false)
    private var enabled: Boolean = true

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.recycler_view_item_transaction_destination, viewGroup, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val transactionDestination = transactionDestinations[position]
        val sendAll = if (this.sendAll.value!!) SendAll.SELECTED else if (transactionDestinations.size > 1) SendAll.UNAVAILABLE else SendAll.UNSELECTED
        val listener = ViewHolderListenerImpl(position)
        viewHolder.bind(transactionDestination, enabled, sendAll, position, listener)
    }

    override fun getItemCount(): Int {
        return transactionDestinations.size
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled != enabled) {
            this.enabled = enabled
            notifyDataSetChanged()
        }
    }

    fun addTransactionDestination() {
        transactionDestinations = transactionDestinations + TransactionDestinationRaw()
        sendAll.value = false
        notifyDataSetChanged()
    }

    fun getTransactionDestinations(): List<TransactionDestinationRaw> = transactionDestinations

    fun getSendAll(): LiveData<Boolean> = sendAll

    fun replaceElementWithList(transactionDestinations: List<TransactionDestinationRaw>, index: Int) {
        if (transactionDestinations.isEmpty()) {
            Timber.w("Attempted to fill with empty transaction destinations list")
            return
        }
        if (index > this.transactionDestinations.size) {
            Timber.w("Attempted to fill at index ${index} but the length of existing transaction destination is ${this.transactionDestinations.size}")
            return
        }
        this.transactionDestinations = this.transactionDestinations.replaceElementWithList(index, transactionDestinations)
        notifyDataSetChanged()
    }

    fun restore(transactionDestinations: List<TransactionDestinationRaw>, sendAll: Boolean) {
        this.sendAll.value = sendAll
        if (transactionDestinations.isEmpty()) {
            this.transactionDestinations = listOf(TransactionDestinationRaw())
        } else if (sendAll) {
            this.transactionDestinations = listOf(transactionDestinations[0])
        } else {
            this.transactionDestinations = transactionDestinations
        }
        notifyDataSetChanged()
    }

    interface Listener {

        fun onScanRequested(position: Int)
    }

    interface ViewHolderListener {

        fun onSendAllClicked()
        fun onRemoveClicked()
        fun onAmountChanged(amount: String)
        fun onAddressChanged(address: String)
        fun onScanRequested()
    }

    inner class ViewHolderListenerImpl(val position: Int) : ViewHolderListener {

        override fun onSendAllClicked() {
            if (this@AdapterTransactionDestination.sendAll.value!!) {
                this@AdapterTransactionDestination.sendAll.value = false
            } else {
                transactionDestinations = listOf(transactionDestinations.getOrNull(position) ?: TransactionDestinationRaw())
                this@AdapterTransactionDestination.sendAll.value = true
            }
            notifyDataSetChanged()
        }

        override fun onRemoveClicked() {
            transactionDestinations = transactionDestinations.filterIndexed { i, _ -> i != position }
            notifyDataSetChanged()
        }

        override fun onAmountChanged(amount: String) {
            transactionDestinations.getOrNull(position)?.amount = amount
        }

        override fun onAddressChanged(address: String) {
            transactionDestinations.getOrNull(position)?.value = address
        }

        override fun onScanRequested() {
            this@AdapterTransactionDestination.listener.onScanRequested(position)
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val transactionDestinationRemoveImageButton: ImageButton = itemView.findViewById(R.id.transaction_destination_remove_image_button)
        private val transactionDestinationScanImageButton: ImageButton = itemView.findViewById(R.id.transaction_destination_scan_image_button)
        private val transactionDestinationAmountLabelTextView: TextView = itemView.findViewById(R.id.transaction_destination_amount_label_textview);
        private val transactionDestinationAmountEditText: EditText = itemView.findViewById(R.id.transaction_destination_amount_edittext)
        private val transactionDestinationAddressLabelTextView: TextView = itemView.findViewById(R.id.transaction_destination_address_label_textview);
        private val transactionDestinationAddressEditText: EditText = itemView.findViewById(R.id.transaction_destination_address_edittext)
        private val transactionDestinationAmountSendAllButton: Button = itemView.findViewById(R.id.transaction_destination_amount_send_all_button)
        private val transactionDestinationDonateLabelTextView: TextView = itemView.findViewById(R.id.transaction_destination_donate_label_text_view)

        private val transactionDestinationRemoveIcon: Drawable = AppCompatResources.getDrawable(itemView.context, android.R.drawable.ic_delete)!!
        private val transactionDestinationScanIcon: Drawable = AppCompatResources.getDrawable(itemView.context, R.drawable.ic_scan)!!
        private val transactionDestinationAmountLabel: CharSequence = itemView.resources.getString(R.string.recycler_view_item_transaction_destination_amount_label)
        private val transactionDestinationAmountWithPositionLabel: (Int) -> CharSequence = { position: Int ->
            itemView.resources.getString(R.string.recycler_view_item_transaction_destination_amount_with_position_label, position)
        }
        private val transactionDestinationAddressLabel: CharSequence = itemView.resources.getString(R.string.recycler_view_item_transaction_destination_address_label)
        private val transactionDestinationAddressWithPositionLabel: (Int) -> CharSequence = { position: Int ->
            itemView.resources.getString(R.string.recycler_view_item_transaction_destination_address_with_position_label, position)
        }
        private val transactionDestinationSendAllLabel: CharSequence = itemView.resources.getString(R.string.recycler_view_item_transaction_destination_send_all_label)
        private val transactionDestinationSendAllUndoLabel: CharSequence = itemView.resources.getString(R.string.recycler_view_item_transaction_destination_send_all_undo_label)
        private val transactionDestinationSendingAllText: CharSequence = itemView.resources.getString(R.string.recycler_view_item_transaction_destination_sending_all_text)
        private val transactionDestinationDonateLabel: CharSequence = itemView.resources.getString(R.string.recycler_view_item_transaction_destination_donate_label)
        private val transactionDestinationDonatingLabel: CharSequence = itemView.resources.getString(R.string.recycler_view_item_transaction_destination_donating_label)

        fun bind(
            transactionDestination: TransactionDestinationRaw,
            enabled: Boolean,
            sendAll: SendAll,
            index: Int,
            listener: ViewHolderListener
        ) {
            val single = when (sendAll) {
                SendAll.UNSELECTED -> true
                SendAll.SELECTED -> true
                SendAll.UNAVAILABLE -> false
            }
            when (sendAll) {
                SendAll.UNSELECTED -> {
                    transactionDestinationAmountSendAllButton.setText(transactionDestinationSendAllLabel)
                    transactionDestinationAmountSendAllButton.setEnabled(enabled)
                    transactionDestinationAmountSendAllButton.setOnClickListener {
                        listener.onSendAllClicked()
                    }
                }
                SendAll.SELECTED -> {
                    transactionDestinationAmountSendAllButton.setText(transactionDestinationSendAllUndoLabel)
                    transactionDestinationAmountSendAllButton.setEnabled(enabled)
                    transactionDestinationAmountSendAllButton.setOnClickListener {
                        listener.onSendAllClicked()
                    }
                }
                SendAll.UNAVAILABLE -> {
                    transactionDestinationAmountSendAllButton.setText(transactionDestinationSendAllLabel)
                    transactionDestinationAmountSendAllButton.setEnabled(false)
                    transactionDestinationAmountSendAllButton.setOnClickListener(null)
                }
            }
            if (single) {
                transactionDestinationRemoveImageButton.setImageDrawable(getTintedRemoveTransactionDestinationDrawable(false))
                transactionDestinationRemoveImageButton.setOnClickListener(null)
                transactionDestinationAmountLabelTextView.setText(transactionDestinationAmountLabel)
                transactionDestinationAddressLabelTextView.setText(transactionDestinationAddressLabel)
            } else {
                if (enabled) {
                    transactionDestinationRemoveImageButton.setImageDrawable(getTintedRemoveTransactionDestinationDrawable(true))
                    transactionDestinationRemoveImageButton.setOnClickListener {
                        listener.onRemoveClicked()
                    }
                }
                transactionDestinationAmountLabelTextView.setText(transactionDestinationAmountWithPositionLabel(index + 1))
                transactionDestinationAddressLabelTextView.setText(transactionDestinationAddressWithPositionLabel(index + 1))
            }
            if (sendAll == SendAll.SELECTED) {
                transactionDestinationAmountEditText.setText(transactionDestinationSendingAllText)
                transactionDestinationAmountEditText.setFocusable(false)
            } else {
                transactionDestinationAmountEditText.setText(transactionDestination.amount)
                transactionDestinationAmountEditText.setFocusableInTouchMode(enabled)
            }
            transactionDestinationAmountEditText.addTextChangedListener(object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable) {
                    val text = s.toString()
                    if (text != transactionDestinationSendingAllText) {
                        listener.onAmountChanged(s.toString())
                    }
                }
            })
            transactionDestinationAddressEditText.setText(transactionDestination.value)
            transactionDestinationAddressEditText.setFocusableInTouchMode(enabled)
            transactionDestinationAddressEditText.addTextChangedListener(object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable) {
                    listener.onAddressChanged(s.toString())
                    updateDonateLabel(isDonatingAddress(s.toString()))
                }
            })
            if (enabled) {
                transactionDestinationDonateLabelTextView.setOnClickListener {
                    val isDonatingAddress = isDonatingAddress(transactionDestinationAddressEditText.text.toString())
                    if (isDonatingAddress) {
                        transactionDestinationAddressEditText.setText("")
                        updateDonateLabel(false)
                    } else {
                        transactionDestinationAddressEditText.setText(Constants.DONATE_ADDRESS)
                        updateDonateLabel(true)
                    }
                }
            }
            if (enabled) {
                transactionDestinationScanImageButton.setImageDrawable(getTintedScanTransactionDestinationDrawable(true))
                transactionDestinationScanImageButton.setOnClickListener {
                    listener.onScanRequested()
                }
            } else {
                transactionDestinationScanImageButton.setImageDrawable(getTintedScanTransactionDestinationDrawable(false))
                transactionDestinationScanImageButton.setOnClickListener(null)
            }
            updateDonateLabel(isDonatingAddress(transactionDestinationAddressEditText.text.toString()))
        }

        private fun getTintedRemoveTransactionDestinationDrawable(enabled: Boolean): Drawable {
            return Helper.tintDrawable(itemView.context, transactionDestinationRemoveIcon, enabled)
        }

        private fun getTintedScanTransactionDestinationDrawable(enabled: Boolean): Drawable {
            return Helper.tintDrawable(itemView.context, transactionDestinationScanIcon, enabled)
        }

        private fun isDonatingAddress(address: String): Boolean {
            return address == Constants.DONATE_ADDRESS
        }

        private fun updateDonateLabel(donating: Boolean) {
            if (donating) {
                transactionDestinationDonateLabelTextView.setText(transactionDestinationDonatingLabel)
                transactionDestinationDonateLabelTextView.setTypeface(null, Typeface.BOLD)
            } else {
                transactionDestinationDonateLabelTextView.setText(transactionDestinationDonateLabel)
                transactionDestinationDonateLabelTextView.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    enum class SendAll {
        UNSELECTED,
        SELECTED,
        UNAVAILABLE,
    }
}
