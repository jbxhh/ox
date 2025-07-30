package org.monfluo.wallet

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
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.ncorti.slidetoact.SlideToActView
import com.ncorti.slidetoact.SlideToActView.OnSlideCompleteListener
import org.monfluo.wallet.model.MoneroUnit
import org.monfluo.wallet.model.PendingTransaction
import org.monfluo.wallet.model.PendingTransactionDestination
import org.monfluo.wallet.model.TransactionDestinationWithAmount
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.TextHelper
import org.monfluo.wallet.util.UriData
import org.monfluo.wallet.util.acitivity.MoneroActivity
import timber.log.Timber
import kotlin.math.max

// TODO: refactor transaction confirmation layout into a separate activity/fragment
class SendActivity : MoneroActivity() {

    var priority: PendingTransaction.Priority = PendingTransaction.Priority.Priority_Default

    private val viewModel: SendViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var sendMaxButton: Button
    private lateinit var addOutputImageView: ImageButton
    private lateinit var destList: LinearLayout
    private lateinit var createButton: Button
    private lateinit var sendTxSlider: SlideToActView
    private lateinit var feeRadioGroup: RadioGroup
    private lateinit var feeRadioGroupLabelTextView: TextView
    private lateinit var feeTextView: TextView
    private lateinit var addressTextView: TextView
    private lateinit var addressResolvedFromTextView: TextView
    private lateinit var amountTextView: TextView
    private lateinit var selectedUtxosValueTextView: TextView

    private var currentEntryIndex = -1

