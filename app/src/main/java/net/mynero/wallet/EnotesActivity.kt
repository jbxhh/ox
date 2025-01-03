package net.mynero.wallet

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.mynero.wallet.adapter.EnotesAdapter
import net.mynero.wallet.model.Balance
import net.mynero.wallet.model.Enote
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.service.wallet.WalletService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.PreferenceUtils
import net.mynero.wallet.util.acitivity.MoneroActivity

class EnotesActivity : MoneroActivity() {

    private val viewModel: EnotesViewModel by viewModels()

    private lateinit var freezeUtxosButton: Button
    private lateinit var sendUtxosButton: Button
    private lateinit var unfreezeUtxosButton: Button
    private lateinit var enotesRecyclerView: RecyclerView
    private lateinit var adapter: EnotesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enotes)

        freezeUtxosButton = findViewById(R.id.freeze_enotes_button)
        sendUtxosButton = findViewById(R.id.send_enotes_button)
        unfreezeUtxosButton = findViewById(R.id.unfreeze_enotes_button)
        enotesRecyclerView = findViewById(R.id.transaction_history_recyclerview)

        val streetMode = PreferenceUtils.isStreetMode(this)
        adapter = EnotesAdapter(listOf(), streetMode, object : EnotesAdapter.EnotesAdapterListener {
            override fun onEnoteSelected(enote: Enote) {
                val selected = adapter.contains(enote)
                if (selected) {
                    adapter.deselectEnote(enote)
                } else {
                    adapter.selectEnote(enote)
                }
                var frozenExists = false
                var unfrozenExists = false
                for (selectedUtxo in adapter.getSelectedEnotes().values) {
                    if (selectedUtxo.isFrozen)
                        frozenExists = true
                    else {
                        unfrozenExists = true
                    }
                }
                if (adapter.getSelectedEnotes().isEmpty()) {
                    sendUtxosButton.isEnabled = false
                    freezeUtxosButton.isEnabled = false
                    unfreezeUtxosButton.isEnabled = false
                } else {
                    sendUtxosButton.isEnabled = unfrozenExists
                    freezeUtxosButton.isEnabled = unfrozenExists
                    unfreezeUtxosButton.isEnabled = frozenExists
                }
            }
        })

        bindListeners()
        bindObservers()
    }

    private fun bindListeners() {
        sendUtxosButton.setOnClickListener {
            val selectedKeyImages = ArrayList<String>()
            for (coinsInfo in adapter.getSelectedEnotes().values) {
                coinsInfo.keyImage?.let { keyImage -> selectedKeyImages.add(keyImage) }
            }
            val intent = Intent(this, SendActivity::class.java)
            intent.putStringArrayListExtra(Constants.EXTRA_SEND_ENOTES, selectedKeyImages)
            startActivity(intent)
        }

        freezeUtxosButton.setOnClickListener {
            Toast.makeText(this, "Freezing enotes, please wait.", Toast.LENGTH_SHORT)
                .show()
            walletService?.freezeEnote(adapter.getSelectedEnotes().keys.toList())
        }

        unfreezeUtxosButton.setOnClickListener {
            Toast.makeText(this, "Thawing enotes, please wait.", Toast.LENGTH_SHORT)
                .show()
            walletService?.thawEnote(adapter.getSelectedEnotes().keys.toList())
        }
    }

    private fun bindObservers() {
        enotesRecyclerView.layoutManager = LinearLayoutManager(this)
        enotesRecyclerView.adapter = adapter
        viewModel.enotes.observe(this) { enotes: List<Enote> ->
            val filteredEnotes = enotes.filter { !it.isSpent }
            if (filteredEnotes.isEmpty()) {
                enotesRecyclerView.visibility = View.GONE
            } else {
                adapter.submitList(filteredEnotes)
                enotesRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService.getWallet()?.let { wallet ->
            updateState(wallet)
        }
    }

    override fun onWalletUpdated(wallet: Wallet) {
        updateState(wallet)
    }

    private fun updateState(wallet: Wallet) {
        val enotes = wallet.getEnotes()
        val addresses = (0 until wallet.numSubaddresses).map {
            wallet.getSubaddress(wallet.getAccountIndex(), it)
        }.toSet()
        val filteredEnotes = enotes.filter {
            it.address != null && addresses.contains(it.address)
        }
        viewModel.updateEnotes(filteredEnotes)
    }
}

internal class EnotesViewModel : ViewModel() {
    private val _enotes: MutableLiveData<List<Enote>> = MutableLiveData()
    val enotes: LiveData<List<Enote>> = _enotes

    fun updateEnotes(enotes: List<Enote>) {
        _enotes.postValue(enotes)
    }
}