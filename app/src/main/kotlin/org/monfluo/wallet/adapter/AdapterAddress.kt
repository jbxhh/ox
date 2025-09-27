/*
 * Copyright (c) 2017 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.monfluo.wallet.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.RadioButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.R
import org.monfluo.wallet.data.Address
import org.monfluo.wallet.model.MoneroUnit
import org.monfluo.wallet.util.TextHelper

class AdapterAddress(
    private var addresses: List<Address>,
    private var selectedAddress: Address?,
    private val streetMode: Boolean,
    private val listener: AdapterAddressListener
): RecyclerView.Adapter<AdapterAddress.ViewHolder>() {

    fun submitAddresses(addresses: List<Address>) {
        this.addresses = addresses
        notifyDataSetChanged()
    }

    fun submitSelectedAddress(selectedAddress: Address?) {
        this.selectedAddress = selectedAddress
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.recycler_view_item_address, viewGroup, false)
        return ViewHolder(view, listener)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val address = addresses[position]
        viewHolder.bind(address, selectedAddress != null && address == selectedAddress)
    }

    override fun getItemCount(): Int {
        return addresses.size
    }

    interface AdapterAddressListener {
        fun onAddressSelected(subaddress: Address)
        fun onAddressEditLabel(subaddress: Address)
    }

    class ViewHolder(val view: View, val listener: AdapterAddressListener) : RecyclerView.ViewHolder(view) {

        private lateinit var addressWrapperLayout: View
        private lateinit var addressRadioButton: RadioButton
        private lateinit var addressIconImageView: ImageView
        private lateinit var addressLabelTextView: TextView
        private lateinit var addressBalanceTextView: TextView
        private lateinit var addressTextView: TextView
        private lateinit var editAddressImageView: ImageView

        fun bind(
            address: Address,
            isSelected: Boolean
        ) {
            addressWrapperLayout = itemView.findViewById(R.id.address_wrapper_layout)
            addressRadioButton = itemView.findViewById(R.id.address_radiobutton)
            addressIconImageView = itemView.findViewById(R.id.address_icon_imagebutton)
            addressLabelTextView = itemView.findViewById(R.id.address_label_textview)
            addressBalanceTextView = itemView.findViewById(R.id.address_balance_textview)
            addressTextView = itemView.findViewById(R.id.address_textview)
            editAddressImageView = itemView.findViewById(R.id.edit_address_imagebutton)

            addressRadioButton.setChecked(isSelected)
            addressLabelTextView.text = itemView.resources.getString(R.string.address_label, address.addressIndex, address.label)
            addressBalanceTextView.text = TextHelper.formatUnlockedBalance(itemView.context, address.totalBalance, MoneroUnit.MONERO, false, 0)

            addressTextView.text = address.address
            if (address.used) {
                addressTextView.setTextColor(view.resources.getColor(R.color.text_subtle))
            } else {
                addressTextView.setTextColor(view.resources.getColor(R.color.text))
            }

            val onAddressSelected = { _: View -> listener.onAddressSelected(address) }
            addressWrapperLayout.setOnClickListener(onAddressSelected)
            addressRadioButton.setOnClickListener(onAddressSelected)
            addressIconImageView.setOnClickListener(onAddressSelected)
            addressTextView.setOnClickListener(onAddressSelected)

            editAddressImageView.setOnClickListener {
                listener.onAddressEditLabel(address)
            }
        }
    }
}
