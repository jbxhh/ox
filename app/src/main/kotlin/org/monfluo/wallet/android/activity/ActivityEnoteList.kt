package org.monfluo.wallet.android.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.adapter.AdapterEnote
import org.monfluo.wallet.android.model.Enote
import org.monfluo.wallet.android.model.Wallet
import org.monfluo.wallet.android.service.wallet.WalletService
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.PreferenceUtils
import org.monfluo.wallet.android.util.component.ActivityMonero
import kotlin.math.max

class ActivityEnoteList : ActivityMonero() {
    private val viewModel: EnotesViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView
    private lateinit var headerTextView: TextView
    private lateinit var goToAccountListImageView: ImageView

    private lateinit var freezeEnotesButton: Button
    private lateinit var sendEnotesButton: Button
    private lateinit var unfreezeEnotesButton: Button
    private lateinit var enotesRecyclerView: RecyclerView
    private lateinit var adapter: AdapterEnote

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enote_list)

        layoutView = findViewById(R.id.enotes_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        headerTextView = findViewById(R.id.header_textview)
        goToAccountListImageView = findViewById(R.id.go_to_account_list_imageview)
        freezeEnotesButton = findViewById(R.id.freeze_enotes_button)
        sendEnotesButton = findViewById(R.id.send_enotes_button)
        unfreezeEnotesButton = findViewById(R.id.unfreeze_enotes_button)
        enotesRecyclerView = findViewById(R.id.transaction_history_recyclerview)

        val streetMode = PreferenceUtils.isStreetMode(this)
        adapter =
            AdapterEnote(
                listOf(),
                streetMode,
                object : AdapterEnote.AdapterEnoteListener {
                    override fun onEnoteSelected(enote: Enote) {
                        val selected = adapter.contains(enote)
                        if (selected) {
                            adapter.deselectEnote(enote)
                        } else {
                            adapter.selectEnote(enote)
                        }
                        var frozenExists = false
                        var unfrozenExists = false
                        for (selectedEnote in adapter.getSelectedEnotes().values) {
                            if (selectedEnote.isFrozen) {
                                frozenExists = true
                            } else {
                                unfrozenExists = true
                            }
                        }
                        if (adapter.getSelectedEnotes().isEmpty()) {
                            sendEnotesButton.isEnabled = false
                            freezeEnotesButton.isEnabled = false
                            unfreezeEnotesButton.isEnabled = false
                        } else {
                            sendEnotesButton.isEnabled = unfrozenExists
                            freezeEnotesButton.isEnabled = unfrozenExists
                            unfreezeEnotesButton.isEnabled = frozenExists
                        }
                    }
                },
            )

        bindWindowInsetsListener()
        bindListeners()
        bindObservers()
    }

    private fun bindWindowInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(layoutView) { _, insetsCompat ->
            val insets = insetsCompat.getInsets(systemBars() or displayCutout())
            layoutView.updatePadding(
                left = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.left),
                top = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.top),
                right = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.right),
                bottom = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.bottom),
            )
            return@setOnApplyWindowInsetsListener insetsCompat
        }
    }

    private fun bindListeners() {
        backImageView.setOnClickListener {
            finish()
        }
        goToAccountListImageView.setOnClickListener {
            startActivity(Intent(this, ActivityAccountList::class.java))
        }
        sendEnotesButton.setOnClickListener {
            val selectedKeyImages = ArrayList<String>()
            for (coinsInfo in adapter.getSelectedEnotes().values) {
                coinsInfo.keyImage?.let { keyImage -> selectedKeyImages.add(keyImage) }
            }
            val intent = Intent(this, ActivitySend::class.java)
            intent.putStringArrayListExtra(Constants.EXTRA_PAYMENT_ENOTES, selectedKeyImages)
            startActivity(intent)
        }

        freezeEnotesButton.setOnClickListener {
            Toast.makeText(this, getText(R.string.toast_freezing_enotes), Toast.LENGTH_SHORT).show()
            walletService?.freezeEnote(adapter.getSelectedEnotes().keys.toList())
        }

        unfreezeEnotesButton.setOnClickListener {
            Toast.makeText(this, getText(R.string.toast_thawing_enotes), Toast.LENGTH_SHORT).show()
            walletService?.thawEnote(adapter.getSelectedEnotes().keys.toList())
        }
    }

    private fun bindObservers() {
        enotesRecyclerView.layoutManager = LinearLayoutManager(this)
        enotesRecyclerView.adapter = adapter
        viewModel.multiAccountMode.observe(this) { multiAccountMode ->
            if (multiAccountMode) {
                goToAccountListImageView.visibility = View.VISIBLE
            } else {
                goToAccountListImageView.visibility = View.GONE
            }
        }
        viewModel.enotes.observe(this) { enotes: List<Enote> ->
            val filteredEnotes = enotes.filter { !it.isSpent }
            if (filteredEnotes.isEmpty()) {
                headerTextView.text = getString(R.string.enotes_header)
                enotesRecyclerView.visibility = View.GONE
            } else {
                headerTextView.text = getString(R.string.enotes_with_amount_header, filteredEnotes.size)
                adapter.submitList(filteredEnotes)
                enotesRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(this)

        viewModel.multiAccountMode.postValue(isMultiAccountMode)
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
        val addresses =
            (0 until wallet.numAddresses)
                .map {
                    wallet.getAddress(wallet.getAccountIndex(), it)
                }.toSet()
        val filteredEnotes =
            enotes.filter {
                it.address != null && addresses.contains(it.address)
            }
        viewModel.updateEnotes(filteredEnotes)
    }
}

internal class EnotesViewModel : ViewModel() {
    private val _enotes: MutableLiveData<List<Enote>> = MutableLiveData()
    val enotes: LiveData<List<Enote>> = _enotes

    val multiAccountMode = MutableLiveData(false)

    fun updateEnotes(enotes: List<Enote>) {
        _enotes.postValue(enotes)
    }
}
