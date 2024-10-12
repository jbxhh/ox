package net.mynero.wallet

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.ncorti.slidetoact.SlideToActView
import com.ncorti.slidetoact.SlideToActView.OnSlideCompleteListener
import net.mynero.wallet.model.PendingTransaction
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.service.BalanceService
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.TxService
import net.mynero.wallet.service.UTXOService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.Helper
import net.mynero.wallet.util.UriData

class SendActivity : AppCompatActivity() {

    var priority: PendingTransaction.Priority = PendingTransaction.Priority.Priority_Default
    private lateinit var mViewModel: SendViewModel
    private lateinit var sendMaxButton: Button
    private lateinit var addOutputImageView: ImageButton
    private lateinit var destList: LinearLayout
    private lateinit var createButton: Button
    private lateinit var sendTxSlider: SlideToActView
    private lateinit var feeRadioGroup: RadioGroup
    private lateinit var feeRadioGroupLabelTextView: TextView
    private lateinit var feeTextView: TextView
    private lateinit var addressTextView: TextView
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

        mViewModel = ViewModelProvider(this)[SendViewModel::class.java]
        sendMaxButton = findViewById(R.id.send_max_button)
        addOutputImageView = findViewById(R.id.add_output_button)
        destList = findViewById(R.id.transaction_destination_list)
        createButton = findViewById(R.id.create_tx_button)
        feeRadioGroup = findViewById(R.id.tx_fee_radiogroup)
        feeTextView = findViewById(R.id.fee_textview)
        sendTxSlider = findViewById(R.id.send_tx_slider)
        addressTextView = findViewById(R.id.address_pending_textview)
        amountTextView = findViewById(R.id.amount_pending_textview)
        feeRadioGroup = findViewById(R.id.tx_fee_radiogroup)
        feeRadioGroupLabelTextView = findViewById(R.id.tx_fee_radiogroup_label_textview)
        selectedUtxosValueTextView = findViewById(R.id.selected_utxos_value_textview)

        bindListeners()
        bindObservers()
        init()

