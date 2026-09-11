package org.monfluo.wallet.android.adapter

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.model.Contact
import org.monfluo.wallet.android.model.TransactionDestinationRaw
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.Helper
import org.monfluo.wallet.android.util.Helper.replaceElementWithList
import org.monfluo.wallet.android.util.SimpleTextWatcher
import timber.log.Timber

class AdapterTransactionDestination(
    val listener: Listener,
) : RecyclerView.Adapter<AdapterTransactionDestination.ViewHolder>() {
    private var transactionDestinations: List<TransactionDestinationRaw> = listOf(TransactionDestinationRaw())
    private var addressBookEnabled: Boolean = true
    private var sendAll: MutableLiveData<Boolean> = MutableLiveData(false)
    private var enabled: Boolean = true

    override fun onCreateViewHolder(
        viewGroup: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.recycler_view_item_transaction_destination, viewGroup, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        position: Int,
    ) {
        val transactionDestination = transactionDestinations[position]
        val sendAll =
            if (this.sendAll.value!!) {
                SendAll.SELECTED
            } else if (transactionDestinations.size > 1) {
                SendAll.UNAVAILABLE
            } else {
                SendAll.UNSELECTED
            }
        val listener = ViewHolderListenerImpl(position)
        viewHolder.bind(transactionDestination, enabled, sendAll, position, addressBookEnabled, listener)
    }

    override fun getItemCount(): Int = transactionDestinations.size

    fun setEnabled(enabled: Boolean) {
        if (this.enabled != enabled) {
            this.enabled = enabled
            notifyDataSetChanged()
        }
    }

    fun setAddressBookEnabled(enabled: Boolean) {
        if (this.addressBookEnabled != enabled) {
            this.addressBookEnabled = enabled
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

    fun replaceElementWithList(
        transactionDestinations: List<TransactionDestinationRaw>,
        index: Int,
    ) {
        if (transactionDestinations.isEmpty()) {
            Timber.w("Attempted to fill with empty transaction destinations list")
            return
        }
        if (index > this.transactionDestinations.size) {
            Timber.w("Attempted to fill at index $index but the length of existing transaction destinations is ${this.transactionDestinations.size}")
            return
        }
        this.transactionDestinations = this.transactionDestinations.replaceElementWithList(index, transactionDestinations)
        notifyDataSetChanged()
    }

    fun restore(
        transactionDestinations: List<TransactionDestinationRaw>,
        sendAll: Boolean,
    ) {
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

    fun insertContact(
        contact: Contact,
        index: Int,
    ) {
        if (index > this.transactionDestinations.size) {
            Timber.w("Attempted to insert contact at index $index but the length of transaction destinations is ${this.transactionDestinations.size}")
            return
        }
        val transactionDestinations = this.transactionDestinations.toMutableList()
        transactionDestinations[index] = transactionDestinations[index].copy(value = contact.address, recipientName = contact.name)
        this.transactionDestinations = transactionDestinations
        notifyItemChanged(index)
    }

    interface Listener {
        fun onScanRequested(position: Int)

        fun onAddressBookRequested(position: Int)
    }

    interface ViewHolderListener {
        fun onSendAllClicked()

        fun onRemoveClicked()

        fun onAmountChanged(amount: String)

        fun onRecipientNameChanged(recipientName: String)

        fun onAddressChanged(address: String)

        fun onScanRequested()

        fun onAddressBookRequested()
    }

    inner class ViewHolderListenerImpl(
        val index: Int,
    ) : ViewHolderListener {
        override fun onSendAllClicked() {
            if (this@AdapterTransactionDestination.sendAll.value!!) {
                this@AdapterTransactionDestination.sendAll.value = false
            } else {
                transactionDestinations = listOf(transactionDestinations.getOrNull(index) ?: TransactionDestinationRaw())
                this@AdapterTransactionDestination.sendAll.value = true
            }
            notifyDataSetChanged()
        }

        override fun onRemoveClicked() {
            transactionDestinations = transactionDestinations.filterIndexed { i, _ -> i != index }
            notifyDataSetChanged()
        }

        override fun onAmountChanged(amount: String) {
            transactionDestinations.getOrNull(index)?.amount = amount
        }

        override fun onRecipientNameChanged(recipientName: String) {
            transactionDestinations.getOrNull(index)?.recipientName = recipientName
        }

        override fun onAddressChanged(address: String) {
            transactionDestinations.getOrNull(index)?.value = address
        }

        override fun onScanRequested() {
            this@AdapterTransactionDestination.listener.onScanRequested(index)
        }

        override fun onAddressBookRequested() {
            this@AdapterTransactionDestination.listener.onAddressBookRequested(index)
        }
    }

    inner class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        private val transactionDestinationRemoveImageButton: ImageButton = itemView.findViewById(R.id.transaction_destination_remove_image_button)
        private val transactionDestinationScanImageButton: ImageButton = itemView.findViewById(R.id.transaction_destination_scan_image_button)
        private val transactionDestinationAddressBookImageButton: ImageButton = itemView.findViewById(R.id.transaction_destination_address_book_button)
        private val transactionDestinationAmountLabelTextView: TextView = itemView.findViewById(R.id.transaction_destination_amount_label_textview)
        private val transactionDestinationAmountEditText: EditText = itemView.findViewById(R.id.transaction_destination_amount_edittext)
        private val transactionDestinationAddressLabelTextView: TextView = itemView.findViewById(R.id.transaction_destination_address_label_textview)
        private val transactionDestinationRecipientNameLabelTextView: TextView = itemView.findViewById(R.id.transaction_destination_recipient_name_label_textview)
        private val transactionDestinationRecipientNameEditText: EditText = itemView.findViewById(R.id.transaction_destination_recipient_name_edittext)
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
            addressBookEnabled: Boolean,
            listener: ViewHolderListener,
        ) {
            Helper.clearEditTextListeners(transactionDestinationAmountEditText)
            Helper.clearEditTextListeners(transactionDestinationAddressEditText)
            Helper.clearEditTextListeners(transactionDestinationRecipientNameEditText)
            if (addressBookEnabled) {
                transactionDestinationRecipientNameLabelTextView.visibility = View.VISIBLE
                transactionDestinationRecipientNameEditText.visibility = View.VISIBLE
                transactionDestinationAddressBookImageButton.visibility = View.VISIBLE
            } else {
                transactionDestinationRecipientNameLabelTextView.visibility = View.GONE
                transactionDestinationRecipientNameEditText.visibility = View.GONE
                transactionDestinationAddressBookImageButton.visibility = View.GONE
            }
            val single =
                when (sendAll) {
                    SendAll.UNSELECTED -> true
                    SendAll.SELECTED -> true
                    SendAll.UNAVAILABLE -> false
                }
            when (sendAll) {
                SendAll.UNSELECTED -> {
                    transactionDestinationAmountSendAllButton.setText(transactionDestinationSendAllLabel)
                    // transactionDestinationAmountSendAllButton.setEnabled(enabled)
                    transactionDestinationAmountSendAllButton.setEnabled(false)
//                    transactionDestinationAmountSendAllButton.setOnClickListener {
//                        listener.onSendAllClicked()
//                    }
                }
                SendAll.SELECTED -> {
                    transactionDestinationAmountSendAllButton.setText(transactionDestinationSendAllUndoLabel)
                    // transactionDestinationAmountSendAllButton.setEnabled(enabled)
                    transactionDestinationAmountSendAllButton.setEnabled(false)
//                    transactionDestinationAmountSendAllButton.setOnClickListener {
//                        listener.onSendAllClicked()
//                    }
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
                } else {
                    transactionDestinationRemoveImageButton.setImageDrawable(getTintedRemoveTransactionDestinationDrawable(false))
                    transactionDestinationRemoveImageButton.setOnClickListener(null)
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
            transactionDestinationAmountEditText.addTextChangedListener(
                object : SimpleTextWatcher() {
                    override fun afterTextChanged(s: Editable) {
                        val text = s.toString()
                        if (text != transactionDestinationSendingAllText) {
                            listener.onAmountChanged(s.toString())
                        }
                    }
                },
            )
            transactionDestinationAddressEditText.setText(transactionDestination.value)
            transactionDestinationAddressEditText.setFocusableInTouchMode(enabled)
            transactionDestinationAddressEditText.addTextChangedListener(
                object : SimpleTextWatcher() {
                    override fun afterTextChanged(s: Editable) {
                        listener.onAddressChanged(s.toString())
                        updateDonateLabel(isDonatingAddress(s.toString()))
                    }
                },
            )
            transactionDestinationRecipientNameEditText.setText(transactionDestination.recipientName)
            transactionDestinationRecipientNameEditText.setFocusableInTouchMode(enabled)
            transactionDestinationRecipientNameEditText.addTextChangedListener(
                object : SimpleTextWatcher() {
                    override fun afterTextChanged(s: Editable) {
                        listener.onRecipientNameChanged(s.toString())
                    }
                },
            )
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
                transactionDestinationAddressBookImageButton.setOnClickListener {
                    listener.onAddressBookRequested()
                }
            } else {
                transactionDestinationScanImageButton.setImageDrawable(getTintedScanTransactionDestinationDrawable(false))
                transactionDestinationScanImageButton.setOnClickListener(null)
                transactionDestinationAddressBookImageButton.setOnClickListener(null)
            }
            updateDonateLabel(isDonatingAddress(transactionDestinationAddressEditText.text.toString()))
        }

        private fun getTintedRemoveTransactionDestinationDrawable(enabled: Boolean): Drawable = Helper.tintDrawable(itemView.context, transactionDestinationRemoveIcon, enabled)

        private fun getTintedScanTransactionDestinationDrawable(enabled: Boolean): Drawable = Helper.tintDrawable(itemView.context, transactionDestinationScanIcon, enabled)

        private fun isDonatingAddress(address: String): Boolean = address == Constants.DONATE_ADDRESS

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
