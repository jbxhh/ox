package net.mynero.wallet.fragment.utxos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.mynero.wallet.R
import net.mynero.wallet.adapter.CoinsInfoAdapter
import net.mynero.wallet.adapter.CoinsInfoAdapter.CoinsInfoAdapterListener
import net.mynero.wallet.fragment.dialog.SendBottomSheetDialog
import net.mynero.wallet.model.CoinsInfo
import net.mynero.wallet.service.AddressService
import net.mynero.wallet.service.UTXOService
import net.mynero.wallet.util.MoneroThreadPoolExecutor
import net.mynero.wallet.util.UriData

class UtxosFragment : Fragment(), CoinsInfoAdapterListener, SendBottomSheetDialog.Listener {
    private val adapter = CoinsInfoAdapter(this)
    private var mViewModel: UtxosViewModel? = null
    private var sendUtxosButton: Button? = null
    private var churnUtxosButton: Button? = null
    private var freezeUtxosButton: Button? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_utxos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mViewModel = ViewModelProvider(this)[UtxosViewModel::class.java]
        bindListeners(view)
        bindObservers(view)
    }

    private fun bindListeners(view: View) {
        freezeUtxosButton = view.findViewById(R.id.freeze_utxos_button)
        sendUtxosButton = view.findViewById(R.id.send_utxos_button)
        churnUtxosButton = view.findViewById(R.id.churn_utxos_button)
        sendUtxosButton?.visibility = View.GONE
        churnUtxosButton?.visibility = View.GONE
        freezeUtxosButton?.visibility = View.GONE
        freezeUtxosButton?.setOnClickListener {
            Toast.makeText(context, "Toggling freeze status, please wait.", Toast.LENGTH_SHORT)
                .show()
            MoneroThreadPoolExecutor.MONERO_THREAD_POOL_EXECUTOR?.execute {
                UTXOService.instance?.toggleFrozen(adapter.selectedUtxos)
                activity?.runOnUiThread {
                    adapter.clear()
                    sendUtxosButton?.visibility = View.GONE
                    churnUtxosButton?.visibility = View.GONE
                    freezeUtxosButton?.visibility = View.GONE
                }
            }
        }
        sendUtxosButton?.setOnClickListener {
            val selectedKeyImages = ArrayList<String>()
            for (coinsInfo in adapter.selectedUtxos.values) {
                coinsInfo.keyImage?.let { keyImage -> selectedKeyImages.add(keyImage) }
            }
            activity?.supportFragmentManager?.let { fragmentManager ->
                val sendDialog = SendBottomSheetDialog()
                sendDialog.listener = this
                sendDialog.selectedUtxos = selectedKeyImages
                sendDialog.show(fragmentManager, null)
            }

        }
        churnUtxosButton?.setOnClickListener {
            val selectedKeyImages = ArrayList<String>()
            for (coinsInfo in adapter.selectedUtxos.values) {
                coinsInfo.keyImage?.let { keyImage -> selectedKeyImages.add(keyImage) }
            }
            activity?.supportFragmentManager?.let { fragmentManager ->
                val sendDialog = SendBottomSheetDialog()
                sendDialog.listener = this
                sendDialog.isChurning = true
                sendDialog.uriData =
                    AddressService.instance?.currentSubaddress()?.address?.let { address ->
                        UriData.parse(address)
                    }
                sendDialog.selectedUtxos = selectedKeyImages
                sendDialog.show(fragmentManager, null)
            }
        }
    }

    private fun bindObservers(view: View) {
        val utxosRecyclerView =
            view.findViewById<RecyclerView>(R.id.transaction_history_recyclerview)
        val utxoService = UTXOService.instance
        utxosRecyclerView.layoutManager = LinearLayoutManager(activity)
        utxosRecyclerView.adapter = adapter
        utxoService?.utxos?.observe(viewLifecycleOwner) { utxos: List<CoinsInfo> ->
            val filteredUtxos = HashMap<String?, CoinsInfo>()
            for (coinsInfo in utxos) {
                if (!coinsInfo.isSpent) {
                    filteredUtxos[coinsInfo.pubKey] = coinsInfo
                }
            }
            if (filteredUtxos.isEmpty()) {
                utxosRecyclerView.visibility = View.GONE
            } else {
                adapter.submitList(filteredUtxos)
                utxosRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    override fun onUtxoSelected(coinsInfo: CoinsInfo) {
        val selected = adapter.contains(coinsInfo)
        if (selected) {
            adapter.deselectUtxo(coinsInfo)
        } else {
            adapter.selectUtxo(coinsInfo)
        }
        var frozenExists = false
        var unfrozenExists = false
        for (selectedUtxo in adapter.selectedUtxos.values) {
            if (selectedUtxo.isFrozen || UTXOService.instance?.isCoinFrozen(selectedUtxo) == true)
                frozenExists = true
            else {
                unfrozenExists = true
            }
        }
        val bothExist: Boolean = frozenExists && unfrozenExists
        if (adapter.selectedUtxos.isEmpty()) {
            sendUtxosButton?.visibility = View.GONE
            churnUtxosButton?.visibility = View.GONE
            freezeUtxosButton?.visibility = View.GONE
            freezeUtxosButton?.setBackgroundResource(R.drawable.button_bg_left)
        } else {
            if (frozenExists) {
                freezeUtxosButton?.setBackgroundResource(R.drawable.button_bg)
                sendUtxosButton?.visibility = View.GONE
                churnUtxosButton?.visibility = View.GONE
            } else {
                freezeUtxosButton?.setBackgroundResource(R.drawable.button_bg_left)
                sendUtxosButton?.visibility = View.VISIBLE
                churnUtxosButton?.visibility = View.VISIBLE
            }
            freezeUtxosButton?.visibility = View.VISIBLE
        }
        if (bothExist) {
            freezeUtxosButton?.setText(R.string.toggle_freeze)
        } else if (frozenExists) {
            freezeUtxosButton?.setText(R.string.unfreeze)
        } else if (unfrozenExists) {
            freezeUtxosButton?.setText(R.string.freeze)
        }
    }

    override fun onSentTransaction() {
        adapter.clear()
        churnUtxosButton?.visibility = View.GONE
        sendUtxosButton?.visibility = View.GONE
        freezeUtxosButton?.visibility = View.GONE
    }
}