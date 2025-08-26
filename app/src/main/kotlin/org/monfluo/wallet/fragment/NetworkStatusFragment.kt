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
import org.monfluo.wallet.util.acitivity.MoneroFragment

class NetworkStatusFragment : MoneroFragment() {

    private val viewModel: NetworkStatusFragmentViewModel by viewModels()

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
            val drawable = when (synchronizationStatus) {
                SynchronizationStatus.OpeningWallet -> R.drawable.synchronization_status_starting
                SynchronizationStatus.StartingSynchronization -> R.drawable.synchronization_status_warning
                is SynchronizationStatus.NodeSynchronization -> R.drawable.synchronization_status_in_progress
                is SynchronizationStatus.WalletSynchronization -> R.drawable.synchronization_status_in_progress
                is SynchronizationStatus.Synchronized -> R.drawable.synchronization_status_synced
                is SynchronizationStatus.WalletError -> R.drawable.synchronization_status_error
            }
            networkStatusImageView.setImageDrawable(AppCompatResources.getDrawable(requireContext(), drawable))
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

internal class NetworkStatusFragmentViewModel : ViewModel() {

    val state: MutableLiveData<State> = MutableLiveData()
}