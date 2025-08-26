package org.monfluo.wallet.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.widget.RadioButton
import android.widget.ImageView
import org.monfluo.wallet.R
import org.monfluo.wallet.model.Account
import org.monfluo.wallet.model.MoneroUnit
import org.monfluo.wallet.util.TextHelper

class AccountAdapter(
    private var accounts: List<Account>,
    private var selectedAccount: Account?,
    private val listener: Listener
) : RecyclerView.Adapter<AccountAdapter.ViewHolder>() {

    fun setAccounts(accounts: List<Account>) {
        this.accounts = accounts
        notifyDataSetChanged()
    }

    fun setSelectedAccount(selectedAccount: Account?) {
        this.selectedAccount = selectedAccount
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recycler_view_item_account, parent, false)
        return ViewHolder(listener, view)
    }

    override fun getItemCount(): Int {
        return accounts.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(accounts[position], accounts[position] == selectedAccount)
    }

    interface Listener {
        fun onAccountSelected(self: AccountAdapter, account: Account)
        fun onAccountEditClicked(self: AccountAdapter, account: Account)
    }

    inner class ViewHolder(private val listener: Listener, view: View) : RecyclerView.ViewHolder(view) {

        private lateinit var accountWrapperLayout: View
        private lateinit var accountRadioButton: RadioButton
        private lateinit var accountIconImageView: ImageView
        private lateinit var accountLabelTextView: TextView
        private lateinit var accountBalanceTextView: TextView
        private lateinit var editAccountImageView: ImageView

        private lateinit var account: Account

        fun bind(
            account: Account,
            isSelected: Boolean
        ) {
            this.account = account

            accountWrapperLayout = itemView.findViewById(R.id.account_wrapper_layout)
            accountRadioButton = itemView.findViewById(R.id.account_radiobutton)
            accountIconImageView = itemView.findViewById(R.id.account_icon_imagebutton)
            accountLabelTextView = itemView.findViewById(R.id.account_label_textview)
            accountBalanceTextView = itemView.findViewById(R.id.account_balance_textview)
            editAccountImageView = itemView.findViewById(R.id.edit_account_imagebutton)

            accountRadioButton.setChecked(isSelected)
            accountLabelTextView.text = itemView.resources.getString(R.string.account_label, account.index, account.label)
            accountBalanceTextView.text = TextHelper.formatUnlockedBalance(itemView.context, account.balance.unlocked, MoneroUnit.MONERO, false, 0)

            val onAccountSelected = { _: View -> listener.onAccountSelected(this@AccountAdapter, account) }
            accountWrapperLayout.setOnClickListener(onAccountSelected)
            accountRadioButton.setOnClickListener(onAccountSelected)
            accountIconImageView.setOnClickListener(onAccountSelected)

            editAccountImageView.setOnClickListener {
                listener.onAccountEditClicked(this@AccountAdapter, account)
            }
        }
    }
}