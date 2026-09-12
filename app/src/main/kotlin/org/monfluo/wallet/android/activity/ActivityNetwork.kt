package org.monfluo.wallet.android.activity

import android.icu.text.DateFormat
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.service.wallet.WalletService
import org.monfluo.wallet.android.service.wallet.WalletSyncData
import org.monfluo.wallet.android.service.wallet.WalletWithData
import org.monfluo.wallet.android.util.component.ActivityMonero
import org.monfluo.wallet.lib.wallet.sync.WalletSyncState
import java.text.NumberFormat
import java.util.Date
import kotlin.getValue
import kotlin.math.max

class ActivityNetwork : ActivityMonero() {
    private val viewModel: NetworkActivityViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView
    private lateinit var nodeStatusHeaderTextView: TextView
    private lateinit var nodeStatusProgressBar: ProgressBar
    private lateinit var nodeStatusDescriptionTextView: TextView
    private lateinit var nodeStatusUpdatedAtTextView: TextView
    private lateinit var walletStatusHeaderTextView: TextView
    private lateinit var walletStatusProgressBar: ProgressBar
    private lateinit var walletStatusDescriptionTextView: TextView
    private lateinit var walletStatusUpdatedAtTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network)

        layoutView = findViewById(R.id.network_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        nodeStatusHeaderTextView = findViewById(R.id.node_status_header_text_view)
        nodeStatusProgressBar = findViewById(R.id.node_status_progress_bar)
        nodeStatusDescriptionTextView = findViewById(R.id.node_status_description_text_view)
        nodeStatusUpdatedAtTextView = findViewById(R.id.node_status_updated_at_text_view)
        walletStatusHeaderTextView = findViewById(R.id.wallet_status_header_text_view)
        walletStatusProgressBar = findViewById(R.id.wallet_status_progress_bar)
        walletStatusDescriptionTextView = findViewById(R.id.wallet_status_description_text_view)
        walletStatusUpdatedAtTextView = findViewById(R.id.wallet_status_updated_at_text_view)

        bindWindowInsetsListener()
        bindListeners()
        bindObservers()
    }

    private fun bindWindowInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(layoutView) { _, insetsCompat ->
            val insets = insetsCompat.getInsets(systemBars() or displayCutout())
            layoutView.updatePadding(
                left = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.left),
                top = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.top),
                right = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.right),
                bottom = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.bottom),
            )
            return@setOnApplyWindowInsetsListener insetsCompat
        }
    }

    private fun bindListeners() {
        backImageView.setOnClickListener {
            finish()
        }
    }

    private fun bindObservers() {
        viewModel.state.observe(this) { state ->
            if (state == null) {
                nodeStatusHeaderTextView.text = getString(R.string.activity_network_node_status_header_waiting_for_wallet)
                nodeStatusProgressBar.isIndeterminate = true
                nodeStatusDescriptionTextView.visibility = View.INVISIBLE
                nodeStatusUpdatedAtTextView.visibility = View.INVISIBLE

                walletStatusHeaderTextView.text = getString(R.string.activity_network_wallet_status_header_opening_wallet)
                walletStatusProgressBar.isIndeterminate = true
                walletStatusDescriptionTextView.visibility = View.INVISIBLE
                walletStatusUpdatedAtTextView.visibility = View.INVISIBLE

                return@observe
            }

            val lastNodeInfo = state.lastNodeInfo
            if (lastNodeInfo != null) {
                nodeStatusUpdatedAtTextView.text = getString(R.string.activity_network_node_last_updated_at, DateFormat.getInstance().format(Date(lastNodeInfo.timestamp)))
                nodeStatusUpdatedAtTextView.visibility = View.VISIBLE
                nodeStatusProgressBar.isIndeterminate = false
                nodeStatusProgressBar.min = 0
                nodeStatusProgressBar.max = lastNodeInfo.nodeInfo.targetHeight.takeIf { it != 0 } ?: lastNodeInfo.nodeInfo.height
                nodeStatusProgressBar.progress = lastNodeInfo.nodeInfo.height
                if (lastNodeInfo.nodeInfo.height < lastNodeInfo.nodeInfo.targetHeight) {
                    nodeStatusHeaderTextView.text = getString(R.string.activity_network_node_status_header_node_is_synchronizing)
                    nodeStatusDescriptionTextView.text =
                        getString(
                            R.string.activity_network_node_status_description_node_is_synchronizing,
                            NumberFormat.getInstance().format(lastNodeInfo.nodeInfo.height - 1),
                            NumberFormat.getInstance().format(lastNodeInfo.nodeInfo.targetHeight - 1),
                            NumberFormat.getInstance().format(lastNodeInfo.nodeInfo.targetHeight - lastNodeInfo.nodeInfo.height),
                        )
                    nodeStatusDescriptionTextView.visibility = View.VISIBLE
                } else {
                    nodeStatusHeaderTextView.text = getString(R.string.activity_network_node_status_header_node_is_synchronized)
                    nodeStatusDescriptionTextView.text =
                        getString(
                            R.string.activity_network_node_status_description_node_is_synchronized,
                            NumberFormat.getInstance().format(lastNodeInfo.nodeInfo.height - 1),
                        )
                    nodeStatusDescriptionTextView.visibility = View.VISIBLE
                }
            } else {
                nodeStatusHeaderTextView.text = getString(R.string.activity_network_node_status_header_fetching_blockchain_data)
                nodeStatusProgressBar.isIndeterminate = true
                nodeStatusDescriptionTextView.visibility = View.INVISIBLE
                nodeStatusUpdatedAtTextView.visibility = View.INVISIBLE
            }

            val adjustedLastSyncState = state.getAdjustedLastSyncState()
            if (adjustedLastSyncState != null) {
                walletStatusUpdatedAtTextView.text = getString(R.string.activity_network_wallet_last_updated_at, DateFormat.getInstance().format(Date(adjustedLastSyncState.timestamp)))
                walletStatusUpdatedAtTextView.visibility = View.VISIBLE

                when (adjustedLastSyncState) {
                    is WalletSyncState.Preparing -> {
                        walletStatusHeaderTextView.text = getString(R.string.activity_network_wallet_status_header_preparing_synchronization)
                        walletStatusProgressBar.isIndeterminate = true
                        walletStatusDescriptionTextView.visibility = View.INVISIBLE
                    }
                    is WalletSyncState.Starting, is WalletSyncState.Syncing -> {
                        val currentHeight = adjustedLastSyncState.currentHeight!!
                        val targetHeight = max(state.lastNodeInfo!!.nodeInfo.height - 1, currentHeight)
                        walletStatusHeaderTextView.text = getString(R.string.activity_network_wallet_status_header_wallet_is_synchronizing)
                        walletStatusProgressBar.isIndeterminate = false
                        walletStatusProgressBar.max = targetHeight
                        walletStatusProgressBar.min = state.initialSyncHeight
                        walletStatusProgressBar.progress = currentHeight
                        walletStatusDescriptionTextView.text =
                            getString(
                                R.string.activity_network_wallet_status_description_wallet_is_synchronizing,
                                NumberFormat.getInstance().format(currentHeight),
                                NumberFormat.getInstance().format(targetHeight),
                                NumberFormat.getInstance().format(targetHeight - currentHeight),
                            )
                        walletStatusDescriptionTextView.visibility = View.VISIBLE
                    }
                    is WalletSyncState.Finished -> {
                        walletStatusHeaderTextView.text = getString(R.string.activity_network_wallet_status_header_wallet_is_synchronized)
                        walletStatusProgressBar.isIndeterminate = false
                        walletStatusProgressBar.min = 0
                        walletStatusProgressBar.max = 100
                        walletStatusProgressBar.progress = 100
                        walletStatusDescriptionTextView.text =
                            getString(
                                R.string.activity_network_wallet_status_description_wallet_is_synchronized,
                                NumberFormat.getInstance().format(adjustedLastSyncState.height),
                            )
                        walletStatusDescriptionTextView.visibility = View.VISIBLE
                    }
                    is WalletSyncState.Cancelled -> {
                        walletStatusHeaderTextView.text = getString(R.string.activity_network_wallet_status_header_cancelled)
                        walletStatusProgressBar.isIndeterminate = true
                        walletStatusDescriptionTextView.visibility = View.INVISIBLE
                    }
                    is WalletSyncState.Failed -> {
                        walletStatusHeaderTextView.text = getString(R.string.activity_network_wallet_status_header_error)
                        walletStatusProgressBar.isIndeterminate = true
                        walletStatusDescriptionTextView.text = adjustedLastSyncState.error.toString()
                        walletStatusDescriptionTextView.visibility = View.VISIBLE
                    }
                }
            } else {
                walletStatusHeaderTextView.text = getString(R.string.activity_network_wallet_status_header_not_synchronizing)
                walletStatusProgressBar.isIndeterminate = true
                walletStatusDescriptionTextView.visibility = View.INVISIBLE
            }
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService.getNewWalletOrLog()?.let { wallet ->
            updateState(wallet)
        }
    }

    override fun onNewWalletUpdated(walletWithData: WalletWithData) {
        updateState(walletWithData)
    }

    private fun updateState(walletWithData: WalletWithData) {
        viewModel.state.postValue(walletWithData.data)
    }
}

internal class NetworkActivityViewModel : ViewModel() {
    val state: MutableLiveData<WalletSyncData?> = MutableLiveData(null)
}
