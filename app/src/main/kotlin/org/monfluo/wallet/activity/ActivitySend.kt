package org.monfluo.wallet.activity

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.content.res.AppCompatResources
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import org.monfluo.wallet.livedata.combineLiveDatas
import org.monfluo.wallet.model.PendingTransaction
import org.monfluo.wallet.model.TransactionDestinationWithAmount
import org.monfluo.wallet.model.TransactionDestinationRaw
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.UriPayment
import org.monfluo.wallet.util.component.ActivityMonero
import org.monfluo.wallet.R
import org.monfluo.wallet.adapter.AdapterTransactionDestination
import org.monfluo.wallet.activity.ActivityTransactionPending
import timber.log.Timber
import kotlin.math.max
import kotlin.comparisons.maxOf

class ActivitySend : ActivityMonero() {

    private val startActivityToSendPendingTransaction = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == 0) {
            Timber.i("Activity started to send pending transaction returned result code 0, doing nothing")
        } else if (result.resultCode == ActivityTransactionPending.RESULT_OK) {
            Timber.i("Activity started to send pending transaction returned RESULT_OK, finishing current activity")
            finish()
        } else {
            Timber.e("Activity started to send pending transaction returned non-successful exit code: ${result.resultCode}")
        }
    }

    private val startActivityToRequestCameraPermissions = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted: Boolean ->
        if (granted) {
            scanTransactionDestination()
        } else {
            Toast.makeText(this, getString(R.string.activity_send_camera_permission_denied_toast), Toast.LENGTH_SHORT).show()
        }
    }

    private val startActivityToScanTransactionDestination = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        Timber.d("Scan transaction detination callback received")
        val contents = result.contents
        if (contents.isNullOrEmpty()) {
            Timber.d("Contents received from scan transaction destination callback is null or empty")
            return@registerForActivityResult
        }
        val uriPayment = UriPayment.parse(result.contents)
        if (uriPayment == null) {
            Timber.w("Parsed uri payment from scan transaction destination callback contents is null")
            Toast.makeText(this, getString(R.string.activity_send_amount_invalid_uri_payment_toast), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val scanIndex = viewModel.scanIndex
        val transactionDestinations = uriPayment.toTransactionDestinationRawList()
        Timber.i("Inserting ${transactionDestinations.size} transaction destinations at position ${scanIndex}")
        adapterTransactionDestination.replaceElementWithList(transactionDestinations, scanIndex)
    }

    private val viewModel: SendViewModel by viewModels()

    private val enabled: MutableLiveData<Boolean> = MutableLiveData(true)

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView
    private lateinit var goToAccountListImageView: ImageView

    private lateinit var addOutputImageButton: ImageButton
    private lateinit var createButton: Button
    private lateinit var transactionFeeRadioGroup: RadioGroup
    private lateinit var transactionFeeRadioGroupLabelTextView: TextView
    private lateinit var selectedEnotesTextView: TextView

    private lateinit var transactionDestinationsRecyclerView: RecyclerView

    private lateinit var adapterTransactionDestination: AdapterTransactionDestination

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send)

        layoutView = findViewById(R.id.send_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        goToAccountListImageView = findViewById(R.id.go_to_account_list_imageview)
        addOutputImageButton = findViewById(R.id.add_output_imagebutton)
        createButton = findViewById(R.id.transaction_create_button)
        transactionFeeRadioGroup = findViewById(R.id.transaction_fee_radiogroup)
        transactionFeeRadioGroupLabelTextView = findViewById(R.id.transaction_fee_label_textview)
        selectedEnotesTextView = findViewById(R.id.selected_enotes_textview)
        transactionDestinationsRecyclerView = findViewById(R.id.transaction_destinations_recyclerview)

        adapterTransactionDestination = AdapterTransactionDestination(object : AdapterTransactionDestination.Listener {

            override fun onScanRequested(position: Int) {
                viewModel.scanIndex = position
                scanTransactionDestination()
            }
        })

        transactionDestinationsRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionDestinationsRecyclerView.adapter = adapterTransactionDestination

        bindWindowInsetsListener()
        bindListeners()
        bindObservers()
        init(savedInstanceState)
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
        goToAccountListImageView.setOnClickListener {
            startActivity(Intent(this, ActivityAccountList::class.java))
        }
        addOutputImageButton.setOnClickListener {
            adapterTransactionDestination.addTransactionDestination()
        }
        val selectedRadioButtonId = when (viewModel.priority.value!!) {
            PendingTransaction.Priority.Priority_Default -> R.id.default_fee_radiobutton
            PendingTransaction.Priority.Priority_Medium -> R.id.default_fee_radiobutton
            PendingTransaction.Priority.Priority_High -> R.id.default_fee_radiobutton
            else -> R.id.default_fee_radiobutton
        }
        transactionFeeRadioGroup.check(selectedRadioButtonId)
        transactionFeeRadioGroup.setOnCheckedChangeListener { _: RadioGroup?, i: Int ->
            when (i) {
                R.id.default_fee_radiobutton -> viewModel.priority.value = PendingTransaction.Priority.Priority_Default
                R.id.medium_fee_radiobutton -> viewModel.priority.value = PendingTransaction.Priority.Priority_Medium
                R.id.high_fee_radiobutton -> viewModel.priority.value = PendingTransaction.Priority.Priority_High
            }
        }
        createButton.setOnClickListener {
            val walletService = walletService
            if (walletService == null) {
                Toast.makeText(this, getString(R.string.activity_send_transaction_create_failed_no_wallet_toast), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val wallet = walletService.getWallet()
            if (wallet == null) {
                Toast.makeText(this, getString(R.string.activity_send_transaction_create_failed_no_wallet_toast), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sendAll = adapterTransactionDestination.getSendAll().value!!
            val feePriority = viewModel.priority.value!!
            val enotes = viewModel.enotes.value
            val nettype = wallet.nettype()

            if (sendAll) {
                val address = parseTransactionDestinationAddressOrWarn(adapterTransactionDestination.getTransactionDestinations()[0].value, 1, nettype) ?: return@setOnClickListener
                enabled.value = false
                Toast.makeText(this, getString(R.string.activity_send_transaction_creating_toast), Toast.LENGTH_SHORT).show()
                walletService.createSweepTransaction(address, feePriority, enotes)
            } else {
                val transactionDestinations = mutableListOf<TransactionDestinationWithAmount>()
                for (indexed in adapterTransactionDestination.getTransactionDestinations().withIndex()) {
                    val transactionDestination = indexed.value
                    val position = indexed.index + 1
                    val address = parseTransactionDestinationAddressOrWarn(transactionDestination.value, position, nettype) ?: return@setOnClickListener
                    val amount = parseTransactionDestinationAmountOrWarn(transactionDestination.amount, position) ?: return@setOnClickListener
                    transactionDestinations.add(TransactionDestinationWithAmount(address, amount))
                }
                enabled.value = false
                Toast.makeText(this, getString(R.string.activity_send_transaction_creating_toast), Toast.LENGTH_SHORT).show()
                walletService.createTransaction(transactionDestinations, feePriority, enotes)
            }
        }
    }

    private fun bindObservers() {
        enabled.observe(this) { enabled ->
            createButton.isEnabled = enabled
            adapterTransactionDestination.setEnabled(enabled)
        }
        val enableOutputAddImageButton = combineLiveDatas(adapterTransactionDestination.getSendAll(), enabled) { sendAll, globalEnabled ->
            return@combineLiveDatas globalEnabled!! && !sendAll!!
        }
        enableOutputAddImageButton.observe(this) { enabled ->
            addOutputImageButton.setImageDrawable(getTintedAddTransactionDestinationDrawable(enabled))
            addOutputImageButton.setEnabled(enabled)
        }
        viewModel.multiAccountMode.observe(this) { multiAccountMode ->
            if (multiAccountMode) {
                goToAccountListImageView.visibility = View.VISIBLE
            } else {
                goToAccountListImageView.visibility = View.GONE
            }
        }
        viewModel.allowFeeOverride.observe(this) { allowFeeOverride ->
            if (allowFeeOverride) {
                transactionFeeRadioGroup.visibility = View.VISIBLE
                transactionFeeRadioGroupLabelTextView.visibility = View.VISIBLE
            } else {
                transactionFeeRadioGroup.visibility = View.GONE
                transactionFeeRadioGroupLabelTextView.visibility = View.GONE
            }
        }
        viewModel.enotesValue.observe(this) { enotesValue ->
            if (enotesValue != null) {
                selectedEnotesTextView.visibility = View.VISIBLE
                selectedEnotesTextView.text = resources.getString(R.string.activity_send_selected_enotes_value_text, Wallet.getDisplayAmount(enotesValue))
            } else {
                selectedEnotesTextView.visibility = View.GONE
            }
        }
    }

    private fun init(savedInstanceState: Bundle?) {
        val intentData = intent?.getDataString()
        val intentExtras = intent?.extras
        if (savedInstanceState != null) {
            val sendAll = savedInstanceState.getBoolean(Constants.EXTRA_TRANSACTION_SEND_ALL, false)
            val transactionDestinations = savedInstanceState.getParcelableArrayList(Constants.EXTRA_TRANSACTION_DESTINATIONS, TransactionDestinationRaw::class.java) ?: listOf()
            adapterTransactionDestination.restore(transactionDestinations, sendAll)
        } else if (intentData != null) {
            if (WalletService.RUNNING.get()) {
                handlePaymentUri(intentData)
            }
        } else if (intentExtras != null) {
            val enotes = intent.getStringArrayListExtra(Constants.EXTRA_PAYMENT_ENOTES) ?: ArrayList()
            viewModel.enotes.value = enotes
            intent.getStringExtra(Constants.EXTRA_PAYMENT_URI)?.let { paymentUriRaw ->
                handlePaymentUri(paymentUriRaw)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(this)
        val isAllowFeeOverride = PreferenceUtils.isAllowFeeOverride(this)

        viewModel.multiAccountMode.value = isMultiAccountMode
        viewModel.allowFeeOverride.value = isAllowFeeOverride
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val sendAll = adapterTransactionDestination.getSendAll().value!!
        val transactionDestinations = adapterTransactionDestination.getTransactionDestinations()

        outState.putBoolean(Constants.EXTRA_TRANSACTION_SEND_ALL, sendAll)
        outState.putParcelableArrayList(Constants.EXTRA_TRANSACTION_DESTINATIONS, ArrayList(transactionDestinations))
    }

    override fun onWalletServiceNotFound() {
        val intentData = intent?.data
        if (intentData == null) {
            super.onWalletServiceNotFound()
        } else {
            Timber.d("Wallet service is not found but intent data is present. Finishing activity and launching start activity and forwarding intent data there")
            startActivity(Intent(this, ActivityStart::class.java).apply {
                setData(intentData)
            })
            finish()
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService.getWalletOrLog()?.let { wallet ->
            updateEnotesLabel(wallet)
        }
    }

    override fun onWalletUpdated(wallet: Wallet) {
        updateEnotesLabel(wallet)
    }

    override fun onTransactionCreated(pendingTransaction: PendingTransaction) {
        runOnUiThread {
            enabled.value = true
            if (pendingTransaction.status == PendingTransaction.Status.Status_Ok) {
                startActivityToSendPendingTransaction.launch(Intent(this, ActivityTransactionPending::class.java))
            } else {
                Toast.makeText(this, getString(R.string.activity_send_transaction_create_failed, pendingTransaction.getErrorString() ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePaymentUri(paymentUriRaw: String) {
        val paymentUri = UriPayment.parse(paymentUriRaw)
        if (paymentUri == null) {
            Timber.w("Parsed uri payment is null")
            Toast.makeText(this, getString(R.string.activity_send_amount_invalid_uri_payment_toast), Toast.LENGTH_SHORT).show()
        } else {
            val transactionDestinations = paymentUri.toTransactionDestinationRawList()
            Timber.i("Inserting ${transactionDestinations.size} transaction destinations")
            adapterTransactionDestination.replaceElementWithList(transactionDestinations, 0)
        }
    }

    private fun updateEnotesLabel(wallet: Wallet) {
        val selectedEnotes = viewModel.enotes.value
        if (selectedEnotes?.isNotEmpty() == true) {
            var selectedValue: Long = 0
            val enotes = wallet.getEnotes()
            for (coinsInfo in enotes) {
                if (selectedEnotes.contains(coinsInfo.keyImage)) {
                    selectedValue += coinsInfo.amount
                }
            }
            viewModel.enotesValue.postValue(selectedValue)
        }
    }

    private fun scanTransactionDestination() {
        if (Helper.getCameraPermission(this, startActivityToRequestCameraPermissions)) {
            val options = ScanOptions()
            options.setBeepEnabled(false)
            options.setOrientationLocked(true)
            options.setDesiredBarcodeFormats(listOf(Intents.Scan.QR_CODE_MODE))
            options.addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
            startActivityToScanTransactionDestination.launch(options)
        }
    }

    private fun parseTransactionDestinationAddressOrWarn(address: String, position: Int, nettype: Int): String? {
        if (!Helper.isOpenaliasLike(address) && !Wallet.isAddressValid(address, nettype)) {
            Toast.makeText(this, getString(R.string.activity_send_address_invalid_toast, position), Toast.LENGTH_SHORT).show()
            return null
        }
        return address
    }

    private fun parseTransactionDestinationAmountOrWarn(amountRaw: String, position: Int): Long? {
        val amount = Wallet.getAmountFromString(amountRaw)
        if (amount == null || amount == 0L) {
            Toast.makeText(this, getString(R.string.activity_send_amount_invalid_toast, position), Toast.LENGTH_SHORT).show()
            return null
        }
        return amount
    }

    private fun getTintedAddTransactionDestinationDrawable(enabled: Boolean): Drawable {
        val drawable = AppCompatResources.getDrawable(this, android.R.drawable.ic_input_add)!!
        return Helper.tintDrawable(this, drawable, enabled)
    }
}

class SendViewModel : ViewModel() {

    var scanIndex: Int = 0

    val enotes = MutableLiveData<List<String>?>(null)
    val enotesValue = MutableLiveData<Long?>(null)
    var priority = MutableLiveData(PendingTransaction.Priority.Priority_Default)
    val multiAccountMode = MutableLiveData(false)
    val allowFeeOverride = MutableLiveData(false)
}
