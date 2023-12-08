package net.mynero.wallet.fragment.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.mynero.wallet.MainActivity
import net.mynero.wallet.R
import net.mynero.wallet.adapter.TransactionInfoAdapter
import net.mynero.wallet.adapter.TransactionInfoAdapter.TxInfoAdapterListener
import net.mynero.wallet.model.TransactionInfo
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.BalanceService
import net.mynero.wallet.service.BlockchainService
import net.mynero.wallet.service.DaemonService
import net.mynero.wallet.service.HistoryService
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.ProxyService
import net.mynero.wallet.util.Constants
import timber.log.Timber

class HomeFragment : Fragment(), TxInfoAdapterListener {
    private var startHeight: Long = 0
    private var mViewModel: HomeViewModel? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mainActivity = activity as MainActivity?
        mViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        bindObservers(view)
        bindListeners(view)
        mainActivity?.restartEvents?.observe(viewLifecycleOwner) {
            bindObservers(view)
            bindListeners(view)
        }
    }

    private fun bindListeners(view: View) {
        val settingsImageView = view.findViewById<ImageView>(R.id.settings_imageview)
        val sendButton = view.findViewById<Button>(R.id.send_button)
        val receiveButton = view.findViewById<Button>(R.id.receive_button)
        settingsImageView.setOnClickListener { navigate(HomeFragmentDirections.navToSettings()) }
        sendButton.setOnClickListener { navigate(HomeFragmentDirections.navToSend()) }
        receiveButton.setOnClickListener { navigate(HomeFragmentDirections.navToReceive()) }
    }

    private fun bindObservers(view: View) {
        val txHistoryRecyclerView =
            view.findViewById<RecyclerView>(R.id.transaction_history_recyclerview)
        val unlockedBalanceTextView = view.findViewById<TextView>(R.id.balance_unlocked_textview)
        val lockedBalanceTextView = view.findViewById<TextView>(R.id.balance_locked_textview)
        val balanceService = BalanceService.instance
        val historyService = HistoryService.instance
        val blockchainService = BlockchainService.instance

        ProxyService.instance?.proxyChangeEvents?.observe(viewLifecycleOwner) { proxy ->
            lifecycleScope.launch(Dispatchers.IO) {
                Timber.d("Updating proxy:: $proxy")
                WalletManager.instance?.setProxy(proxy)
                WalletManager.instance?.wallet?.setProxy(proxy)
                WalletManager.instance?.wallet?.init(0)
                WalletManager.instance?.wallet?.startRefresh()
            }
        }

        DaemonService.instance?.daemonChangeEvents?.observe(viewLifecycleOwner) { daemon ->
            lifecycleScope.launch(Dispatchers.IO) {
                Timber.d("Updating daemon:: $daemon")
                WalletManager.instance?.setDaemon(daemon)
                WalletManager.instance?.wallet?.init(0)
                WalletManager.instance?.wallet?.setTrustedDaemon(daemon.trusted)
                WalletManager.instance?.wallet?.startRefresh()
            }
        }

        balanceService?.balanceInfo?.observe(viewLifecycleOwner) { balanceInfo ->
            if (balanceInfo != null) {
                unlockedBalanceTextView.text = balanceInfo.unlockedDisplay
                if (balanceInfo.lockedDisplay == Constants.STREET_MODE_BALANCE || balanceInfo.isLockedBalanceZero) {
                    lockedBalanceTextView.visibility = View.INVISIBLE
                } else {
                    lockedBalanceTextView.text = getString(
                        R.string.wallet_locked_balance_text,
                        balanceInfo.lockedDisplay
                    )
                    lockedBalanceTextView.visibility = View.VISIBLE
                }
            }
        }
        val progressBar = view.findViewById<ProgressBar>(R.id.sync_progress_bar)
        val progressBarText = view.findViewById<TextView>(R.id.sync_progress_text)
        blockchainService?.height?.observe(viewLifecycleOwner) { height: Long ->
            val wallet = WalletManager.instance?.wallet
            if (wallet?.isSynchronized == false) {
                if (startHeight == 0L && height != 1L) {
                    startHeight = height
                }
                val daemonHeight = blockchainService.daemonHeight
                val n = daemonHeight - height
                val x = Math.round(100 - (100f * n / (1f * daemonHeight - startHeight)))
                progressBar.isIndeterminate = height <= 1 || daemonHeight <= 0
                if (height > 1 && daemonHeight > 1) {
                    progressBar.progress = x
                    progressBarText.visibility = View.VISIBLE
                    progressBarText.text = "Syncing... $n blocks remaining"
                } else {
                    progressBarText.visibility = View.GONE
                }
            } else {
                progressBar.visibility = View.INVISIBLE
                progressBarText.visibility = View.VISIBLE
                progressBarText.text = "Synchronized"
            }
        }
        val adapter = TransactionInfoAdapter(this)
        txHistoryRecyclerView.layoutManager = LinearLayoutManager(activity)
        txHistoryRecyclerView.adapter = adapter
        historyService?.history?.observe(viewLifecycleOwner) { history: List<TransactionInfo> ->
            if (history.isEmpty()) {
                // DISPLAYING EMPTY WALLET HISTORY
                val wallet = WalletManager.instance?.wallet
                val textResId: Int
                val botImgResId = if (wallet != null && wallet.isSynchronized) {
                    textResId = R.string.no_history_nget_some_monero_in_here
                    R.drawable.xmrchan_empty // img for synchronized
                } else {
                    textResId = R.string.no_history_loading
                    R.drawable.xmrchan_loading // img for loading
                }
                txHistoryRecyclerView.visibility = View.GONE
                displayEmptyHistory(true, view, textResId, botImgResId)
            } else {
                // POPULATED WALLET HISTORY
                val sortedHistory = history.sortedByDescending { it.timestamp }
                if (sortedHistory.size > 100) {
                    adapter.submitList(sortedHistory.subList(0, 99))
                } else {
                    adapter.submitList(sortedHistory)
                }
                txHistoryRecyclerView.visibility = View.VISIBLE
                displayEmptyHistory(
                    false,
                    view,
                    R.string.no_history_nget_some_monero_in_here,
                    R.drawable.xmrchan_loading
                )
            }
        }
    }

    override fun onClickTransaction(txInfo: TransactionInfo?) {
        val directions: NavDirections = HomeFragmentDirections.navToTransaction(txInfo)
        navigate(directions)
    }

    private fun navigate(destination: NavDirections) {
        val activity = activity
        if (activity != null) {
            val fm = activity.supportFragmentManager
            val navHostFragment = fm.findFragmentById(R.id.nav_host_fragment) as NavHostFragment?
            navHostFragment?.navController?.navigate(destination)
        }
    }

    private fun displayEmptyHistory(
        display: Boolean,
        view: View,
        textResId: Int,
        botImgResId: Int
    ) {
        val mnrjTextView = view.findViewById<TextView>(R.id.monerochan_empty_tx_textview)
        val textView = view.findViewById<TextView>(R.id.empty_tx_textview)
        val botImageView = view.findViewById<ImageView>(R.id.monerochan_imageview)
        view.findViewById<View>(R.id.no_history_layout).visibility =
            if (display) View.VISIBLE else View.GONE
        val displayMonerochan =
            PrefService.instance?.getBoolean(Constants.PREF_MONEROCHAN, true) == true
        if (displayMonerochan) {
            botImageView.visibility = View.VISIBLE
            mnrjTextView.visibility = View.VISIBLE
            textView.visibility = View.GONE
        } else {
            botImageView.visibility = View.GONE
            mnrjTextView.visibility = View.GONE
            textView.visibility = View.VISIBLE
        }
        botImageView.setImageResource(botImgResId)
        mnrjTextView.setText(textResId)
        textView.setText(textResId)
    }
}