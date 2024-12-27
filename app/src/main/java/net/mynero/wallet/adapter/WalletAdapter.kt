import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.mynero.wallet.R
import kotlin.io.path.Path
import kotlin.io.path.name

class WalletAdapter(
    private var walletPaths: List<String>,
    private val listener: AccountAdapterListener
) : RecyclerView.Adapter<WalletAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.wallet_item, parent, false)
        return ViewHolder(listener, view)
    }

    override fun getItemCount(): Int {
        return walletPaths.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(walletPaths[position])
    }

    interface AccountAdapterListener {
        fun onWalletSelected(walletPath: String)
    }

    class ViewHolder(private val listener: AccountAdapterListener, view: View) : RecyclerView.ViewHolder(view) {

        private lateinit var walletNameTextView: TextView

        private lateinit var walletPath: String

        fun bind(
            walletPath: String,
        ) {
            this.walletPath = walletPath

            walletNameTextView = itemView.findViewById(R.id.wallet_name_textview)

            walletNameTextView.text = Path(walletPath).name

            itemView.setOnClickListener {
                listener.onWalletSelected(walletPath)
            }
        }
    }
}