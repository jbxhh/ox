package org.monfluo.wallet.activity

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
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.model.WalletStatus
import org.monfluo.wallet.service.wallet.State
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.component.ActivityMonero
import org.monfluo.wallet.R
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
                bottom = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.bottom)
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
            if (state != null) {
                val (heights, initialWalletStatus, walletLastRefreshedAt, nodeLastRefreshedAt) = state

                if (heights.walletHeight == 0L) {
                    nodeStatusHeaderTextView.text = getString(R.string.activity_network_node_status_header_waiting_for_wallet)
                    nodeStatusProgressBar.isIndeterminate = true
                    nodeStatusDescriptionTextView.visibility = View.INVISIBLE
                    nodeStatusUpdatedAtTextView.visibility = View.INVISIBLE

                    walletStatusHeaderTextView.text = getString(R.string.activity_network_wallet_status_header_opening_wallet)
                    walletStatusProgressBar.isIndeterminate = true
                    walletStatusDescriptionTextView.visibility = View.INVISIBLE
                    walletStatusUpdatedAtTextView.visibility = View.INVISIBLE
                } else {
                    val walletDiff = heights.blockchainHeight - heights.walletHeight
                    val nodeDiff = heights.blockchainTargetHeight - heights.blockchainHeight

                    val walletDisplayHeight = NumberFormat.getInstance().format(heights.walletHeight - 1)
                    val blockchainDisplayHeight = if (heights.blockchainHeight > 0) NumberFormat.getInstance().format(heights.blockchainHeight - 1) else "???"
                    val targetDisplayHeight = if (heights.blockchainTargetHeight > 0) NumberFormat.getInstance().format(heights.blockchainTargetHeight - 1) else "???"

                    val walletDiffDisplay = NumberFormat.getInstance().format(walletDiff)
                    val nodeDiffDisplay = NumberFormat.getInstance().format(nodeDiff)

                    if (nodeLastRefreshedAt != null) {
                        nodeStatusUpdatedAtTextView.text = getString(R.string.activity_network_node_last_updated_at, DateFormat.getInstance().format(Date(nodeLastRefreshedAt)))
                        nodeStatusUpdatedAtTextView.visibility = View.VISIBLE
                    } else {
                        nodeStatusUpdatedAtTextView.visibility = View.INVISIBLE
                    }

                    if (walletLastRefreshedAt != null) {
                        walletStatusUpdatedAtTextView.text = getString(R.string.activity_network_wallet_last_updated_at, DateFormat.getInstance().format(Date(walletLastRefreshedAt)))
                        walletStatusUpdatedAtTextView.visibility = View.VISIBLE
                    } else {
                        walletStatusUpdatedAtTextView.visibility = View.INVISIBLE
                    }

                    if (heights.blockchainHeight == 0L) {
                        nodeStatusHeaderTextView.text = getString(R.string.activity_network_node_status_header_fetching_blockchain_data)
                        nodeStatusProgressBar.isIndeterminate = true
                        nodeStatusDescriptionTextView.visibility = View.INVISIBLE
                    } else if (heights.blockchainHeight < heights.blockchainTargetHeight) {
                        nodeStatusHeaderTextView.text = getString(R.string.activity_network_node_status_header_node_is_synchronizing)
                        nodeStatusProgressBar.isIndeterminate = false
                        nodeStatusProgressBar.max = heights.blockchainTargetHeight.toInt()
                        nodeStatusProgressBar.progress = heights.blockchainHeight.toInt()
                        nodeStatusDescriptionTextView.text = getString(
                            R.string.activity_network_node_status_description_node_is_synchronizing,
                            blockchainDisplayHeight,
                            targetDisplayHeight,
                            nodeDiffDisplay,
                        )
                        nodeStatusDescriptionTextView.visibility = View.VISIBLE
                    } else {
                        nodeStatusHeaderTextView.text = getString(R.string.activity_network_node_status_header_node_is_synchronized)
                        nodeStatusProgressBar.isIndeterminate = false
                        nodeStatusProgressBar.max = heights.blockchainTargetHeight.toInt()
                        nodeStatusProgressBar.progress = heights.blockchainHeight.toInt()
                        nodeStatusDescriptionTextView.text = getString(
                            R.string.activity_network_node_status_description_node_is_synchronized,
                            blockchainDisplayHeight,
                        )
                        nodeStatusDescriptionTextView.visibility = View.VISIBLE
                    }

                    if (initialWalletStatus != null && initialWalletStatus.status == WalletStatus.Critical) {
                        walletStatusHeaderTextView.text = getString(R.string.activity_network_wallet_status_header_error)
                        walletStatusProgressBar.isIndeterminate = true
                        walletStatusDescriptionTextView.text = initialWalletStatus.error
                        walletStatusDescriptionTextView.visibility = View.VISIBLE
                    } else if (heights.walletHeight == heights.blockchainHeight) {
                        walletStatusHeaderTextView.text = getString(R.string.activity_network_wallet_status_header_wallet_is_synchronized)
                        walletStatusProgressBar.isIndeterminate = false
                        walletStatusProgressBar.min = 0
                        walletStatusProgressBar.max = 100
                        walletStatusProgressBar.progress = 100
                        walletStatusDescriptionTextView.text = getString(
                            R.string.activity_network_wallet_status_description_wallet_is_synchronized,
                            walletDisplayHeight,
                        )
                        walletStatusDescriptionTextView.visibility = View.VISIBLE
                    } else if (heights.walletHeight > heights.walletBeginSyncHeight) {
                        walletStatusHeaderTextView.text = getString(R.string.activity_network_wallet_status_header_wallet_is_synchronizing)
                        walletStatusProgressBar.isIndeterminate = false
                        walletStatusProgressBar.max = heights.blockchainHeight.toInt()
                        walletStatusProgressBar.min = heights.walletBeginSyncHeight.toInt()
                        walletStatusProgressBar.progress = heights.walletHeight.toInt()
                        walletStatusDescriptionTextView.text = getString(
                            R.string.activity_network_wallet_status_description_wallet_is_synchronizing,
                            walletDisplayHeight,
                            blockchainDisplayHeight,
                            walletDiffDisplay,
                        )
                        walletStatusDescriptionTextView.visibility = View.VISIBLE
                    } else {
                        walletStatusHeaderTextView.text = getString(R.string.activity_network_wallet_status_header_starting_synchronization)
                        walletStatusProgressBar.isIndeterminate = true
                        walletStatusDescriptionTextView.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        viewModel.state.postValue(walletService.getState())
    }

    override fun onWalletUpdated(wallet: Wallet) {
        walletService?.let { walletService ->
            viewModel.state.postValue(walletService.getState())
        }
    }

    override fun onNewBlock(wallet: Wallet) {
        walletService?.let { walletService ->
            viewModel.state.postValue(walletService.getState())
        }
    }

    override fun onBlockchainAndTargetHeightsFetched(blockchainHeight: Long, targetHeight: Long) {
        walletService?.let { walletService ->
            viewModel.state.postValue(walletService.getState())
        }
    }
}

internal class NetworkActivityViewModel : ViewModel() {

    val state: MutableLiveData<State> = MutableLiveData(null)
}
