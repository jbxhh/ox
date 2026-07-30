package org.monfluo.wallet.android.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.android.R

class AdapterWallet(
    private val listener: AdapterWalletListener,
) : RecyclerView.Adapter<AdapterWallet.ViewHolder>() {
    private var wallets: List<String> = listOf()

    fun setWalletPaths(wallets: List<String>) {
        if (this.wallets != wallets) {
            this.wallets = wallets
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recycler_view_item_wallet, parent, false)
        return ViewHolder(listener, view)
    }

    override fun getItemCount(): Int = wallets.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(wallets[position])
    }

    interface AdapterWalletListener {
        fun onWalletSelected(
            self: AdapterWallet,
            wallet: String,
        )

        fun onWalletEdit(
            self: AdapterWallet,
            wallet: String,
        )
    }

    inner class ViewHolder(
        private val listener: AdapterWalletListener,
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        private lateinit var walletIconImageButton: ImageButton
        private lateinit var walletNameTextView: TextView
        private lateinit var editWalletImageButton: ImageButton

        fun bind(
            wallet: String,
        ) {
            walletIconImageButton = itemView.findViewById(R.id.wallet_icon_imagebutton)
            walletNameTextView = itemView.findViewById(R.id.wallet_label_textview)
            editWalletImageButton = itemView.findViewById(R.id.edit_wallet_imagebutton)

            walletNameTextView.text = wallet

            itemView.setOnClickListener {
                listener.onWalletSelected(this@AdapterWallet, wallet)
            }
            walletIconImageButton.setOnClickListener {
                listener.onWalletSelected(this@AdapterWallet, wallet)
            }

            editWalletImageButton.setOnClickListener {
                listener.onWalletEdit(this@AdapterWallet, wallet)
            }
        }
    }
}
