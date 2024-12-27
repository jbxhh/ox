package net.mynero.wallet.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.mynero.wallet.R
import net.mynero.wallet.model.Account

class AccountAdapter(
    private var accounts: List<Account>,
    private var selectedAccount: Account?,
    private var streetMode: Boolean,
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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.account_item, parent, false)
        return ViewHolder(listener, view, streetMode)
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

    class ViewHolder(private val listener: AccountAdapterListener, view: View, private val streetMode: Boolean) : RecyclerView.ViewHolder(view) {

        private lateinit var addressTextView: TextView
        private lateinit var addressLabelTextView: TextView
        private lateinit var addressAmountTextView: TextView

        private lateinit var account: Account

        fun bind(
            account: Account,
            isSelected: Boolean
        ) {
            this.account = account

            addressTextView = itemView.findViewById(R.id.account_item_account_textview)
            addressLabelTextView = itemView.findViewById(R.id.address_label_textview)
            addressAmountTextView = itemView.findViewById(R.id.address_amount_textview)

            addressTextView.text = account.label
            addressLabelTextView.text = account.index.toString()
            addressAmountTextView.text = account.balance.total.toString()

            if (isSelected) {
                addressTextView.setTypeface(null, Typeface.BOLD)
            } else {
                addressTextView.setTypeface(null, Typeface.NORMAL)
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