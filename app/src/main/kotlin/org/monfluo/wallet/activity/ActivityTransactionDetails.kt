package org.monfluo.wallet.activity

import android.icu.text.DateFormat
import android.os.Bundle
import android.view.View
import android.graphics.Typeface
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.adapter.AdapterTransactionDetails
import org.monfluo.wallet.livedata.combineLiveDatas
import org.monfluo.wallet.model.MoneroUnit
import org.monfluo.wallet.model.TransactionInfo
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.TextHelper
import org.monfluo.wallet.util.component.ActivityMonero
import org.monfluo.wallet.R
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import timber.log.Timber
import kotlin.math.max

class ActivityTransactionDetails : ActivityMonero() {

    private val viewModel: TransactionActivityViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var transactionDetailsRecyclerView: RecyclerView

    private val adapterTransactionDetails: AdapterTransactionDetails = AdapterTransactionDetails()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!preInit()) {
            finish()
            return
        }
        setContentView(R.layout.activity_transaction)

        layoutView = findViewById(R.id.transaction_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        transactionDetailsRecyclerView = findViewById(R.id.transaction_details_recyclerview)

        transactionDetailsRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionDetailsRecyclerView.adapter = adapterTransactionDetails

        bindWindowInsetsListener()
        bindObservers()
        bindListeners()
    }

    private fun preInit(): Boolean {
        if (viewModel.transactionHash == null) {
            val extras = intent.extras
            if (extras != null && extras.containsKey(Constants.EXTRA_TRANSACTION_ID)) {
                viewModel.transactionHash = extras.getString(Constants.EXTRA_TRANSACTION_ID)!!
                return true
            } else {
                Timber.e("Transaction hash is null in the view model and it was not present in extras")
                return false
            }
        } else {
            return true
        }
    }

    private fun bindWindowInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(layoutView) { _, insetsCompat ->
            val insets = insetsCompat.getInsets(systemBars() or displayCutout())
            layoutView.updatePadding(
                left = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.left),
                top = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.top),
                right = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.right),
                bottom = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.bottom)
            )
            return@setOnApplyWindowInsetsListener insetsCompat
        }
    }

    private fun bindListeners() {
        backImageView.setOnClickListener {
            finish()
        }
    }

    private fun bindObservers() {
        viewModel.streetModeAndTransactionInfo.observe(this) { (streetMode, transactionInfo) ->
            adapterTransactionDetails.submitTransaction(transactionInfo)
            adapterTransactionDetails.submitStreetMode(streetMode)
        }
    }

    override fun onResume() {
        super.onResume()

        val isStreetModeMode = PreferenceUtils.isStreetMode(this)

        viewModel.streetMode.value = isStreetModeMode
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
        val transactions = wallet.getHistory()
        val transactionInfo = transactions.find { it.hash == viewModel.transactionHash }?.let {
            val key = wallet.getTxKey(it.hash)
            val addressValue = wallet.getAddress(it.accountIndex, it.addressIndex)
            val description = wallet.getUserNote(it.hash).takeIf { !it.isBlank() }
            it.copy(key = key, addressValue = addressValue, description = description)
        }

        viewModel.transactionInfo.postValue(transactionInfo)
    }
}

internal class TransactionActivityViewModel : ViewModel() {
    var transactionHash: String? = null

    val streetMode: MutableLiveData<Boolean> = MutableLiveData(false)
    val transactionInfo: MutableLiveData<TransactionInfo?> = MutableLiveData()

    val streetModeAndTransactionInfo = combineLiveDatas(
        streetMode,
        transactionInfo,
    ) { streetMode, transactionInfo ->
        return@combineLiveDatas Pair(streetMode!!, transactionInfo)
    }
}
