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
import org.monfluo.wallet.NetworkActivity
import org.monfluo.wallet.R
import org.monfluo.wallet.data.Heights
import org.monfluo.wallet.data.SynchronizationStatus
import org.monfluo.wallet.model.Wallet
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
            startActivity(Intent(requireContext(), NetworkActivity::class.java))
        }
    }

    private fun bindObservers() {
        viewModel.heights.observe(viewLifecycleOwner) { heights ->
            val synchronizationStatus = SynchronizationStatus.fromHeights(heights)
            // TODO: we should also detect errors and display an error drawable
            val drawable = when (synchronizationStatus) {
                SynchronizationStatus.StartingSynchronization -> R.drawable.synchronization_status_starting
                is SynchronizationStatus.Synchronizing -> R.drawable.synchronization_status_in_progress
                is SynchronizationStatus.Synchronized -> R.drawable.synchronization_status_synced
            }
            networkStatusImageView.setImageDrawable(AppCompatResources.getDrawable(requireContext(), drawable))
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        updateHeights(walletService, walletService.getWallet())
    }

    override fun onWalletUpdated(wallet: Wallet) {
        walletService?.let { walletService ->
            updateHeights(walletService, wallet)
        }
    }

    override fun onBlockchainHeightFetched(height: Long) {
        walletService?.let { walletService ->
            updateHeights(walletService, walletService.getWallet())
        }
    }

    private fun updateHeights(walletService: WalletService, wallet: Wallet?) {
        viewModel.heights.postValue(Heights(walletService.getWalletBeginSyncHeight(), wallet?.getBlockChainHeightJ() ?: 0, walletService.getDaemonHeight()))
    }
}

internal class NetworkStatusFragmentViewModel : ViewModel() {

    val heights = MutableLiveData(Heights(0L, 0L, 0L))
}