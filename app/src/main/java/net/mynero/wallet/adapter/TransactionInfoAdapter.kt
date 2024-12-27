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
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.DateHelper
import net.mynero.wallet.util.Helper
import net.mynero.wallet.util.ThemeHelper
import timber.log.Timber
import java.util.Calendar
import java.util.Date

class TransactionInfoAdapter(
    private var streetMode: Boolean,
    private val listener: TxInfoAdapterListener,
) : RecyclerView.Adapter<TransactionInfoAdapter.ViewHolder>() {

    private var localDataSet: List<TransactionInfo> = emptyList()

    fun submitList(dataSet: List<TransactionInfo>) {
        if (localDataSet != dataSet) {
            localDataSet = dataSet
            notifyDataSetChanged()
        }
    }

    fun submitStreetMode(newStreetMode: Boolean) {
        if (streetMode != newStreetMode) {
            streetMode = newStreetMode
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.transaction_history_item, viewGroup, false)
        return ViewHolder(listener, view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val tx = localDataSet[position]
        viewHolder.bind(tx, streetMode)
    }

    override fun getItemCount(): Int {
        return localDataSet.size
    }

    interface TxInfoAdapterListener {
        fun onClickTransaction(txInfo: TransactionInfo)
    }

    class ViewHolder(val listener: TxInfoAdapterListener?, view: View) : RecyclerView.ViewHolder(view) {

        private val outboundColour: Int = ThemeHelper.getThemedColor(view.context, R.attr.negativeColor)
        private val inboundColour: Int = ThemeHelper.getThemedColor(view.context, R.attr.positiveColor)
        private val pendingColour: Int = ThemeHelper.getThemedColor(view.context, R.attr.neutralColor)
        private val failedColour: Int = ThemeHelper.getThemedColor(view.context, R.attr.neutralColor)

        private lateinit var amountTextView: TextView
        private lateinit var confirmationsTextView: TextView
        private lateinit var confirmationsProgressBar: CircularProgressIndicator
        private lateinit var txFailedTextView: TextView
        private lateinit var txDateTimeTextView: TextView

        init {
            val cal = Calendar.getInstance()
            val tz = cal.timeZone //get the local time zone.
            DateHelper.DATETIME_FORMATTER.timeZone = tz
        }

        fun bind(txInfo: TransactionInfo, streetMode: Boolean) {
            confirmationsTextView = itemView.findViewById(R.id.tvConfirmations)
            confirmationsProgressBar = itemView.findViewById(R.id.pbConfirmations)
            amountTextView = itemView.findViewById(R.id.tx_amount)
            txFailedTextView = itemView.findViewById(R.id.tx_failed)
            txDateTimeTextView = itemView.findViewById(R.id.tx_datetime)

            val displayAmount =
                if (streetMode) Constants.STREET_MODE_BALANCE else Helper.getDisplayAmount(
                    txInfo.amount,
                    Helper.DISPLAY_DIGITS_INFO
                )
            confirmationsProgressBar.max = TransactionInfo.CONFIRMATION
            txFailedTextView.visibility = View.GONE
            if (txInfo.isFailed) {
                amountTextView.text = itemView.context.getString(R.string.tx_list_amount_negative, displayAmount)
                txFailedTextView.visibility = View.VISIBLE
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
            }
            if (txInfo.direction == TransactionInfo.Direction.Direction_Out) {
                amountTextView.text =
                    itemView.context.getString(R.string.tx_list_amount_negative, displayAmount)
            } else {
                amountTextView.text =
                    itemView.context.getString(R.string.tx_list_amount_positive, displayAmount)
            }
            txDateTimeTextView.text = getDateTime(txInfo.timestamp)
            itemView.setOnClickListener { _: View? -> listener?.onClickTransaction(txInfo) }
        }

        private fun setTxColour(clr: Int) {
            amountTextView.setTextColor(clr)
        }

        private fun getDateTime(time: Long): String {
            return DateHelper.DATETIME_FORMATTER.format(Date(time * 1000))
        }
    }
}