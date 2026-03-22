package org.monfluo.wallet.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.R
import org.monfluo.wallet.model.Contact
import org.monfluo.wallet.util.TextHelper

class AdapterContact(
    private var contacts: List<Contact>,
    private val listener: Listener,
) : RecyclerView.Adapter<AdapterContact.ViewHolder>() {
    fun submitContacts(contacts: List<Contact>) {
        if (this.contacts != contacts) {
            this.contacts = contacts
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(
        viewGroup: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.recycler_view_item_contact, viewGroup, false)
        return ViewHolder(view, listener)
    }

    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        position: Int,
    ) {
        val contact = contacts[position]
        viewHolder.bind(contact)
    }

    override fun getItemCount(): Int = contacts.size

    interface Listener {
        fun onContactSelected(
            self: AdapterContact,
            contact: Contact,
        )

        fun onContactEditClicked(
            self: AdapterContact,
            contact: Contact,
        )
    }

    inner class ViewHolder(
        val view: View,
        val listener: Listener,
    ) : RecyclerView.ViewHolder(view) {
        private val contactIconImageButton: ImageButton = itemView.findViewById(R.id.contact_icon_imagebutton)
        private val contactLabelTextView: TextView = itemView.findViewById(R.id.contact_label_textview)
        private val contactAddressTextView: TextView = itemView.findViewById(R.id.contact_address_textview)
        private val editContactImageButton: ImageButton = itemView.findViewById(R.id.edit_contact_imagebutton)

        fun bind(
            contact: Contact,
        ) {
            contactLabelTextView.text = contact.name
            contactAddressTextView.text = TextHelper.formatAddress(contact.address)
            val onContactSelected = { _: View -> listener.onContactSelected(this@AdapterContact, contact) }
            contactIconImageButton.setOnClickListener(onContactSelected)
            contactLabelTextView.setOnClickListener(onContactSelected)
            contactAddressTextView.setOnClickListener(onContactSelected)
            editContactImageButton.setOnClickListener { _: View ->
                listener.onContactEditClicked(this@AdapterContact, contact)
            }
        }
    }
}
