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
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.UTXOService
import net.mynero.wallet.util.Constants

class CoinsInfoAdapter(val listener: CoinsInfoAdapterListener?) :
    RecyclerView.Adapter<CoinsInfoAdapter.ViewHolder>() {
    private var localDataSet // <public-key, coinsinfo>
            : List<CoinsInfo>

    @JvmField
    val selectedUtxos // <public-key, coinsinfo>
            : HashMap<String?, CoinsInfo>
    private var editing = false

    /**
     * Initialize the dataset of the Adapter.
     */
    init {
        localDataSet = ArrayList()
        selectedUtxos = HashMap()
    }

    fun submitList(dataSet: HashMap<String?, CoinsInfo>) {
        localDataSet = ArrayList(dataSet.values)
        notifyDataSetChanged()
    }

    fun deselectUtxo(coinsInfo: CoinsInfo) {
        selectedUtxos.remove(coinsInfo.pubKey)
        if (selectedUtxos.size == 0) {
            editing = false
        }
        notifyDataSetChanged()
    }

    fun selectUtxo(coinsInfo: CoinsInfo) {
        editing = true
        selectedUtxos[coinsInfo.pubKey] = coinsInfo
        notifyDataSetChanged()
    }

    operator fun contains(coinsInfo: CoinsInfo): Boolean {
        return selectedUtxos.containsKey(coinsInfo.pubKey)
    }

    fun clear() {
        selectedUtxos.clear()
        editing = false
        notifyDataSetChanged()
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.utxo_selection_item, viewGroup, false)
        return ViewHolder(listener, view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val tx = localDataSet[position]
        viewHolder.bind(editing, tx, selectedUtxos)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return localDataSet.size
    }

    interface CoinsInfoAdapterListener {
        fun onUtxoSelected(coinsInfo: CoinsInfo?)
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(private val listener: CoinsInfoAdapterListener?, view: View) :
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
            selectedUtxos: HashMap<String?, CoinsInfo>
        ) {
            this.editing = editing
            this.coinsInfo = coinsInfo
            val selected = selectedUtxos.containsKey(coinsInfo.pubKey)
            val pubKeyTextView = itemView.findViewById<TextView>(R.id.utxo_pub_key_textview)
            val amountTextView = itemView.findViewById<TextView>(R.id.utxo_amount_textview)
            val addressTextView = itemView.findViewById<TextView>(R.id.utxo_address_textview)
            val globalIdxTextView = itemView.findViewById<TextView>(R.id.utxo_global_index_textview)
            val outpointTextView = itemView.findViewById<TextView>(R.id.utxo_outpoint_textview)
            val streetModeEnabled =
                PrefService.instance?.getBoolean(Constants.PREF_STREET_MODE, false)
            val balanceString =
                if (streetModeEnabled == true) Constants.STREET_MODE_BALANCE else Wallet.getDisplayAmount(
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
            } else if (coinsInfo.isFrozen || UTXOService.instance?.isCoinFrozen(coinsInfo) == true) {
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
                listener?.onUtxoSelected(coinsInfo)
            }
        }

        override fun onLongClick(view: View): Boolean {
            if (editing) return false
            val unlocked = coinsInfo?.isUnlocked == true
            if (unlocked) {
                listener?.onUtxoSelected(coinsInfo)
            }
            return unlocked
        }
    }
}