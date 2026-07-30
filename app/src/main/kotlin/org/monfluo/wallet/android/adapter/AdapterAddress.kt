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
package org.monfluo.wallet.android.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.model.MoneroUnit
import org.monfluo.wallet.android.util.TextHelper
import org.monfluo.wallet.lib.data.AddressView

class AdapterAddress(
    private var addresses: List<AddressView>,
    private var selectedAddressIndex: Int,
    private val streetMode: Boolean,
    private val listener: AdapterAddressListener,
) : RecyclerView.Adapter<AdapterAddress.ViewHolder>() {
    fun submitAddresses(addresses: List<AddressView>) {
        if (this.addresses != addresses) {
            this.addresses = addresses
            notifyDataSetChanged()
        }
    }

    fun submitSelectedAddressIndex(selectedAddressIndex: Int) {
        if (this.selectedAddressIndex != selectedAddressIndex) {
            this.selectedAddressIndex = selectedAddressIndex
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(
        viewGroup: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater
                .from(viewGroup.context)
                .inflate(R.layout.recycler_view_item_address, viewGroup, false)
        return ViewHolder(view, listener)
    }

    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        position: Int,
    ) {
        val address = addresses[position]
        viewHolder.bind(address, streetMode, position == selectedAddressIndex)
    }

    override fun getItemCount(): Int = addresses.size

    interface AdapterAddressListener {
        fun onAddressSelected(address: AddressView)

        fun onAddressEditLabel(address: AddressView)

        fun onAddressCopy(address: AddressView)
    }

    class ViewHolder(
        val view: View,
        val listener: AdapterAddressListener,
    ) : RecyclerView.ViewHolder(view) {
        private val addressWrapperLayout: View = itemView.findViewById(R.id.address_wrapper_layout)
        private val addressRadioButton: RadioButton = itemView.findViewById(R.id.address_radiobutton)
        private val addressIconImageView: ImageView = itemView.findViewById(R.id.address_icon_imagebutton)
        private val addressLabelTextView: TextView = itemView.findViewById(R.id.address_label_textview)
        private val addressBalanceTextView: TextView = itemView.findViewById(R.id.address_balance_textview)
        private val addressTextView: TextView = itemView.findViewById(R.id.address_textview)
        private val editAddressImageView: ImageView = itemView.findViewById(R.id.edit_address_imagebutton)
        private val copyAddressImageButton: ImageButton = itemView.findViewById(R.id.copy_address_imagebutton)

        fun bind(
            address: AddressView,
            streetMode: Boolean,
            isSelected: Boolean,
        ) {
            addressRadioButton.setChecked(isSelected)
            addressLabelTextView.text = itemView.resources.getString(R.string.address_label, address.addressIndex, "address.label")
            addressBalanceTextView.text = TextHelper.formatUnlockedBalance(itemView.context, address.amount, MoneroUnit.MONERO, streetMode, 0)

            addressTextView.text = TextHelper.formatAddress(address.value)
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
            copyAddressImageButton.setOnClickListener {
                listener.onAddressCopy(address)
            }
        }
    }
}