        val selectedUtxos = mViewModel.utxos.value
        if (selectedUtxos?.isNotEmpty() == true) {
            var selectedValue: Long = 0
            val utxos = UTXOService.instance?.getUtxos() ?: return
            for (coinsInfo in utxos) {
                if (selectedUtxos.contains(coinsInfo.keyImage)) {
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

    private fun init() {
        val address = intent.getStringExtra(Constants.EXTRA_SEND_ADDRESS)
        val amount = intent.getStringExtra(Constants.EXTRA_SEND_AMOUNT)
        val max = intent.getBooleanExtra(Constants.EXTRA_SEND_MAX, false)
        val utxos = intent.getStringArrayListExtra(Constants.EXTRA_SEND_UTXOS) ?: ArrayList()
        addOutput(true, address, amount)
        mViewModel.setSendingMax(max)
        mViewModel.setUtxos(utxos)
    }

    private fun bindListeners() {
        feeRadioGroup.check(R.id.default_fee_radiobutton)
        priority = PendingTransaction.Priority.Priority_Default
        feeRadioGroup.setOnCheckedChangeListener { _: RadioGroup?, i: Int ->
            when (i) {
                R.id.default_fee_radiobutton -> priority = PendingTransaction.Priority.Priority_Default
                R.id.medium_fee_radiobutton -> priority = PendingTransaction.Priority.Priority_Medium
                R.id.high_fee_radiobutton -> priority = PendingTransaction.Priority.Priority_High
            }
        }
        if (PrefService.instance.getBoolean(Constants.PREF_ALLOW_FEE_OVERRIDE, false)) {
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
        sendMaxButton.setOnClickListener { mViewModel.setSendingMax(!isSendAll) }
        createButton.setOnClickListener {
            val outputsValid = checkDestsValidity(isSendAll)
            if (outputsValid) {
                Toast.makeText(this, getString(R.string.creating_tx), Toast.LENGTH_SHORT).show()
                createButton.isEnabled = false
                sendMaxButton.isEnabled = false
                createTx(rawDests, isSendAll, priority, mViewModel.utxos.value ?: ArrayList())
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

    private fun confirmSlider() {
        val pendingTx = mViewModel.pendingTransaction.value ?: return
        Toast.makeText(this, getString(R.string.sending_tx), Toast.LENGTH_SHORT)
            .show()
        sendTx(pendingTx)
    }

    private fun checkDestsValidity(sendAll: Boolean): Boolean {
        val dests = rawDests
        for (dest in dests) {
            val address = dest.component1()
            val amount = dest.component2()
            if (!sendAll) {
                if (amount.isEmpty()) {
                    Toast.makeText(
                        this,
                        getString(R.string.send_amount_empty),
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }
                val amountRaw = Wallet.getAmountFromString(amount)
                val balance = BalanceService.instance?.unlockedBalanceRaw ?: 0
                if (amountRaw >= balance || amountRaw <= 0) {
                    Toast.makeText(
                        this,
                        getString(R.string.send_amount_invalid),
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }
            } else if (dests.size > 1) {
                Toast.makeText(
                    this,
                    getString(R.string.send_amount_invalid_sendall_paytomany),
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
            val uriData = UriData.parse(address)
            val isValidAddress = uriData != null
            if (!isValidAddress) {
                Toast.makeText(
                    this,
                    getString(R.string.send_address_invalid),
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

    private fun bindObservers() {
        mViewModel.sendingMax.observe(this) { sendingMax: Boolean? ->
            if (mViewModel.pendingTransaction.value == null) {
                if (sendingMax == true) {
                    prepareOutputsForMaxSend()
                    sendMaxButton.text = getText(R.string.undo)
                } else {
                    unprepareMaxSend()
                    sendMaxButton.text = getText(R.string.send_max)
                }
            }
        }
        mViewModel.showAddOutputButton.observe(this) { show: Boolean? ->
            setAddOutputButtonVisibility(
                if (show == true && !destsHasPaymentId()) View.VISIBLE else View.INVISIBLE
            )
        }
        mViewModel.pendingTransaction.observe(this) { pendingTx: PendingTransaction? ->
            showConfirmationLayout(pendingTx != null)
            if (pendingTx != null) {
                val address = if (destCount == 1) getAddressField(0).text.toString() else "Multiple"
                addressTextView.text = getString(R.string.tx_address_text, address)
                amountTextView.text =
                    getString(
                        R.string.tx_amount_text,
                        Helper.getDisplayAmount(pendingTx.getAmount())
                    )
                feeTextView.text =
                    getString(R.string.tx_fee_text, Helper.getDisplayAmount(pendingTx.getFee()))
            }
        }
    }

    private fun addOutput(initial: Boolean, address: String? = null, amount: String? = null) {
        val index = destCount
        val entryView =
            layoutInflater.inflate(R.layout.transaction_output_item, null) as ConstraintLayout
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
                        mViewModel.setShowAddOutputButton(false)
                    }
                } else if (currentOutputs == 1 && !isSendAll) {
                    // when send-all is false and this is our only dest and address is invalid, then show add output button
                    mViewModel.setShowAddOutputButton(true)
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
        get() = destList.childCount ?: -1
    private val rawDests: List<Pair<String, String>>
        get() {
            val dests = ArrayList<Pair<String, String>>()
            for (i in 0 until destCount) {
                val entryView = getDestView(i)
                val amountField = entryView.findViewById<EditText>(R.id.amount_edittext)
                val addressField = entryView.findViewById<EditText>(R.id.address_edittext)
                val amount = amountField.text.toString().trim { it <= ' ' }
                val address = addressField.text.toString().trim { it <= ' ' }
                dests.add(Pair(address, amount))
            }
            return dests
        }
    private val isSendAll: Boolean
        get() = mViewModel.sendingMax.value ?: false

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
            if (PrefService.instance.getBoolean(Constants.PREF_ALLOW_FEE_OVERRIDE, false)) {
                feeRadioGroup.visibility = View.VISIBLE
                feeRadioGroupLabelTextView.visibility = View.VISIBLE
            } else {
                feeRadioGroup.visibility = View.GONE
                feeRadioGroupLabelTextView.visibility = View.GONE
            }
            sendTxSlider.visibility = View.GONE
            feeTextView.visibility = View.GONE
            addressTextView.visibility = View.GONE
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
                    getString(R.string.send_amount_invalid),
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
                mViewModel.setShowAddOutputButton(false)
            }
            val addressField = entryView.findViewById<EditText>(R.id.address_edittext)
            addressField.setText(uriData.address)
            if (uriData.hasAmount()) {
                setAmount(entryView, uriData.amount)
            }
        } else {
            Toast.makeText(this, getString(R.string.send_address_invalid), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun setAmount(entryView: ConstraintLayout, amount: String?) {
        sendMaxButton.isEnabled = false
        val amountField = entryView.findViewById<EditText>(R.id.amount_edittext)
        amountField.setText(amount)
    }

    private fun createTx(
        dests: List<Pair<String, String>>,
        sendAll: Boolean,
        feePriority: PendingTransaction.Priority,
        utxos: ArrayList<String> = ArrayList()
    ) {
        (application as MoneroApplication).executor?.execute {
            try {
                val pendingTx =
                    TxService.instance?.createTx(dests, sendAll, feePriority, utxos)
                if (pendingTx != null && pendingTx.status === PendingTransaction.Status.Status_Ok) {
                    mViewModel.setPendingTransaction(pendingTx)
                } else {
                    val activity: Activity = this
                    if (pendingTx != null) {
                        activity.runOnUiThread {
                            createButton.isEnabled = true
                            sendMaxButton.isEnabled = true
                            if (pendingTx.getErrorString() != null) Toast.makeText(
                                activity,
                                getString(R.string.error_creating_tx, pendingTx.getErrorString()),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val activity: Activity = this
                activity.runOnUiThread {
                    createButton.isEnabled = true
                    sendMaxButton.isEnabled = true
                    Toast.makeText(
                        activity,
                        getString(R.string.error_creating_tx, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun sendTx(pendingTx: PendingTransaction) {
        (application as MoneroApplication).executor?.execute {
            val success = TxService.instance?.sendTx(pendingTx)
            val activity: Activity = this
            activity.runOnUiThread {
                if (success == true) {
                    Toast.makeText(this, getString(R.string.sent_tx), Toast.LENGTH_SHORT)
                        .show()
                    activity.onBackPressed()
                } else {
                    sendTxSlider.resetSlider()
                    Toast.makeText(
                        this,
                        getString(R.string.error_sending_tx),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
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
    private val _utxos = MutableLiveData<ArrayList<String>>(ArrayList())
    private val _pendingTransaction = MutableLiveData<PendingTransaction?>(null)
    var sendingMax: LiveData<Boolean?> = _sendingMax
    var showAddOutputButton: LiveData<Boolean?> = _showAddOutputButton
    var utxos: LiveData<ArrayList<String>> = _utxos
    var pendingTransaction: LiveData<PendingTransaction?> = _pendingTransaction
    fun setSendingMax(value: Boolean) {
        _sendingMax.value = value
        setShowAddOutputButton(!value)
    }

    fun setShowAddOutputButton(value: Boolean) {
        _showAddOutputButton.value = value
    }

    fun setUtxos(value: ArrayList<String>) {
        _utxos.value = value
    }

    fun setPendingTransaction(pendingTx: PendingTransaction?) {
        _pendingTransaction.postValue(pendingTx)
    }
}