package org.monfluo.wallet.fragment

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
import org.monfluo.wallet.activity.ActivityNetwork
import org.monfluo.wallet.R
import org.monfluo.wallet.data.SynchronizationStatus
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.State
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.component.FragmentMonero

class FragmentNetworkStatus : FragmentMonero() {

    private val viewModel: FragmentNetworkStatusViewModel by viewModels()

    private lateinit var networkStatusImageView: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_network_status, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
            val synchronizationStatus = SynchronizationStatus.from(state)
            val (drawable, contentDescriptionResource) = when (synchronizationStatus) {
                SynchronizationStatus.OpeningWallet -> Pair(R.drawable.synchronization_status_starting, R.string.content_description_network_status_icon_opening_wallet)
                SynchronizationStatus.StartingSynchronization -> Pair(R.drawable.synchronization_status_warning, R.string.content_description_network_status_icon_starting_synchronization)
                is SynchronizationStatus.NodeSynchronization -> Pair(R.drawable.synchronization_status_in_progress, R.string.content_description_network_status_icon_synchronizing_node)
                is SynchronizationStatus.WalletSynchronization -> Pair(R.drawable.synchronization_status_in_progress, R.string.content_description_network_status_icon_synchronizing_wallet)
                is SynchronizationStatus.Synchronized -> Pair(R.drawable.synchronization_status_synced, R.string.content_description_network_status_icon_synchronized)
                is SynchronizationStatus.WalletError -> Pair(R.drawable.synchronization_status_error, R.string.content_description_network_status_icon_wallet_error)
            }
            val context = requireContext()
            networkStatusImageView.setImageDrawable(AppCompatResources.getDrawable(context, drawable))
            networkStatusImageView.setContentDescription(context.getString(contentDescriptionResource))
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        updateState(walletService)
    }

    override fun onWalletUpdated(wallet: Wallet) {
        walletService?.let { walletService ->
            updateState(walletService)
        }
    }

    override fun onBlockchainAndTargetHeightsFetched(blockchainHeight: Long, targetHeight: Long) {
        walletService?.let { walletService ->
            updateState(walletService)
        }
    }

    private fun updateState(walletService: WalletService) {
        viewModel.state.postValue(walletService.getState())
    }
}

internal class FragmentNetworkStatusViewModel : ViewModel() {

    val state: MutableLiveData<State> = MutableLiveData()
}
