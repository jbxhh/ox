package net.mynero.wallet.fragment.dialog

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import net.mynero.wallet.MoneroApplication
import net.mynero.wallet.R
import net.mynero.wallet.model.PendingTransaction
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.model.Wallet.Companion.getAmountFromString
import net.mynero.wallet.model.Wallet.Companion.isAddressValid
import net.mynero.wallet.service.BalanceService
import net.mynero.wallet.service.TxService
import net.mynero.wallet.service.UTXOService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.Helper
import net.mynero.wallet.util.Helper.getCameraPermission
import net.mynero.wallet.util.Helper.getClipBoardText
import net.mynero.wallet.util.UriData
import net.mynero.wallet.util.UriData.Companion.parse

class SendBottomSheetDialog : BottomSheetDialogFragment() {
    private val _sendingMax = MutableLiveData(false)
    private val _pendingTransaction = MutableLiveData<PendingTransaction?>(null)
    var selectedUtxos = ArrayList<String>()
    private var sendingMax: LiveData<Boolean?> = _sendingMax
    private var pendingTransaction: LiveData<PendingTransaction?> = _pendingTransaction
    var uriData: UriData? = null
    private val cameraPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            onScan()
        } else {
            Toast.makeText(activity, getString(R.string.no_camera_permission), Toast.LENGTH_SHORT)
                .show()
        }
    }

    var isChurning = false
    var listener: Listener? = null
    var priority: PendingTransaction.Priority = PendingTransaction.Priority.Priority_Low
    private var addressEditText: EditText? = null
    private var amountEditText: EditText? = null
    private val barcodeLauncher = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->
        if (result.contents != null) {
            pasteAddress(result.contents)
        }
    }
    private var sendAllTextView: TextView? = null
    private var feeTextView: TextView? = null
    private var addressTextView: TextView? = null
    private var amountTextView: TextView? = null
    private var feeRadioGroupLabelTextView: TextView? = null
    private var selectedUtxosValueTextView: TextView? = null
    private var createButton: Button? = null
    private var sendButton: Button? = null
    private var sendMaxButton: Button? = null
    private var pasteAddressImageButton: ImageButton? = null
    private var scanAddressImageButton: ImageButton? = null
    private var feeRadioGroup: RadioGroup? = null
    private var donateTextView: TextView? = null
    private var donatingTextView: TextView? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.send_bottom_sheet_dialog, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pasteAddressImageButton = view.findViewById(R.id.paste_address_imagebutton)
        scanAddressImageButton = view.findViewById(R.id.scan_address_imagebutton)
        sendMaxButton = view.findViewById(R.id.send_max_button)
        addressEditText = view.findViewById(R.id.address_edittext)
        amountEditText = view.findViewById(R.id.amount_edittext)
        sendButton = view.findViewById(R.id.send_tx_button)
        createButton = view.findViewById(R.id.create_tx_button)
        sendAllTextView = view.findViewById(R.id.sending_all_textview)
        feeTextView = view.findViewById(R.id.fee_textview)
        addressTextView = view.findViewById(R.id.address_pending_textview)
        amountTextView = view.findViewById(R.id.amount_pending_textview)
        feeRadioGroup = view.findViewById(R.id.tx_fee_radiogroup)
        feeRadioGroupLabelTextView = view.findViewById(R.id.tx_fee_radiogroup_label_textview)
        selectedUtxosValueTextView = view.findViewById(R.id.selected_utxos_value_textview)
        donateTextView = view.findViewById(R.id.donate_label_textview)
        donatingTextView = view.findViewById(R.id.donating_label_textview)
        donateTextView?.setOnClickListener {
            addressEditText?.setText(
                Constants.DONATE_ADDRESS
            )
        }
        donatingTextView?.setOnClickListener {
            addressEditText?.setText("")
        }
        addressEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (s.toString() == Constants.DONATE_ADDRESS) {
                    donatingTextView?.visibility = View.VISIBLE
                    donateTextView?.visibility = View.INVISIBLE
                } else {
                    donatingTextView?.visibility = View.INVISIBLE
                    donateTextView?.visibility = View.VISIBLE
                }
            }
        })
        if (uriData != null) {
            addressEditText?.setText(uriData?.address)
            if (uriData?.hasAmount() == true) {
                amountEditText?.setText(uriData?.amount)
            }
        }
        if (selectedUtxos.isNotEmpty()) {
            var selectedValue: Long = 0
            val utxos = UTXOService.instance?.getUtxos() ?: return
            for (coinsInfo in utxos) {
                if (selectedUtxos.contains(coinsInfo.keyImage)) {
                    selectedValue += coinsInfo.amount
                }
            }
            val valueString = Wallet.getDisplayAmount(selectedValue)
            selectedUtxosValueTextView?.visibility = View.VISIBLE
            if (isChurning) {
                _sendingMax.postValue(true)
                sendMaxButton?.isEnabled = false
                selectedUtxosValueTextView?.text = resources.getString(
                    R.string.selected_utxos_value_churning,
                    valueString
                )
            } else {
                selectedUtxosValueTextView?.text = resources.getString(
                    R.string.selected_utxos_value,
                    valueString
                )
            }
        } else {
            selectedUtxosValueTextView?.visibility = View.GONE
        }
        bindObservers()
        bindListeners()
    }

    private fun bindObservers() {
        BalanceService.instance?.balanceInfo?.observe(viewLifecycleOwner) { balanceInfo ->
            createButton?.isEnabled = balanceInfo?.rawUnlocked != 0L
            if (!isChurning) {
                sendMaxButton?.isEnabled = balanceInfo?.rawUnlocked != 0L
            }
        }
        sendingMax.observe(viewLifecycleOwner) { sendingMax: Boolean? ->
            if (pendingTransaction.value == null) {
                if (sendingMax == true) {
                    amountEditText?.visibility = View.INVISIBLE
                    sendAllTextView?.visibility = View.VISIBLE
                    sendMaxButton?.text = getText(R.string.undo)
                } else {
                    amountEditText?.visibility = View.VISIBLE
                    sendAllTextView?.visibility = View.GONE
                    sendMaxButton?.text = getText(R.string.send_max)
                }
            }
        }
        pendingTransaction.observe(viewLifecycleOwner) { pendingTx: PendingTransaction? ->
            showConfirmationLayout(pendingTx != null)
            if (pendingTx != null) {
                val address = addressEditText?.text.toString()
                addressTextView?.text = getString(R.string.tx_address_text, address)
                amountTextView?.text = getString(
                    R.string.tx_amount_text,
                    Helper.getDisplayAmount(pendingTx.getAmount())
                )
                feeTextView?.text =
                    getString(R.string.tx_fee_text, Helper.getDisplayAmount(pendingTx.getFee()))
            }
        }
    }

    private fun bindListeners() {
        feeRadioGroup?.check(R.id.low_fee_radiobutton)
        feeRadioGroup?.setOnCheckedChangeListener { _: RadioGroup?, i: Int ->
            when (i) {
                R.id.low_fee_radiobutton -> priority = PendingTransaction.Priority.Priority_Low
                R.id.med_fee_radiobutton -> priority = PendingTransaction.Priority.Priority_Medium
                R.id.high_fee_radiobutton -> priority = PendingTransaction.Priority.Priority_High
            }
        }
        pasteAddressImageButton?.setOnClickListener {
            val ctx = context
            if (ctx != null) {
                val clipboard = getClipBoardText(ctx)
                clipboard?.let { pasteAddress(it) }
            }
        }
        scanAddressImageButton?.setOnClickListener { onScan() }
        sendMaxButton?.setOnClickListener {
            val currentValue = if (sendingMax.value != null) sendingMax.value else false
            _sendingMax.postValue(currentValue == false)
        }
        createButton?.setOnClickListener {
            val activity = activity
            if (activity != null) {
                val sendAll = sendingMax.value ?: false
                val address = addressEditText?.text.toString().trim { it <= ' ' }
                val amount = amountEditText?.text.toString().trim { it <= ' ' }
                val validAddress = isAddressValid(address)
                if (validAddress && (amount.isNotEmpty() || sendAll)) {
                    val amountRaw = getAmountFromString(amount)
                    val balance = BalanceService.instance?.unlockedBalanceRaw ?: 0
                    if ((amountRaw >= balance || amountRaw <= 0) && !sendAll) {
                        Toast.makeText(
                            activity,
                            getString(R.string.send_amount_invalid),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                    Toast.makeText(activity, getString(R.string.creating_tx), Toast.LENGTH_SHORT)
                        .show()
                    createButton?.isEnabled = false
                    createTx(address, amount, sendAll, priority)
                } else if (!validAddress) {
                    Toast.makeText(
                        activity,
                        getString(R.string.send_address_invalid),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        activity,
                        getString(R.string.send_amount_empty),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        sendButton?.setOnClickListener {
            val pendingTx = pendingTransaction.value
            if (pendingTx != null) {
                Toast.makeText(activity, getString(R.string.sending_tx), Toast.LENGTH_SHORT).show()
                sendButton?.isEnabled = false
                sendTx(pendingTx)
            }
        }
    }

    private fun onScan() {
        if (activity?.let { getCameraPermission(it, cameraPermissionsLauncher) } == true) {
            val options = ScanOptions()
            options.setBeepEnabled(false)
            options.setOrientationLocked(true)
            options.setDesiredBarcodeFormats(listOf(Intents.Scan.QR_CODE_MODE))
            options.addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
            barcodeLauncher.launch(options)
        }
    }

    private fun sendTx(pendingTx: PendingTransaction) {
        val activity: Activity? = activity
        if (activity != null) {
            (activity.application as MoneroApplication).executor?.execute {
                val success = TxService.instance?.sendTx(pendingTx)
                activity.runOnUiThread(Runnable {
                    if (success == true) {
                        Toast.makeText(activity, getString(R.string.sent_tx), Toast.LENGTH_SHORT)
                            .show()
                        if (listener != null) {
                            listener?.onSentTransaction()
                        }
                        dismiss()
                    } else {
                        sendButton?.isEnabled = true
                        Toast.makeText(
                            activity,
                            getString(R.string.error_sending_tx),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            }
        }
    }

    private fun createTx(
        address: String,
        amount: String,
        sendAll: Boolean,
        feePriority: PendingTransaction.Priority
    ) {
        val activity: Activity? = activity
        if (activity != null) {
            (activity.application as MoneroApplication).executor?.execute {
                try {
                    val pendingTx = TxService.instance?.createTx(
                        address,
                        amount,
                        sendAll,
                        feePriority,
                        selectedUtxos
                    )
                    if (pendingTx != null && pendingTx.status === PendingTransaction.Status.Status_Ok) {
                        _pendingTransaction.postValue(pendingTx)
                    } else {
                        activity.runOnUiThread(Runnable {
                            createButton?.isEnabled = true
                            if (pendingTx != null) {
                                Toast.makeText(
                                    activity,
                                    getString(
                                        R.string.error_creating_tx,
                                        pendingTx.getErrorString()
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                    }
                } catch (e: Exception) {
                    activity.runOnUiThread(Runnable {
                        createButton?.isEnabled = true
                        Toast.makeText(activity, e.message, Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
    }

    private fun showConfirmationLayout(show: Boolean) {
        if (show) {
            sendButton?.visibility = View.VISIBLE
            addressEditText?.visibility = View.GONE
            amountEditText?.visibility = View.GONE
            sendAllTextView?.visibility = View.GONE
            createButton?.visibility = View.GONE
            sendMaxButton?.visibility = View.GONE
            pasteAddressImageButton?.visibility = View.GONE
            scanAddressImageButton?.visibility = View.GONE
            feeTextView?.visibility = View.VISIBLE
            addressTextView?.visibility = View.VISIBLE
            amountTextView?.visibility = View.VISIBLE
            selectedUtxosValueTextView?.visibility = View.GONE
            feeRadioGroup?.visibility = View.GONE
            feeRadioGroupLabelTextView?.visibility = View.GONE
            donateTextView?.visibility = View.GONE
        } else {
            sendButton?.visibility = View.GONE
            addressEditText?.visibility = View.VISIBLE
            amountEditText?.visibility =
                if (java.lang.Boolean.TRUE == sendingMax.value) View.GONE else View.VISIBLE
            sendAllTextView?.visibility =
                if (java.lang.Boolean.TRUE == sendingMax.value) View.VISIBLE else View.GONE
            createButton?.visibility = View.VISIBLE
            sendMaxButton?.visibility = View.VISIBLE
            pasteAddressImageButton?.visibility = View.VISIBLE
            scanAddressImageButton?.visibility = View.VISIBLE
            feeTextView?.visibility = View.GONE
            addressTextView?.visibility = View.GONE
            amountTextView?.visibility = View.GONE
            if (selectedUtxos.isNotEmpty()) {
                selectedUtxosValueTextView?.visibility = View.VISIBLE
            }
            feeRadioGroup?.visibility = View.VISIBLE
            feeRadioGroupLabelTextView?.visibility = View.VISIBLE
            donateTextView?.visibility = View.VISIBLE
        }
    }

    private fun pasteAddress(address: String) {
        val uriData = parse(address)
        if (uriData != null) {
            addressEditText?.setText(uriData.address)
            if (uriData.hasAmount()) {
                amountEditText?.setText(uriData.amount)
            }
        } else {
            Toast.makeText(activity, getString(R.string.send_address_invalid), Toast.LENGTH_SHORT)
                .show()
        }
    }

    interface Listener {
        fun onSentTransaction()
    }
}