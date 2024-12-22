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

import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import net.mynero.wallet.R
import net.mynero.wallet.model.CoinsInfo
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.util.Constants

class EnotesAdapter(
    private var enotes: List<CoinsInfo>,
    private val streetMode: Boolean,
    private val listener: EnotesAdapterListener
) : RecyclerView.Adapter<EnotesAdapter.ViewHolder>() {

    private val selectedEnotes: MutableMap<String, CoinsInfo> = HashMap()
    private var editing = false

    fun submitList(enotes: List<CoinsInfo>) {
        this.enotes = enotes
        notifyDataSetChanged()
    }

    fun getSelectedEnotes(): Map<String, CoinsInfo> = selectedEnotes

    fun deselectEnote(coinsInfo: CoinsInfo) {
        selectedEnotes.remove(coinsInfo.pubKey)
        if (selectedEnotes.size == 0) {
            editing = false
        }
        notifyDataSetChanged()
    }

    fun selectEnote(coinsInfo: CoinsInfo) {
        editing = true
        selectedEnotes[coinsInfo.pubKey!!] = coinsInfo
        notifyDataSetChanged()
    }

    operator fun contains(coinsInfo: CoinsInfo): Boolean {
        return selectedEnotes.containsKey(coinsInfo.pubKey)
    }

    fun clear() {
        selectedEnotes.clear()
        editing = false
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.enote_selection_item, viewGroup, false)
        return ViewHolder(listener, view, streetMode)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val tx = enotes[position]
        viewHolder.bind(editing, tx, selectedEnotes)
    }

    override fun getItemCount(): Int {
        return enotes.size
    }

    interface EnotesAdapterListener {
        fun onEnoteSelected(coinsInfo: CoinsInfo)
    }

    class ViewHolder(private val listener: EnotesAdapterListener?, view: View, private val streetMode: Boolean) :
        RecyclerView.ViewHolder(view), View.OnClickListener, OnLongClickListener {
        private var coinsInfo: CoinsInfo? = null
        private var editing = false

        init {
            view.setOnClickListener(this)
            view.setOnLongClickListener(this)
        }

        fun bind(
            editing: Boolean,
            coinsInfo: CoinsInfo,
            selectedEnotes: Map<String, CoinsInfo>
        ) {
            this.editing = editing
            this.coinsInfo = coinsInfo
            val selected = selectedEnotes.containsKey(coinsInfo.pubKey)
            val pubKeyTextView = itemView.findViewById<TextView>(R.id.utxo_pub_key_textview)
            val amountTextView = itemView.findViewById<TextView>(R.id.utxo_amount_textview)
            val addressTextView = itemView.findViewById<TextView>(R.id.utxo_address_textview)
            val globalIdxTextView = itemView.findViewById<TextView>(R.id.utxo_global_index_textview)
            val outpointTextView = itemView.findViewById<TextView>(R.id.utxo_outpoint_textview)
            val balanceString =
                if (streetMode) Constants.STREET_MODE_BALANCE else Wallet.getDisplayAmount(
                    coinsInfo.amount
                )
            amountTextView.text =
                itemView.resources.getString(R.string.tx_amount_no_prefix, balanceString)
            pubKeyTextView.text = coinsInfo.pubKey
            addressTextView.text = coinsInfo.address
            globalIdxTextView.text =
                itemView.resources.getString(
                    R.string.global_index_text,
                    coinsInfo.globalOutputIndex
                )
            outpointTextView.text = itemView.resources.getString(
                R.string.outpoint_text,
                coinsInfo.hash + ":" + coinsInfo.localOutputIndex
            )
            if (selected) {
                itemView.backgroundTintList =
                    ContextCompat.getColorStateList(itemView.context, R.color.oled_colorSecondary)
            } else if (coinsInfo.isFrozen) {
                itemView.backgroundTintList =
                    ContextCompat.getColorStateList(itemView.context, R.color.oled_frozen_utxo)
            } else if (!coinsInfo.isUnlocked) {
                itemView.backgroundTintList =
                    ContextCompat.getColorStateList(itemView.context, R.color.oled_locked_utxo)
            } else {
                itemView.backgroundTintList =
                    ContextCompat.getColorStateList(
                        itemView.context,
                        R.color.oled_dialogBackgroundColor
                    )
            }
        }

        override fun onClick(view: View) {
            if (!editing) return
            val unlocked = coinsInfo?.isUnlocked == true
            if (unlocked) {
                coinsInfo?.let { listener?.onEnoteSelected(it) }
            }
        }

        override fun onLongClick(view: View): Boolean {
            if (editing) return false
            val unlocked = coinsInfo?.isUnlocked == true
            if (unlocked) {
                coinsInfo?.let { listener?.onEnoteSelected(it) }
            }
            return unlocked
        }
    }
}