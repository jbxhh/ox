package org.monfluo.wallet.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.R
import org.monfluo.wallet.data.Heights
import org.monfluo.wallet.data.SynchronizationStatus
import org.monfluo.wallet.livedata.combineLiveDatas
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.acitivity.MoneroFragment

class StatusBarFragment : MoneroFragment() {

    private val viewModel: StatusBarFragmentViewModel by viewModels()

    private lateinit var walletAndAccountTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressBarText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_status_bar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        walletAndAccountTextView = view.findViewById(R.id.wallet_and_account_textview)
        progressBar = view.findViewById(R.id.sync_progress_bar)
        progressBarText = view.findViewById(R.id.sync_progress_text)

        bindObservers()
    }

    override fun onResume() {
        super.onResume()

        val context = requireContext()

        val isMultiWalletMode = PreferenceUtils.isMultiWalletMode(context)
        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(context)

        viewModel.setMultiWalletMode(isMultiWalletMode)
        viewModel.setMultiAccountMode(isMultiAccountMode)
    }

    private fun bindObservers() {
        viewModel.showWalletLabel.observe(viewLifecycleOwner) { isShowWalletLabel ->
            if (isShowWalletLabel) {
                walletService?.getWallet()?.let { wallet ->
                    walletAndAccountTextView.visibility = View.VISIBLE
                    walletAndAccountTextView.text = getString(
                        R.string.wallet_name_and_account_label,
                        wallet.name(),
                        wallet.getAccountIndex()
                    )
                }
            } else {
                walletAndAccountTextView.visibility = View.GONE
            }
        }

        viewModel.syncProgress.observe(viewLifecycleOwner) { (walletConnected, heights) ->
            if (walletConnected) {
                val synchronizationStatus = SynchronizationStatus.fromHeights(heights)
                val (walletBeginSyncHeight, walletHeight, daemonHeight) = heights
                progressBarText.visibility = View.VISIBLE
                progressBarText.text = synchronizationStatus.getText(requireContext())
                if (synchronizationStatus is SynchronizationStatus.Synchronized) {
                    progressBar.visibility = View.INVISIBLE
                } else {
                    progressBar.isIndeterminate = false
                    progressBar.max = daemonHeight.toInt()
                    progressBar.min = walletBeginSyncHeight.toInt()
                    progressBar.setProgress(walletHeight.toInt(), true)
                    progressBar.visibility = View.VISIBLE
                }
            } else {
                progressBar.isIndeterminate = true
            }
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        updateState(walletService.getWallet())
        updateHeights(walletService, walletService.getWallet())
    }

    override fun onWalletUpdated(wallet: Wallet) {
        updateState(wallet)
        walletService?.let { walletService ->
            updateHeights(walletService, wallet)
        }
    }

    override fun onBlockchainHeightFetched(height: Long) {
        walletService?.let { walletService ->
            updateHeights(walletService, walletService.getWallet())
        }
    }

    private fun updateState(wallet: Wallet?) {
        viewModel.setWalletConnected(wallet != null)
    }

    private fun updateHeights(walletService: WalletService, wallet: Wallet?) {
        viewModel.setHeights(Heights(walletService.getWalletBeginSyncHeight(), wallet?.getBlockChainHeightJ() ?: 0, walletService.getDaemonHeight()))
    }
}

internal class StatusBarFragmentViewModel : ViewModel() {

    private val _walletConnected = MutableLiveData(false)
    private val _multiWalletMode = MutableLiveData(false)
    private val _multiAccountMode = MutableLiveData(false)
    private val _heights = MutableLiveData(Heights(0L, 0L, 0L))

    val showWalletLabel = combineLiveDatas(
        _walletConnected,
        _multiWalletMode,
        _multiAccountMode,
    ) { _, multiWalletMode, multiAccountMode ->
        return@combineLiveDatas multiWalletMode!! || multiAccountMode!!
    }

    val syncProgress = combineLiveDatas(
        _walletConnected,
        _heights,
    ) { walletConnected, heights ->
        Pair(walletConnected!!, heights!!)
    }

    fun setMultiWalletMode(multiWalletMode: Boolean) {
        _multiWalletMode.postValue(multiWalletMode)
    }

    fun setMultiAccountMode(multiAccountMode: Boolean) {
        _multiAccountMode.postValue(multiAccountMode)
    }

    fun setWalletConnected(walletConnected: Boolean) {
        _walletConnected.postValue(walletConnected)
    }

    fun setHeights(heights: Heights) {
        _heights.postValue(heights)
    }
}