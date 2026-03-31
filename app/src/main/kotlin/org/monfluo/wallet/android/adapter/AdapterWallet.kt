package org.monfluo.wallet.android.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.android.R
import kotlin.io.path.Path
import kotlin.io.path.name

class AdapterWallet(
    private val listener: AdapterWalletListener,
) : RecyclerView.Adapter<AdapterWallet.ViewHolder>() {
    private var walletPaths: List<String> = listOf()

    fun setWalletPaths(walletPaths: List<String>) {
        if (this.walletPaths != walletPaths) {
            this.walletPaths = walletPaths
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

    override fun getItemCount(): Int = walletPaths.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(walletPaths[position])
    }

    interface AdapterWalletListener {
        fun onWalletSelected(
            self: AdapterWallet,
            walletPath: String,
        )

        fun onWalletEdit(
            self: AdapterWallet,
            walletPath: String,
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
            walletPath: String,
        ) {
            walletIconImageButton = itemView.findViewById(R.id.wallet_icon_imagebutton)
            walletNameTextView = itemView.findViewById(R.id.wallet_label_textview)
            editWalletImageButton = itemView.findViewById(R.id.edit_wallet_imagebutton)

            walletNameTextView.text = Path(walletPath).name

            itemView.setOnClickListener {
                listener.onWalletSelected(this@AdapterWallet, walletPath)
            }
            walletIconImageButton.setOnClickListener {
                listener.onWalletSelected(this@AdapterWallet, walletPath)
            }

            editWalletImageButton.setOnClickListener {
                listener.onWalletEdit(this@AdapterWallet, walletPath)
            }
        }
    }
}
