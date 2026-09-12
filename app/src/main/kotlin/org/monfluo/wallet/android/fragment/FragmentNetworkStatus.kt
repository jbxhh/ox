package org.monfluo.wallet.android.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.activity.ActivityNetwork
import org.monfluo.wallet.android.service.wallet.WalletService
import org.monfluo.wallet.android.service.wallet.WalletSyncData
import org.monfluo.wallet.android.service.wallet.WalletWithData
import org.monfluo.wallet.android.util.component.FragmentMonero
import org.monfluo.wallet.lib.wallet.sync.WalletSyncState

class FragmentNetworkStatus : FragmentMonero() {
    private val viewModel: FragmentNetworkStatusViewModel by viewModels()

    private lateinit var networkStatusImageView: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_network_status, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        networkStatusImageView = view.findViewById(R.id.network_status_imageview)

        bindListeners()
        bindObservers()
    }

    private fun bindListeners() {
        networkStatusImageView.setOnClickListener {
            startActivity(Intent(requireContext(), ActivityNetwork::class.java))
        }
    }

    private fun bindObservers() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            val adjustedLastSyncState = state.getAdjustedLastSyncState()
            val (drawable, contentDescriptionResource) =
                when (adjustedLastSyncState) {
                    null -> Pair(R.drawable.synchronization_status_starting, R.string.content_description_network_status_icon_opening_wallet)
                    is WalletSyncState.Preparing -> Pair(R.drawable.synchronization_status_warning, R.string.content_description_network_status_icon_starting_synchronization)
                    is WalletSyncState.Starting, is WalletSyncState.Syncing -> Pair(R.drawable.synchronization_status_in_progress, R.string.content_description_network_status_icon_synchronizing_wallet)
                    is WalletSyncState.Finished -> Pair(R.drawable.synchronization_status_synced, R.string.content_description_network_status_icon_synchronized)
                    is WalletSyncState.Cancelled -> Pair(R.drawable.synchronization_status_error, R.string.content_description_network_status_icon_wallet_error)
                    is WalletSyncState.Failed -> Pair(R.drawable.synchronization_status_error, R.string.content_description_network_status_icon_wallet_error)
                }
            val context = requireContext()
            networkStatusImageView.setImageDrawable(AppCompatResources.getDrawable(context, drawable))
            networkStatusImageView.setContentDescription(context.getString(contentDescriptionResource))
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService.getNewWalletOrLog()?.let { wallet ->
            updateState(wallet)
        }
    }

    override fun onNewWalletUpdated(
        walletWithData: WalletWithData,
    ) {
        updateState(walletWithData)
    }

    private fun updateState(walletWithData: WalletWithData) {
        viewModel.state.postValue(walletWithData.data)
    }
}

internal class FragmentNetworkStatusViewModel : ViewModel() {
    val state: MutableLiveData<WalletSyncData> = MutableLiveData()
}
