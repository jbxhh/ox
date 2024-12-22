package net.mynero.wallet

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.mynero.wallet.adapter.EnotesAdapter
import net.mynero.wallet.model.Balance
import net.mynero.wallet.model.CoinsInfo
import net.mynero.wallet.service.wallet.WalletService
import net.mynero.wallet.service.wallet.WalletServiceObserver
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.PreferenceUtils

class EnotesActivity : AppCompatActivity(), WalletServiceObserver {

    private val viewModel: EnotesViewModel by viewModels()

    private var walletService: WalletService? = null

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
            override fun onEnoteSelected(coinsInfo: CoinsInfo) {
                val selected = adapter.contains(coinsInfo)
                if (selected) {
                    adapter.deselectEnote(coinsInfo)
                } else {
                    adapter.selectEnote(coinsInfo)
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

        bindService(Intent(applicationContext, WalletService::class.java), connection, 0)
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val walletService = (service as WalletService.WalletServiceBinder).service
            this@EnotesActivity.walletService = walletService
            walletService.addObserver(this@EnotesActivity)
            walletService.refreshEnotes()
        }

        override fun onServiceDisconnected(className: ComponentName) {
            walletService = null
        }
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
            walletService?.freezeEnote(adapter.getSelectedEnotes().keys.filterNotNull().toList())
        }

        unfreezeUtxosButton.setOnClickListener {
            Toast.makeText(this, "Thawing enotes, please wait.", Toast.LENGTH_SHORT)
                .show()
            walletService?.thawEnote(adapter.getSelectedEnotes().keys.filterNotNull().toList())
        }
    }

    private fun bindObservers() {
        enotesRecyclerView.layoutManager = LinearLayoutManager(this)
        enotesRecyclerView.adapter = adapter
        viewModel.enotes.observe(this) { enotes: List<CoinsInfo> ->
            val filteredEnotes = enotes.filter { !it.isSpent }
            if (filteredEnotes.isEmpty()) {
                enotesRecyclerView.visibility = View.GONE
            } else {
                adapter.submitList(filteredEnotes)
                adapter.clear()
                enotesRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    override fun onEnotesRefreshed(enotes: List<CoinsInfo>, balance: Balance) {
        viewModel.updateEnotes(enotes)
    }
}

internal class EnotesViewModel : ViewModel() {
    private val _enotes: MutableLiveData<List<CoinsInfo>> = MutableLiveData()
    val enotes: LiveData<List<CoinsInfo>> = _enotes

    fun updateEnotes(enotes: List<CoinsInfo>) {
        _enotes.postValue(enotes)
    }
}