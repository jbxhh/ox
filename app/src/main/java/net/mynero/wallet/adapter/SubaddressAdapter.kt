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
package net.mynero.wallet.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.mynero.wallet.R
import net.mynero.wallet.data.Subaddress
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.Helper

class SubaddressAdapter(
    private var addresses: List<Subaddress>,
    private var selectedAddress: Subaddress?,
    val listener: SubaddressAdapterListener?
): RecyclerView.Adapter<SubaddressAdapter.ViewHolder>() {

    fun submitAddresses(addresses: List<Subaddress>) {
        this.addresses = addresses
        notifyDataSetChanged()
    }

    fun submitSelectedAddress(selectedAddress: Subaddress?) {
        this.selectedAddress = selectedAddress
        notifyDataSetChanged()
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.address_item, viewGroup, false)
        return ViewHolder(view, listener)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val address = addresses[position]
        viewHolder.bind(address, selectedAddress != null && address == selectedAddress)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return addresses.size
    }

    interface SubaddressAdapterListener {
        fun onSubaddressSelected(subaddress: Subaddress?)
        fun onSubaddressEditLabel(subaddress: Subaddress?)
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View, val listener: SubaddressAdapterListener?) :
        RecyclerView.ViewHolder(view) {

        fun bind(subaddress: Subaddress, isSelected: Boolean) {
            val addressTextView =
                itemView.findViewById<TextView>(R.id.address_item_address_textview)
            val addressLabelTextView = itemView.findViewById<TextView>(R.id.address_label_textview)
            val addressAmountTextView =
                itemView.findViewById<TextView>(R.id.address_amount_textview)
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
            val amount = subaddress.amount
            if (amount > 0) {
                val streetMode =
                    PrefService.instance?.getBoolean(Constants.PREF_STREET_MODE, false) == true
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
            itemView.setOnClickListener { listener?.onSubaddressSelected(subaddress) }
            itemView.setOnLongClickListener { _: View? ->
                listener?.onSubaddressEditLabel(subaddress)
                true
            }
        }
    }
}