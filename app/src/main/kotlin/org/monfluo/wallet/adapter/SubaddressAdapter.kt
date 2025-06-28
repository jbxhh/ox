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

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.R
import org.monfluo.wallet.data.Subaddress
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.Helper

class SubaddressAdapter(
    private var addresses: List<Subaddress>,
    private var selectedAddress: Subaddress?,
    private val streetMode: Boolean,
    private val listener: SubaddressAdapterListener
): RecyclerView.Adapter<SubaddressAdapter.ViewHolder>() {

    fun submitAddresses(addresses: List<Subaddress>) {
        this.addresses = addresses
        notifyDataSetChanged()
    }

    fun submitSelectedAddress(selectedAddress: Subaddress?) {
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
        viewHolder.bind(address, selectedAddress != null && address == selectedAddress, streetMode)
    }

    override fun getItemCount(): Int {
        return addresses.size
    }

    interface SubaddressAdapterListener {
        fun onSubaddressSelected(subaddress: Subaddress)
        fun onSubaddressEditLabel(subaddress: Subaddress)
    }

    class ViewHolder(val view: View, val listener: SubaddressAdapterListener) : RecyclerView.ViewHolder(view) {

        private lateinit var addressTextView: TextView
        private lateinit var addressLabelTextView: TextView
        private lateinit var addressAmountTextView: TextView

        fun bind(subaddress: Subaddress, isSelected: Boolean, streetMode: Boolean) {
            addressTextView = itemView.findViewById(R.id.address_item_address_textview)
            addressLabelTextView = itemView.findViewById(R.id.address_label_textview)
            addressAmountTextView = itemView.findViewById(R.id.address_amount_textview)

            addressTextView.text = subaddress.address
            if (isSelected) {
                addressTextView.setTypeface(null, Typeface.BOLD)
            } else {
                addressTextView.setTypeface(null, Typeface.NORMAL)
            }
            val label = subaddress.displayLabel
            val address = itemView.context.getString(
                R.string.subbaddress_info_subtitle,
                subaddress.addressIndex, subaddress.squashedAddress
            )
            addressLabelTextView.text = label.ifEmpty { address }
            val amount = subaddress.totalBalance
            if (subaddress.used) {
                addressTextView.setTextColor(view.resources.getColor(R.color.oled_addressListColor))
            } else {
                addressTextView.setTextColor(view.resources.getColor(R.color.oled_colorPrimaryVariant))
            }
            if (amount > 0) {
                if (streetMode) {
                    addressAmountTextView.text = itemView.context.getString(
                        R.string.tx_list_amount_positive,
                        Constants.STREET_MODE_BALANCE
                    )
                } else {
                    addressAmountTextView.text = itemView.context.getString(
                        R.string.tx_list_amount_positive,
                        Helper.getDisplayAmount(amount, Helper.DISPLAY_DIGITS_INFO)
                    )
                }
            } else addressAmountTextView.text = ""
            itemView.setOnClickListener { listener.onSubaddressSelected(subaddress) }
            itemView.setOnLongClickListener { _: View? ->
                listener.onSubaddressEditLabel(subaddress)
                true
            }
        }
    }
}