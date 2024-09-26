package net.mynero.wallet

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.mynero.wallet.adapter.CoinsInfoAdapter
import net.mynero.wallet.model.CoinsInfo
import net.mynero.wallet.service.AddressService
import net.mynero.wallet.service.UTXOService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.MoneroThreadPoolExecutor

class UtxosActivity : AppCompatActivity() {

    private lateinit var sendUtxosButton: Button
    private lateinit var churnUtxosButton: Button
    private lateinit var freezeUtxosButton: Button
    private lateinit var adapter: CoinsInfoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_utxos)

        freezeUtxosButton = findViewById(R.id.freeze_utxos_button)
        sendUtxosButton = findViewById(R.id.send_utxos_button)
        churnUtxosButton = findViewById(R.id.churn_utxos_button)

        adapter = CoinsInfoAdapter(object : CoinsInfoAdapter.CoinsInfoAdapterListener {
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
                    sendUtxosButton.visibility = View.GONE
                    churnUtxosButton.visibility = View.GONE
                    freezeUtxosButton.visibility = View.GONE
                    freezeUtxosButton.setBackgroundResource(R.drawable.button_bg_left)
                } else {
                    if (frozenExists) {
                        freezeUtxosButton.setBackgroundResource(R.drawable.button_bg)
                        sendUtxosButton.visibility = View.GONE
                        churnUtxosButton.visibility = View.GONE
                    } else {
                        freezeUtxosButton.setBackgroundResource(R.drawable.button_bg_left)
                        sendUtxosButton.visibility = View.VISIBLE
                        churnUtxosButton.visibility = View.VISIBLE
                    }
                    freezeUtxosButton.visibility = View.VISIBLE
                }
                if (bothExist) {
                    freezeUtxosButton.setText(R.string.toggle_freeze)
                } else if (frozenExists) {
                    freezeUtxosButton.setText(R.string.unfreeze)
                } else if (unfrozenExists) {
                    freezeUtxosButton.setText(R.string.freeze)
                }
            }
        })

        bindListeners()
        bindObservers()
    }

    private fun bindListeners() {
        sendUtxosButton.visibility = View.GONE
        churnUtxosButton.visibility = View.GONE
        freezeUtxosButton.visibility = View.GONE
        freezeUtxosButton.setOnClickListener {
            Toast.makeText(this, "Toggling freeze status, please wait.", Toast.LENGTH_SHORT)
                .show()
            MoneroThreadPoolExecutor.MONERO_THREAD_POOL_EXECUTOR?.execute {
                UTXOService.instance?.toggleFrozen(adapter.selectedUtxos)
                runOnUiThread {
                    adapter.clear()
                    sendUtxosButton.visibility = View.GONE
                    churnUtxosButton.visibility = View.GONE
                    freezeUtxosButton.visibility = View.GONE
                }
            }
        }
        sendUtxosButton.setOnClickListener {
            val selectedKeyImages = ArrayList<String>()
            for (coinsInfo in adapter.selectedUtxos.values) {
                coinsInfo.keyImage?.let { keyImage -> selectedKeyImages.add(keyImage) }
            }
            supportFragmentManager.let { fragmentManager ->
                val intent = Intent(this, SendActivity::class.java)
                intent.putStringArrayListExtra(Constants.EXTRA_SEND_UTXOS, selectedKeyImages)
                startActivity(intent)
            }

        }
        churnUtxosButton.setOnClickListener {
            val selectedKeyImages = ArrayList<String>()
            for (coinsInfo in adapter.selectedUtxos.values) {
                coinsInfo.keyImage?.let { keyImage -> selectedKeyImages.add(keyImage) }
            }
            val intent = Intent(this, SendActivity::class.java)
            intent.putExtra(Constants.EXTRA_SEND_ADDRESS, AddressService.instance?.currentSubaddress()?.address)
            intent.putExtra(Constants.EXTRA_SEND_MAX, true)
            intent.putStringArrayListExtra(Constants.EXTRA_SEND_UTXOS, selectedKeyImages)
            startActivity(intent)
        }
    }

    private fun bindObservers() {
        val utxosRecyclerView =
            findViewById<RecyclerView>(R.id.transaction_history_recyclerview)
        val utxoService = UTXOService.instance
        utxosRecyclerView.layoutManager = LinearLayoutManager(this)
        utxosRecyclerView.adapter = adapter
        utxoService?.utxos?.observe(this) { utxos: List<CoinsInfo> ->
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
}