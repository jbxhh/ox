package org.monfluo.wallet.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.R
import kotlin.io.path.Path
import kotlin.io.path.name

class WalletAdapter(
    private var walletPaths: List<String>,
    private val listener: WalletAdapterListener
) : RecyclerView.Adapter<WalletAdapter.ViewHolder>() {

    fun setWalletPaths(walletPaths: List<String>) {
        this.walletPaths = walletPaths
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recycler_view_item_wallet, parent, false)
        return ViewHolder(listener, view)
    }

    override fun getItemCount(): Int {
        return walletPaths.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(walletPaths[position])
    }

    interface WalletAdapterListener {
        fun onWalletSelected(self: WalletAdapter, walletPath: String)
        fun onWalletEdit(self: WalletAdapter, walletPath: String)
    }

    inner class ViewHolder(private val listener: WalletAdapterListener, view: View) : RecyclerView.ViewHolder(view) {

        private lateinit var walletNameTextView: TextView

        private lateinit var walletPath: String

        fun bind(
            walletPath: String,
        ) {
            this.walletPath = walletPath

            walletNameTextView = itemView.findViewById(R.id.account_name_textview)

            walletNameTextView.text = Path(walletPath).name

            itemView.setOnClickListener {
                listener.onWalletSelected(this@WalletAdapter, walletPath)
            }

            itemView.setOnLongClickListener { _ ->
                listener.onWalletEdit(this@WalletAdapter, walletPath)
                true
            }
        }
    }
}