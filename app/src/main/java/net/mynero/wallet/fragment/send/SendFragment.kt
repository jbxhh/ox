package net.mynero.wallet.fragment.send

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
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.ncorti.slidetoact.SlideToActView
import com.ncorti.slidetoact.SlideToActView.OnSlideCompleteListener
import net.mynero.wallet.MoneroApplication
import net.mynero.wallet.R
import net.mynero.wallet.model.PendingTransaction
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.service.BalanceService
import net.mynero.wallet.service.TxService
import net.mynero.wallet.util.Helper
import net.mynero.wallet.util.UriData

class SendFragment : Fragment() {
    var priority: PendingTransaction.Priority = PendingTransaction.Priority.Priority_Low
    private var mViewModel: SendViewModel? = null
    private var sendMaxButton: Button? = null
    private var addOutputImageView: ImageButton? = null
    private var destList: LinearLayout? = null
    private var inflater: LayoutInflater? = null
    private var createButton: Button? = null
    private var sendTxSlider: SlideToActView? = null
    private var feeRadioGroup: RadioGroup? = null
    private var feeRadioGroupLabelTextView: TextView? = null
    private var feeTextView: TextView? = null
    private var addressTextView: TextView? = null
    private var amountTextView: TextView? = null
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
            Toast.makeText(activity, getString(R.string.no_camera_permission), Toast.LENGTH_SHORT)
                .show()
            currentEntryIndex = -1
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_send, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mViewModel = ViewModelProvider(this)[SendViewModel::class.java]
        sendMaxButton = view.findViewById(R.id.send_max_button)
        addOutputImageView = view.findViewById(R.id.add_output_button)
        destList = view.findViewById(R.id.transaction_destination_list)
        createButton = view.findViewById(R.id.create_tx_button)
        feeRadioGroup = view.findViewById(R.id.tx_fee_radiogroup)
        feeTextView = view.findViewById(R.id.fee_textview)
        sendTxSlider = view.findViewById(R.id.send_tx_slider)
        addressTextView = view.findViewById(R.id.address_pending_textview)
        amountTextView = view.findViewById(R.id.amount_pending_textview)
        feeRadioGroup = view.findViewById(R.id.tx_fee_radiogroup)
        feeRadioGroupLabelTextView = view.findViewById(R.id.tx_fee_radiogroup_label_textview)
        inflater = activity?.layoutInflater
        bindListeners()
        bindObservers()
        init()
    }

    private fun init() {
        addOutput(true)
    }

    private fun bindListeners() {
        feeRadioGroup?.check(R.id.low_fee_radiobutton)
        priority = PendingTransaction.Priority.Priority_Low
        feeRadioGroup?.setOnCheckedChangeListener { _: RadioGroup?, i: Int ->
            when (i) {
                R.id.low_fee_radiobutton -> priority = PendingTransaction.Priority.Priority_Low
                R.id.med_fee_radiobutton -> priority = PendingTransaction.Priority.Priority_Medium
                R.id.high_fee_radiobutton -> priority = PendingTransaction.Priority.Priority_High
            }
        }
        addOutputImageView?.setOnClickListener {
            sendMaxButton?.visibility = View.GONE
            val outputCount = destCount
            if (outputCount < 8) {
                addOutput(false)
            } else {
                Toast.makeText(
                    activity,
                    getString(R.string.max_outputs_allowed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        sendMaxButton?.setOnClickListener { mViewModel?.setSendingMax(!isSendAll) }
        createButton?.setOnClickListener {
            val outputsValid = checkDestsValidity(isSendAll)
            if (outputsValid) {
                Toast.makeText(activity, getString(R.string.creating_tx), Toast.LENGTH_SHORT).show()
                createButton?.isEnabled = false
                sendMaxButton?.isEnabled = false
                createTx(rawDests, isSendAll, priority)
            } else {
                Toast.makeText(
                    activity,
                    getString(R.string.creating_tx_failed_invalid_outputs),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        sendTxSlider?.onSlideCompleteListener =
            object : OnSlideCompleteListener {
                override fun onSlideComplete(view: SlideToActView) {
                    val pendingTx = mViewModel?.pendingTransaction?.value ?: return
                    Toast.makeText(activity, getString(R.string.sending_tx), Toast.LENGTH_SHORT)
                        .show()
                    sendTx(pendingTx)
                }
            }
    }

    private fun checkDestsValidity(sendAll: Boolean): Boolean {
        val dests = rawDests
        for (dest in dests) {
            val address = dest.component1()
            val amount = dest.component2()
            if (!sendAll) {
                if (amount.isEmpty()) {
                    Toast.makeText(
                        activity,
                        getString(R.string.send_amount_empty),
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }
                val amountRaw = Wallet.getAmountFromString(amount)
                val balance = BalanceService.instance?.unlockedBalanceRaw ?: 0
                if (amountRaw >= balance || amountRaw <= 0) {
                    Toast.makeText(
                        activity,
                        getString(R.string.send_amount_invalid),
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }
            } else if (dests.size > 1) {
                Toast.makeText(
                    activity,
                    getString(R.string.send_amount_invalid_sendall_paytomany),
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
            val uriData = UriData.parse(address)
            val isValidAddress = uriData != null
            if (!isValidAddress) {
                Toast.makeText(
                    activity,
                    getString(R.string.send_address_invalid),
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
            if (dests.size > 1 && uriData?.hasPaymentId() == true) {
                Toast.makeText(
                    activity,
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
        mViewModel?.sendingMax?.observe(viewLifecycleOwner) { sendingMax: Boolean? ->
            if (mViewModel?.pendingTransaction?.value == null) {
                if (sendingMax == true) {
                    prepareOutputsForMaxSend()
                    sendMaxButton?.text = getText(R.string.undo)
                } else {
                    unprepareMaxSend()
                    sendMaxButton?.text = getText(R.string.send_max)
                }
            }
        }
        mViewModel?.showAddOutputButton?.observe(viewLifecycleOwner) { show: Boolean? ->
            setAddOutputButtonVisibility(
                if (show == true && !destsHasPaymentId()) View.VISIBLE else View.INVISIBLE
            )
        }
        mViewModel?.pendingTransaction?.observe(viewLifecycleOwner) { pendingTx: PendingTransaction? ->
            showConfirmationLayout(pendingTx != null)
            if (pendingTx != null) {
                val address = if (destCount == 1) getAddressField(0).text.toString() else "Multiple"
                addressTextView?.text = getString(R.string.tx_address_text, address)
                amountTextView?.text =
                    getString(
                        R.string.tx_amount_text,
                        Helper.getDisplayAmount(pendingTx.getAmount())
                    )
                feeTextView?.text =
                    getString(R.string.tx_fee_text, Helper.getDisplayAmount(pendingTx.getFee()))
            }
        }
    }

    private fun addOutput(initial: Boolean) {
        if (inflater != null) {
            val index = destCount
            val entryView =
                inflater?.inflate(R.layout.transaction_output_item, null) as ConstraintLayout
            val removeOutputImageButton =
                entryView.findViewById<ImageButton>(R.id.remove_output_imagebutton)
            val addressField = entryView.findViewById<EditText>(R.id.address_edittext)
            addressField.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int
                ) {
                }

                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                override fun afterTextChanged(editable: Editable) {
                    val currentOutputs: Int = destCount
                    val uriData = UriData.parse(editable.toString())
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
                            mViewModel?.setShowAddOutputButton(false)
                        }
                    } else if (currentOutputs == 1 && !isSendAll) {
                        // when send-all is false and this is our only dest and address is invalid, then show add output button
                        mViewModel?.setShowAddOutputButton(true)
                    }
                }
            })
            entryView.findViewById<View>(R.id.paste_amount_imagebutton)
                .setOnClickListener {
                    val ctx = context
                    if (ctx != null) {
                        val clipboard = Helper.getClipBoardText(ctx)
                        if (clipboard != null) {
                            pasteAddress(entryView, clipboard, true)
                        }
                    }
                }
            entryView.findViewById<View>(R.id.paste_address_imagebutton)
                .setOnClickListener {
                    val ctx = context
                    if (ctx != null) {
                        val clipboard = Helper.getClipBoardText(ctx)
                        if (clipboard != null) {
                            pasteAddress(entryView, clipboard, false)
                        }
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
                            sendMaxButton?.visibility = View.VISIBLE
                        }
                        destList?.removeView(entryView)
                    }
                }
            }
            destList?.addView(entryView)
        }
    }

    private val destCount: Int
        get() = destList?.childCount ?: -1
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
        get() = mViewModel?.sendingMax?.value ?: false

    private fun getDestView(pos: Int): ConstraintLayout {
        return destList?.getChildAt(pos) as ConstraintLayout
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
            destList?.visibility = View.GONE
            setAddOutputButtonVisibility(View.GONE)
            sendMaxButton?.visibility = View.GONE
            createButton?.visibility = View.GONE
            feeRadioGroup?.visibility = View.GONE
            feeRadioGroupLabelTextView?.visibility = View.GONE
            sendTxSlider?.visibility = View.VISIBLE
            feeTextView?.visibility = View.VISIBLE
            addressTextView?.visibility = View.VISIBLE
            amountTextView?.visibility = View.VISIBLE
        } else {
            destList?.visibility = View.VISIBLE
            setAddOutputButtonVisibility(View.VISIBLE)
            sendMaxButton?.visibility = View.VISIBLE
            createButton?.visibility = View.VISIBLE
            feeRadioGroup?.visibility = View.VISIBLE
            feeRadioGroupLabelTextView?.visibility = View.VISIBLE
            sendTxSlider?.visibility = View.GONE
            feeTextView?.visibility = View.GONE
            addressTextView?.visibility = View.GONE
            amountTextView?.visibility = View.GONE
        }
    }

    private fun onScan(index: Int) {
        currentEntryIndex = index
        if (activity?.let { Helper.getCameraPermission(it, cameraPermissionsLauncher) } == true) {
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
                    activity,
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
                    activity,
                    getString(R.string.paymentid_paytomany),
                    Toast.LENGTH_SHORT
                ).show()
                return
            } else if (currentOutputs == 1 && uriData.hasPaymentId()) {
                mViewModel?.setShowAddOutputButton(false)
            }
            val addressField = entryView.findViewById<EditText>(R.id.address_edittext)
            addressField.setText(uriData.address)
            if (uriData.hasAmount()) {
                setAmount(entryView, uriData.amount)
            }
        } else {
            Toast.makeText(activity, getString(R.string.send_address_invalid), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun setAmount(entryView: ConstraintLayout, amount: String?) {
        sendMaxButton?.isEnabled = false
        val amountField = entryView.findViewById<EditText>(R.id.amount_edittext)
        amountField.setText(amount)
    }

    private fun createTx(
        dests: List<Pair<String, String>>,
        sendAll: Boolean,
        feePriority: PendingTransaction.Priority
    ) {
        (activity?.application as MoneroApplication).executor?.execute {
            try {
                val pendingTx =
                    TxService.instance?.createTx(dests, sendAll, feePriority, ArrayList())
                if (pendingTx != null && pendingTx.status === PendingTransaction.Status.Status_Ok) {
                    mViewModel?.setPendingTransaction(pendingTx)
                } else {
                    val activity: Activity? = activity
                    if (activity != null && pendingTx != null) {
                        activity.runOnUiThread(Runnable {
                            createButton?.isEnabled = true
                            sendMaxButton?.isEnabled = true
                            if (pendingTx.getErrorString() != null) Toast.makeText(
                                activity,
                                getString(R.string.error_creating_tx, pendingTx.getErrorString()),
                                Toast.LENGTH_SHORT
                            ).show()
                        })
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val activity: Activity? = activity
                activity?.runOnUiThread {
                    createButton?.isEnabled = true
                    sendMaxButton?.isEnabled = true
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
        (activity?.application as MoneroApplication).executor?.execute {
            val success = TxService.instance?.sendTx(pendingTx)
            val activity: Activity? = activity
            activity?.runOnUiThread {
                if (success == true) {
                    Toast.makeText(getActivity(), getString(R.string.sent_tx), Toast.LENGTH_SHORT)
                        .show()
                    activity.onBackPressed()
                } else {
                    sendTxSlider?.resetSlider()
                    Toast.makeText(
                        getActivity(),
                        getString(R.string.error_sending_tx),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        }
    }

    private fun setAddOutputButtonVisibility(visibility: Int) {
        addOutputImageView?.visibility = visibility
    }
}