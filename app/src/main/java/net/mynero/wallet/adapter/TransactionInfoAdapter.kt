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
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import net.mynero.wallet.R
import net.mynero.wallet.model.TransactionInfo
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.DateHelper.DATETIME_FORMATTER
import net.mynero.wallet.util.Helper
import net.mynero.wallet.util.Helper.getDisplayAmount
import net.mynero.wallet.util.ThemeHelper.getThemedColor
import java.util.Calendar
import java.util.Date

class TransactionInfoAdapter(val listener: TxInfoAdapterListener?) :
    RecyclerView.Adapter<TransactionInfoAdapter.ViewHolder>() {
    private var localDataSet: List<TransactionInfo>

    /**
     * Initialize the dataset of the Adapter.
     */
    init {
        localDataSet = ArrayList()
    }

    fun submitList(dataSet: List<TransactionInfo>) {
        localDataSet = dataSet
        notifyDataSetChanged()
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.transaction_history_item, viewGroup, false)
        return ViewHolder(listener, view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val tx = localDataSet[position]
        viewHolder.bind(tx)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return localDataSet.size
    }

    interface TxInfoAdapterListener {
        fun onClickTransaction(txInfo: TransactionInfo?)
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(val listener: TxInfoAdapterListener?, view: View) : RecyclerView.ViewHolder(view) {
        private val outboundColour: Int
        private val inboundColour: Int
        private val pendingColour: Int
        private val failedColour: Int
        private var amountTextView: TextView? = null

        init {
            inboundColour = getThemedColor(view.context, R.attr.positiveColor)
            outboundColour = getThemedColor(view.context, R.attr.negativeColor)
            pendingColour = getThemedColor(view.context, R.attr.neutralColor)
            failedColour = getThemedColor(view.context, R.attr.neutralColor)
            val cal = Calendar.getInstance()
            val tz = cal.timeZone //get the local time zone.
            DATETIME_FORMATTER.timeZone = tz
        }

        fun bind(txInfo: TransactionInfo) {
            val streetModeEnabled =
                PrefService.getInstance().getBoolean(Constants.PREF_STREET_MODE, false)
            val displayAmount =
                if (streetModeEnabled) Constants.STREET_MODE_BALANCE else getDisplayAmount(
                    txInfo.amount,
                    Helper.DISPLAY_DIGITS_INFO
                )
            val confirmationsTextView = itemView.findViewById<TextView>(R.id.tvConfirmations)
            val confirmationsProgressBar =
                itemView.findViewById<CircularProgressIndicator>(R.id.pbConfirmations)
            confirmationsProgressBar.max = TransactionInfo.CONFIRMATION
            amountTextView = itemView.findViewById(R.id.tx_amount)
            itemView.findViewById<View>(R.id.tx_failed).visibility = View.GONE
            if (txInfo.isFailed) {
                (itemView.findViewById<View>(R.id.tx_amount) as TextView).text =
                    itemView.context.getString(R.string.tx_list_amount_negative, displayAmount)
                itemView.findViewById<View>(R.id.tx_failed).visibility = View.VISIBLE
                setTxColour(failedColour)
                confirmationsTextView.visibility = View.GONE
                confirmationsProgressBar.visibility = View.GONE
            } else if (txInfo.isPending) {
                setTxColour(pendingColour)
                confirmationsProgressBar.isIndeterminate = true
                confirmationsProgressBar.visibility = View.VISIBLE
                confirmationsTextView.visibility = View.GONE
            } else if (txInfo.direction == TransactionInfo.Direction.Direction_In) {
                setTxColour(inboundColour)
                if (!txInfo.isConfirmed) {
                    confirmationsProgressBar.visibility = View.VISIBLE
                    val confirmations = txInfo.confirmations.toInt()
                    confirmationsProgressBar.setProgressCompat(confirmations, true)
                    val confCount = confirmations.toString()
                    confirmationsTextView.text = confCount
                    if (confCount.length == 1) // we only have space for character in the progress circle
                        confirmationsTextView.visibility =
                            View.VISIBLE else confirmationsTextView.visibility = View.GONE
                } else {
                    confirmationsProgressBar.visibility = View.GONE
                    confirmationsTextView.visibility = View.GONE
                }
            } else {
                setTxColour(outboundColour)
                confirmationsProgressBar.visibility = View.GONE
                confirmationsTextView.visibility = View.GONE
            }
            if (txInfo.direction == TransactionInfo.Direction.Direction_Out) {
                (itemView.findViewById<View>(R.id.tx_amount) as TextView).text =
                    itemView.context.getString(R.string.tx_list_amount_negative, displayAmount)
            } else {
                (itemView.findViewById<View>(R.id.tx_amount) as TextView).text =
                    itemView.context.getString(R.string.tx_list_amount_positive, displayAmount)
            }
            (itemView.findViewById<View>(R.id.tx_datetime) as TextView).text =
                getDateTime(txInfo.timestamp)
            itemView.setOnClickListener { _: View? -> listener?.onClickTransaction(txInfo) }
        }

        private fun setTxColour(clr: Int) {
            amountTextView?.setTextColor(clr)
        }

        private fun getDateTime(time: Long): String {
            return DATETIME_FORMATTER.format(Date(time * 1000))
        }
    }
}