    private val qrCodeLauncher =
        registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
            if (result.contents != null) {
                if (currentEntryIndex != -1) {
                    pasteAddress(getDestView(currentEntryIndex), result.contents, false)
                    currentEntryIndex = -1
                }
            }
        }

    private val cameraPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            onScan(currentEntryIndex)
        } else {
            Toast.makeText(this, getString(R.string.no_camera_permission), Toast.LENGTH_SHORT)
                .show()
            currentEntryIndex = -1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send)

        layoutView = findViewById(R.id.send_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        sendMaxButton = findViewById(R.id.send_max_button)
        addOutputImageView = findViewById(R.id.add_output_button)
        destList = findViewById(R.id.transaction_destination_list)
        createButton = findViewById(R.id.create_tx_button)
        feeRadioGroup = findViewById(R.id.tx_fee_radiogroup)
        feeTextView = findViewById(R.id.fee_textview)
        sendTxSlider = findViewById(R.id.send_tx_slider)
        addressTextView = findViewById(R.id.address_pending_textview)
        addressResolvedFromTextView = findViewById(R.id.address_resolved_from_pending_textview)
        amountTextView = findViewById(R.id.amount_pending_textview)
        feeRadioGroup = findViewById(R.id.tx_fee_radiogroup)
        feeRadioGroupLabelTextView = findViewById(R.id.tx_fee_radiogroup_label_textview)
        selectedUtxosValueTextView = findViewById(R.id.selected_utxos_value_textview)

        bindWindowInsetsListener()
        bindListeners()
        bindObservers()
        init()
    }

    private fun bindWindowInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(layoutView) { _, insetsCompat ->
            val insets = insetsCompat.getInsets(systemBars() or displayCutout())
            layoutView.updatePadding(
                left = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.left),
                top = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.top),
                right = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.right),
                bottom = max(resources.getDimensionPixelOffset(R.dimen.standard_padding), insets.bottom)
            )
            return@setOnApplyWindowInsetsListener insetsCompat
        }
    }

    private fun bindListeners() {
        backImageView.setOnClickListener {
            finish()
        }
        feeRadioGroup.check(R.id.default_fee_radiobutton)
        priority = PendingTransaction.Priority.Priority_Default
        feeRadioGroup.setOnCheckedChangeListener { _: RadioGroup?, i: Int ->
            when (i) {
                R.id.default_fee_radiobutton -> priority = PendingTransaction.Priority.Priority_Default
                R.id.medium_fee_radiobutton -> priority = PendingTransaction.Priority.Priority_Medium
                R.id.high_fee_radiobutton -> priority = PendingTransaction.Priority.Priority_High
            }
        }
        if (PreferenceUtils.isAllowFeeOverride(this)) {
            feeRadioGroup.visibility = View.VISIBLE
            feeRadioGroupLabelTextView.visibility = View.VISIBLE
        } else {
            feeRadioGroup.visibility = View.GONE
            feeRadioGroupLabelTextView.visibility = View.GONE
        }
        addOutputImageView.setOnClickListener {
            sendMaxButton.visibility = View.GONE
            val outputCount = destCount
            if (outputCount < 8) {
                addOutput(false)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.max_outputs_allowed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        sendMaxButton.setOnClickListener { viewModel.setSendingMax(!isSendAll) }
        createButton.setOnClickListener {
            val wallet = walletService?.getWallet()
            if (wallet == null) {
                Toast.makeText(
                    this,
                    getString(R.string.cannot_create_transaction_no_wallet),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            val outputsValid = checkDestinationsValidity(wallet, isSendAll)
            if (outputsValid) {
                Toast.makeText(this, getString(R.string.creating_tx), Toast.LENGTH_SHORT).show()
                createButton.isEnabled = false
                sendMaxButton.isEnabled = false
                createTx(rawDests, isSendAll, priority, viewModel.enotes.value ?: ArrayList())
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.creating_tx_failed_invalid_outputs),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        sendTxSlider.onSlideCompleteListener =
            object : OnSlideCompleteListener {
                override fun onSlideComplete(view: SlideToActView) {
                    confirmSlider()
                }
            }

        sendTxSlider.let { slideToActView ->
            ViewCompat.addAccessibilityAction(
                slideToActView,
                getString(R.string.approve_the_transaction)
            ) { _, _ ->
                confirmSlider()
                return@addAccessibilityAction true
            }
        }
    }

    private fun bindObservers() {
        viewModel.sendingMax.observe(this) { sendingMax: Boolean? ->
            if (viewModel.pendingTransaction.value == null) {
                if (sendingMax == true) {
                    prepareOutputsForMaxSend()
                    sendMaxButton.text = getText(R.string.undo)
                } else {
                    unprepareMaxSend()
                    sendMaxButton.text = getText(R.string.send_max)
                }
            }
        }
        viewModel.showAddOutputButton.observe(this) { show: Boolean? ->
            setAddOutputButtonVisibility(
                if (show == true && !destsHasPaymentId()) View.VISIBLE else View.INVISIBLE
            )
        }
        viewModel.pendingTransaction.observe(this) { pair ->
            showConfirmationLayout(pair != null)
            if (pair != null) {
                val (pendingTransaction, pendingTransactionGenericDestination) = pair

                val resolvedDestinations = pendingTransactionGenericDestination.getResolvedDestinations()
                val isSingleDestination = resolvedDestinations.size == 1
                val firstResolvedDestination = resolvedDestinations[0]
                val address = firstResolvedDestination.address
                val resolvedFrom = firstResolvedDestination.resolvedFrom?.value

                if (isSingleDestination && resolvedFrom != null) {
                    addressTextView.text = getString(R.string.tx_address_text, address)
                    addressResolvedFromTextView.visibility = View.VISIBLE
                    addressResolvedFromTextView.text = getString(R.string.tx_address_resolved_from_text, getAddressField(0).text.toString())
                } else {
                    val displayAddress = if (isSingleDestination) address else getText(R.string.multiple_addresses)
                    addressTextView.text = getString(R.string.tx_address_text, displayAddress)
                    addressResolvedFromTextView.visibility = View.GONE
                }

                amountTextView.text = getString(
                    R.string.tx_amount_text,
                    TextHelper.getDisplayAmount(pendingTransaction.getAmount(), MoneroUnit.MONERO, false)
                )
                feeTextView.text = getString(R.string.tx_fee_text, TextHelper.getDisplayAmount(pendingTransaction.getFee(), MoneroUnit.MONERO, false))
            }
        }
    }

    private fun init() {
        val address = intent.getStringExtra(Constants.EXTRA_SEND_ADDRESS)
        val amount = intent.getStringExtra(Constants.EXTRA_SEND_AMOUNT)
        val max = intent.getBooleanExtra(Constants.EXTRA_SEND_MAX, false)
        val enotes = intent.getStringArrayListExtra(Constants.EXTRA_SEND_ENOTES) ?: ArrayList()
        addOutput(true, address, amount)
        viewModel.setSendingMax(max)
        viewModel.setEnotes(enotes)
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        updateEnotesLabel()
    }

    private fun updateEnotesLabel() {
        walletService?.getWalletOrLog()?.let { wallet ->
            val selectedEnotes = viewModel.enotes.value
            if (selectedEnotes?.isNotEmpty() == true) {
                var selectedValue: Long = 0
                val enotes = wallet.getEnotes()
                for (coinsInfo in enotes) {
                    if (selectedEnotes.contains(coinsInfo.keyImage)) {
                        selectedValue += coinsInfo.amount
                    }
                }
                val valueString = Wallet.getDisplayAmount(selectedValue)
                selectedUtxosValueTextView.visibility = View.VISIBLE
                selectedUtxosValueTextView.text = resources.getString(
                    R.string.selected_utxos_value,
                    valueString
                )
            } else {
                selectedUtxosValueTextView.visibility = View.GONE
            }
        }
    }

    private fun confirmSlider() {
        val (pendingTx) = viewModel.pendingTransaction.value ?: return
        Toast.makeText(this, getString(R.string.sending_tx), Toast.LENGTH_SHORT)
            .show()
        sendTx(pendingTx)
    }

    private fun checkDestinationsValidity(wallet: Wallet, sendAll: Boolean): Boolean {
        val dests = rawDests
        for (dest in dests) {
            val (address, amount) = dest
            if (!sendAll) {
                if (amount.isEmpty()) {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_send_amount_empty),
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }
                val amountRaw = Wallet.getAmountFromString(amount)
                val balance = wallet.unlockedBalance
                if (amountRaw >= balance || amountRaw <= 0) {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_send_amount_invalid),
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }
            } else if (dests.size > 1) {
                Toast.makeText(
                    this,
                    getString(R.string.toast_send_amount_invalid_sendall_paytomany),
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
            val uriData = UriData.parse(address)
            val isValidAddress = uriData != null
            if (!isValidAddress) {
                Toast.makeText(
                    this,
                    getString(R.string.toast_send_address_invalid),
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
            if (dests.size > 1 && uriData?.hasPaymentId() == true) {
                Toast.makeText(
                    this,
                    getString(R.string.paymentid_paytomany),
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
        }
        return true
    }

    private fun destsHasPaymentId(): Boolean {
        val dests = rawDests
        for (dest in dests) {
            val address = dest.component1()
            val uriData = UriData.parse(address) ?: return false
            if (uriData.hasPaymentId()) return true
        }
        return false
    }

    private fun addOutput(initial: Boolean, address: String? = null, amount: String? = null) {
        val index = destCount
        val entryView =
            layoutInflater.inflate(R.layout.recycler_view_item_destination, null) as ConstraintLayout
        val removeOutputImageButton =
            entryView.findViewById<ImageButton>(R.id.remove_output_imagebutton)
        val addressField = entryView.findViewById<EditText>(R.id.address_edittext)
        val amountField = entryView.findViewById<EditText>(R.id.amount_edittext)
        val donateTextView = entryView.findViewById<TextView>(R.id.donate_label)
        val donatingTextView = entryView.findViewById<TextView>(R.id.donating_label)
        donateTextView.setOnClickListener {
            addressField.setText(
                Constants.DONATE_ADDRESS
            )
        }
        donatingTextView.setOnClickListener {
            addressField.setText("")
        }
        address?.let { addressField.setText(it) }
        amount?.let { amountField.setText(it) }
        val activity = this
        addressField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                val currentOutputs: Int = destCount
                val uriData = UriData.parse(s.toString())
                if (uriData != null) {
                    // we have valid address
                    val hasPaymentId = uriData.hasPaymentId()
                    if (currentOutputs > 1 && hasPaymentId) {
                        // multiple outputs when pasting/editing in integrated address. this is not allowed
                        Toast.makeText(
                            activity,
                            getString(R.string.paymentid_paytomany),
                            Toast.LENGTH_SHORT
                        ).show()
                        addressField.text = null
                    } else if (currentOutputs == 1 && hasPaymentId) {
                        // show add output button: we are sending to integrated address
                        viewModel.setShowAddOutputButton(false)
                    }
                } else if (currentOutputs == 1 && !isSendAll) {
                    // when send-all is false and this is our only dest and address is invalid, then show add output button
                    viewModel.setShowAddOutputButton(true)
                }

                if (s.toString() == Constants.DONATE_ADDRESS) {
                    donateTextView.visibility = View.INVISIBLE
                    donatingTextView.visibility = View.VISIBLE
                } else {
                    donateTextView.visibility = View.VISIBLE
                    donatingTextView.visibility = View.INVISIBLE
                }
            }
        })
        entryView.findViewById<View>(R.id.paste_amount_imagebutton)
            .setOnClickListener {
                val clipboard = Helper.getClipBoardText(this)
                if (clipboard != null) {
                    pasteAddress(entryView, clipboard, true)
                }
            }
        entryView.findViewById<View>(R.id.paste_address_imagebutton)
            .setOnClickListener {
                val clipboard = Helper.getClipBoardText(this)
                if (clipboard != null) {
                    pasteAddress(entryView, clipboard, false)
                }
            }
        entryView.findViewById<View>(R.id.scan_address_imagebutton)
            .setOnClickListener { onScan(index) }
        if (initial) {
            removeOutputImageButton.visibility = View.INVISIBLE
        } else {
            removeOutputImageButton.setOnClickListener {
                val currentCount = destCount
                if (currentCount > 1) {
                    if (currentCount == 2) {
                        sendMaxButton.visibility = View.VISIBLE
                    }
                    destList.removeView(entryView)
                }
            }
        }
        destList.addView(entryView)
    }

    private val destCount: Int
        get() = destList.childCount
    private val rawDests: List<Pair<String, String>>
        get() {
            val destinations = mutableListOf<Pair<String, String>>()
            for (i in 0 until destCount) {
                val entryView = getDestView(i)
                val amountField = entryView.findViewById<EditText>(R.id.amount_edittext)
                val addressField = entryView.findViewById<EditText>(R.id.address_edittext)
                val amount = amountField.text.toString().trim { it <= ' ' }
                val address = addressField.text.toString().trim { it <= ' ' }
                destinations.add(Pair<String, String>(address, amount))
            }
            return destinations
        }
    private val isSendAll: Boolean
        get() = viewModel.sendingMax.value ?: false

    private fun getDestView(pos: Int): ConstraintLayout {
        return destList.getChildAt(pos) as ConstraintLayout
    }

    private fun getAddressField(pos: Int): EditText {
        return getDestView(pos).findViewById<View>(R.id.address_edittext) as EditText
    }

    private fun unprepareMaxSend() {
        val entryView = getDestView(0)
        entryView.findViewById<View>(R.id.sending_all_textview).visibility = View.INVISIBLE
        entryView.findViewById<View>(R.id.amount_edittext).visibility =
            View.VISIBLE
    }

    private fun prepareOutputsForMaxSend() {
        val entryView = getDestView(0)
        entryView.findViewById<View>(R.id.sending_all_textview).visibility = View.VISIBLE
        entryView.findViewById<View>(R.id.amount_edittext).visibility =
            View.INVISIBLE
    }

    private fun showConfirmationLayout(show: Boolean) {
        if (show) {
            destList.visibility = View.GONE
            setAddOutputButtonVisibility(View.GONE)
            sendMaxButton.visibility = View.GONE
            createButton.visibility = View.GONE
            feeRadioGroup.visibility = View.GONE
            feeRadioGroupLabelTextView.visibility = View.GONE
            sendTxSlider.visibility = View.VISIBLE
            feeTextView.visibility = View.VISIBLE
            addressTextView.visibility = View.VISIBLE
            amountTextView.visibility = View.VISIBLE
        } else {
            destList.visibility = View.VISIBLE
            setAddOutputButtonVisibility(View.VISIBLE)
            sendMaxButton.visibility = View.VISIBLE
            createButton.visibility = View.VISIBLE
            if (PreferenceUtils.isAllowFeeOverride(this)) {
                feeRadioGroup.visibility = View.VISIBLE
                feeRadioGroupLabelTextView.visibility = View.VISIBLE
            } else {
                feeRadioGroup.visibility = View.GONE
                feeRadioGroupLabelTextView.visibility = View.GONE
            }
            sendTxSlider.visibility = View.GONE
            feeTextView.visibility = View.GONE
            addressTextView.visibility = View.GONE
            addressResolvedFromTextView.visibility = View.GONE
            amountTextView.visibility = View.GONE
        }
    }

    private fun onScan(index: Int) {
        currentEntryIndex = index
        if (Helper.getCameraPermission(this, cameraPermissionsLauncher)) {
            val options = ScanOptions()
            options.setBeepEnabled(false)
            options.setOrientationLocked(true)
            options.setDesiredBarcodeFormats(listOf(Intents.Scan.QR_CODE_MODE))
            options.addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
            qrCodeLauncher.launch(options)
        }
    }

    private fun pasteAddress(
        entryView: ConstraintLayout,
        clipboard: String,
        pastingAmount: Boolean
    ) {
        if (pastingAmount) {
            try {
                clipboard.toDouble()
                setAmount(entryView, clipboard)
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    getString(R.string.toast_send_amount_invalid),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }
        val uriData = UriData.parse(clipboard)
        if (uriData != null) {
            val currentOutputs = destCount
            if (currentOutputs > 1 && uriData.hasPaymentId()) {
                Toast.makeText(
                    this,
                    getString(R.string.paymentid_paytomany),
                    Toast.LENGTH_SHORT
                ).show()
                return
            } else if (currentOutputs == 1 && uriData.hasPaymentId()) {
                viewModel.setShowAddOutputButton(false)
            }
            val addressField = entryView.findViewById<EditText>(R.id.address_edittext)
            addressField.setText(uriData.address)
            if (uriData.hasAmount()) {
                setAmount(entryView, uriData.amount)
            }
        } else {
            Toast.makeText(this, getString(R.string.toast_send_address_invalid), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun setAmount(entryView: ConstraintLayout, amount: String?) {
        sendMaxButton.isEnabled = false
        val amountField = entryView.findViewById<EditText>(R.id.amount_edittext)
        amountField.setText(amount)
    }

    private fun createTx(
        destinations: List<Pair<String, String>>,
        sendAll: Boolean,
        feePriority: PendingTransaction.Priority,
        enotes: ArrayList<String> = ArrayList()
    ) {
        if (sendAll) {
            walletService?.createSweepTransaction(destinations[0].first, feePriority, enotes)
        } else {
            val transactionDestinations = destinations.map { TransactionDestinationWithAmount(it.first, Wallet.getAmountFromString(it.second)) }
            walletService?.createTransaction(transactionDestinations, feePriority, enotes)
        }
    }

    private fun sendTx(pendingTx: PendingTransaction) {
        walletService?.sendTransaction(pendingTx)
    }

    override fun onTransactionCreated(pendingTransaction: PendingTransaction, destination: PendingTransactionDestination) {
        if (pendingTransaction.status === PendingTransaction.Status.Status_Ok) {
            viewModel.setPendingTransaction(Pair(pendingTransaction, destination))
        } else {
            runOnUiThread {
                createButton.isEnabled = true
                sendMaxButton.isEnabled = true
                if (pendingTransaction.getErrorString() != null) Toast.makeText(
                    this,
                    getString(R.string.error_creating_tx, pendingTransaction.getErrorString()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onTransactionSent(pendingTransaction: PendingTransaction, success: Boolean) {
        runOnUiThread {
            if (success) {
                Toast.makeText(this, getString(R.string.sent_tx), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                sendTxSlider.resetSlider()
                Toast.makeText(this, getString(R.string.toast_error_sending_tx), Toast.LENGTH_SHORT).show()
                Timber.e("Transaction creation failed with status ${pendingTransaction.status}")
            }
        }
    }

    private fun setAddOutputButtonVisibility(visibility: Int) {
        addOutputImageView.visibility = visibility
    }
}

class SendViewModel : ViewModel() {
    private val _sendingMax = MutableLiveData(false)
    private val _showAddOutputButton = MutableLiveData(true)
    private val _enotes = MutableLiveData<ArrayList<String>>(ArrayList())
    private val _pendingTransaction = MutableLiveData<Pair<PendingTransaction, PendingTransactionDestination>?>(null)
    var sendingMax: LiveData<Boolean?> = _sendingMax
    var showAddOutputButton: LiveData<Boolean?> = _showAddOutputButton
    var enotes: LiveData<ArrayList<String>> = _enotes
    var pendingTransaction: LiveData<Pair<PendingTransaction, PendingTransactionDestination>?> = _pendingTransaction
    fun setSendingMax(value: Boolean) {
        _sendingMax.value = value
        setShowAddOutputButton(!value)
    }

    fun setShowAddOutputButton(value: Boolean) {
        _showAddOutputButton.value = value
    }

    fun setEnotes(value: ArrayList<String>) {
        _enotes.value = value
    }

    fun setPendingTransaction(pair: Pair<PendingTransaction, PendingTransactionDestination>?) {
        _pendingTransaction.postValue(pair)
    }
}