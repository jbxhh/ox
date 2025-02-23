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
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.R
import org.monfluo.wallet.model.Enote
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.util.Constants
import java.text.NumberFormat

class EnotesAdapter(
    private var enotes: List<Enote>,
    private val streetMode: Boolean,
    private val listener: EnotesAdapterListener
) : RecyclerView.Adapter<EnotesAdapter.ViewHolder>() {

    private val selectedEnotes: MutableMap<Long, Enote> = HashMap()
    private var editing = false

    fun submitList(enotes: List<Enote>) {
        if (this.enotes != enotes) {
            this.enotes = enotes
            notifyDataSetChanged()
        }
    }

    fun getSelectedEnotes(): Map<Long, Enote> = selectedEnotes

    fun deselectEnote(enote: Enote) {
        selectedEnotes.remove(enote.idx)
        if (selectedEnotes.isEmpty()) {
            editing = false
        }
        notifyDataSetChanged()
    }

    fun selectEnote(enote: Enote) {
        editing = true
        selectedEnotes[enote.idx] = enote
        notifyDataSetChanged()
    }

    operator fun contains(enote: Enote): Boolean {
        return selectedEnotes.containsKey(enote.idx)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.recycler_view_item_enote, viewGroup, false)
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
        fun onEnoteSelected(enote: Enote)
    }

    class ViewHolder(private val listener: EnotesAdapterListener, view: View, private val streetMode: Boolean) :
        RecyclerView.ViewHolder(view), View.OnClickListener, OnLongClickListener {
        private var enote: Enote? = null
        private var editing = false

        init {
            view.setOnClickListener(this)
            view.setOnLongClickListener(this)
        }

        fun bind(
            editing: Boolean,
            enote: Enote,
            selectedEnotes: Map<Long, Enote>
        ) {
            this.editing = editing
            this.enote = enote
            val selected = selectedEnotes.containsKey(enote.idx)
            val pubKeyTextView = itemView.findViewById<TextView>(R.id.utxo_pub_key_textview)
            val amountTextView = itemView.findViewById<TextView>(R.id.utxo_amount_textview)
            val addressTextView = itemView.findViewById<TextView>(R.id.utxo_address_textview)
            val globalIdxTextView = itemView.findViewById<TextView>(R.id.utxo_global_index_textview)
            val outpointTextView = itemView.findViewById<TextView>(R.id.utxo_outpoint_textview)
            val balanceString =
                if (streetMode) Constants.STREET_MODE_BALANCE else Wallet.getDisplayAmount(
                    enote.amount
                )
            amountTextView.text =
                itemView.resources.getString(R.string.tx_amount_no_prefix, balanceString)
            pubKeyTextView.text = enote.pubKey
            addressTextView.text = enote.address
            globalIdxTextView.text =
                itemView.resources.getString(
                    R.string.global_index_text,
                    NumberFormat.getInstance().format(enote.globalOutputIndex)
                )
            outpointTextView.text = itemView.resources.getString(
                R.string.outpoint_text,
                enote.hash + ":" + enote.localOutputIndex
            )
            if (selected) {
                itemView.backgroundTintList =
                    ContextCompat.getColorStateList(itemView.context, R.color.oled_colorSecondary)
            } else if (enote.isFrozen) {
                itemView.backgroundTintList =
                    ContextCompat.getColorStateList(itemView.context, R.color.oled_frozen_utxo)
            } else if (!enote.isUnlocked) {
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
            val unlocked = enote?.isUnlocked == true
            if (unlocked) {
                enote?.let { listener.onEnoteSelected(it) }
            }
        }

        override fun onLongClick(view: View): Boolean {
            if (editing) return false
            val unlocked = enote?.isUnlocked == true
            if (unlocked) {
                enote?.let { listener.onEnoteSelected(it) }
            }
            return unlocked
        }
    }
}