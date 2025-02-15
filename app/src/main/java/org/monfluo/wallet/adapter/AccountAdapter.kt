package org.monfluo.wallet.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.R
import org.monfluo.wallet.model.Account

class AccountAdapter(
    private var accounts: List<Account>,
    private var selectedAccount: Account?,
    private val listener: AccountAdapterListener
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

    interface AccountAdapterListener {
        fun onAccountSelected(account: Account)
        fun onAccountEditLabel(account: Account)
    }

    class ViewHolder(private val listener: AccountAdapterListener, view: View) : RecyclerView.ViewHolder(view) {

        private lateinit var accountIndexTextView: TextView
        private lateinit var accountLabelTextView: TextView

        private lateinit var account: Account

        fun bind(
            account: Account,
            isSelected: Boolean
        ) {
            this.account = account

            accountIndexTextView = itemView.findViewById(R.id.account_index_textview)
            accountLabelTextView = itemView.findViewById(R.id.account_label_textview)

            accountIndexTextView.text = account.index.toString()
            accountLabelTextView.text = account.label

            if (isSelected) {
                accountLabelTextView.setTypeface(null, Typeface.BOLD)
            } else {
                accountLabelTextView.setTypeface(null, Typeface.NORMAL)
            }

            itemView.setOnClickListener {
                listener.onAccountSelected(account)
            }
            itemView.setOnLongClickListener {
                listener.onAccountEditLabel(account)
                return@setOnLongClickListener true
            }
        }
    }
}