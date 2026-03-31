package org.monfluo.wallet.android.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.model.Account
import org.monfluo.wallet.android.model.MoneroUnit
import org.monfluo.wallet.android.util.TextHelper

class AdapterAccount(
    private var accounts: List<Account>,
    private var selectedAccount: Account?,
    private val listener: Listener,
) : RecyclerView.Adapter<AdapterAccount.ViewHolder>() {
    fun setAccounts(accounts: List<Account>) {
        if (this.accounts != accounts) {
            this.accounts = accounts
            notifyDataSetChanged()
        }
    }

    fun setSelectedAccount(selectedAccount: Account?) {
        if (this.selectedAccount != selectedAccount) {
            this.selectedAccount = selectedAccount
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recycler_view_item_account, parent, false)
        return ViewHolder(listener, view)
    }

    override fun getItemCount(): Int = accounts.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(accounts[position], accounts[position] == selectedAccount)
    }

    interface Listener {
        fun onAccountSelected(
            self: AdapterAccount,
            account: Account,
        )

        fun onAccountEditClicked(
            self: AdapterAccount,
            account: Account,
        )
    }

    inner class ViewHolder(
        private val listener: Listener,
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        private val accountWrapperLayout: View = itemView.findViewById(R.id.account_wrapper_layout)
        private val accountRadioButton: RadioButton = itemView.findViewById(R.id.account_radiobutton)
        private val accountIconImageView: ImageView = itemView.findViewById(R.id.account_icon_imagebutton)
        private val accountLabelTextView: TextView = itemView.findViewById(R.id.account_label_textview)
        private val accountBalanceTextView: TextView = itemView.findViewById(R.id.account_balance_textview)
        private val editAccountImageView: ImageView = itemView.findViewById(R.id.edit_account_imagebutton)

        fun bind(
            account: Account,
            isSelected: Boolean,
        ) {
            accountRadioButton.setChecked(isSelected)
            accountLabelTextView.text = itemView.resources.getString(R.string.account_label, account.index, account.label)
            accountBalanceTextView.text = TextHelper.formatUnlockedBalance(itemView.context, account.balance.unlocked, MoneroUnit.MONERO, false, 0)

            val onAccountSelected = { _: View -> listener.onAccountSelected(this@AdapterAccount, account) }
            accountWrapperLayout.setOnClickListener(onAccountSelected)
            accountRadioButton.setOnClickListener(onAccountSelected)
            accountIconImageView.setOnClickListener(onAccountSelected)

            editAccountImageView.setOnClickListener {
                listener.onAccountEditClicked(this@AdapterAccount, account)
            }
        }
    }
}
