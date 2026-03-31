package org.monfluo.wallet.android.activity

import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.fragment.DialogFragmentQrCode
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.PreferenceUtils
import org.monfluo.wallet.android.util.QrCodeHelper
import org.monfluo.wallet.android.util.SimpleTextWatcher
import org.monfluo.wallet.android.util.TextHelper
import org.monfluo.wallet.android.util.UriPayment
import org.monfluo.wallet.android.util.component.ActivityBase
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

private const val ALPHA_OPAQUE: Int = 255
private const val ALPHA_HALF_TRANSPARENT: Int = 128

class ActivityPaymentRequest : ActivityBase() {
    private val viewModel: ActivityUriPaymentViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var addressQrCodeImageView: ImageView
    private lateinit var addressQrCodeIconImageView: ImageView
    private lateinit var addressTextView: TextView
    private lateinit var amountTextView: TextView
    private lateinit var recipientNameTextView: TextView
    private lateinit var descriptionTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!preInit()) {
            finish()
            return
        }
        setContentView(R.layout.activity_payment_request)

        layoutView = findViewById(R.id.payment_request_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        addressQrCodeImageView = findViewById(R.id.address_qrcode_imageview)
        addressQrCodeIconImageView = findViewById(R.id.address_qrcode_icon_imageview)
        addressTextView = findViewById(R.id.address_textview)
        amountTextView = findViewById(R.id.amount_edittext)
        recipientNameTextView = findViewById(R.id.recipient_name_edittext)
        descriptionTextView = findViewById(R.id.transaction_description_edittext)

        bindWindowInsetsListener()
        bindListeners()
        bindObservers()
        init()
    }

    private fun preInit(): Boolean {
        if (viewModel.addressValue == null) {
            val extras = intent.extras
            if (extras != null && extras.containsKey(Constants.EXTRA_ADDRESS_VALUE)) {
                viewModel.addressValue = extras.getString(Constants.EXTRA_ADDRESS_VALUE)!!
                return true
            } else {
                Timber.e("Address value is null in the view model and it was not present in extras")
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
                bottom = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.bottom),
            )
            return@setOnApplyWindowInsetsListener insetsCompat
        }
    }

    private fun bindListeners() {
        backImageView.setOnClickListener {
            finish()
        }
        addressQrCodeImageView.setOnClickListener {
            viewModel.qrCode.value?.let { bitmapAndStatus ->
                if (bitmapAndStatus != null && bitmapAndStatus.second == QrCodeStatus.CURRENT) {
                    val qrCodeDialog = DialogFragmentQrCode(bitmapAndStatus.first, viewModel.showIconOnQrCodes.value!!, viewModel.isMaxBrightnessQrCodes.value!!)
                    qrCodeDialog.show(supportFragmentManager, "dialog_fragment_qr_code")
                }
            }
        }
        val generateQrCodeTextWarcher =
            object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable) {
                    generateQrCode()
                }
            }
        amountTextView.addTextChangedListener(generateQrCodeTextWarcher)
        recipientNameTextView.addTextChangedListener(generateQrCodeTextWarcher)
        descriptionTextView.addTextChangedListener(generateQrCodeTextWarcher)
    }

    private fun bindObservers() {
        viewModel.showIconOnQrCodes.observe(this) { isShowIconOnQrCodes ->
            if (isShowIconOnQrCodes) {
                addressQrCodeIconImageView.visibility = View.VISIBLE
            } else {
                addressQrCodeIconImageView.visibility = View.GONE
            }
        }
        viewModel.isForceUriFormatQrCodes.observe(this) { isForceUriFormatQrCodes ->
            generateQrCode()
        }
        viewModel.qrCode.observe(this) { bitmapAndStatus ->
            if (bitmapAndStatus == null) {
                addressQrCodeImageView.visibility = View.INVISIBLE
            } else {
                val (bitmap, status) = bitmapAndStatus
                addressQrCodeImageView.setImageBitmap(bitmap)
                addressQrCodeImageView.visibility = View.VISIBLE
                val alpha =
                    when (status) {
                        QrCodeStatus.CURRENT -> ALPHA_OPAQUE
                        QrCodeStatus.ABSOLETE -> ALPHA_HALF_TRANSPARENT
                    }
                addressQrCodeImageView.setAlpha(alpha)
            }
        }
    }

    private fun init() {
        addressTextView.setText(TextHelper.formatAddress(viewModel.addressValue!!))
    }

    override fun onResume() {
        super.onResume()

        val isShowIconOnQrCodes = PreferenceUtils.isShowIconOnQrCodes(this)
        val isMaxBrightnessQrCodes = PreferenceUtils.isMaxBrightnessQrCodes(this)
        val isForceUriFormatQrCodes = PreferenceUtils.isForceUriFormatQrCodes(this)

        viewModel.showIconOnQrCodes.value = isShowIconOnQrCodes
        viewModel.isMaxBrightnessQrCodes.value = isMaxBrightnessQrCodes
        viewModel.isForceUriFormatQrCodes.value = isForceUriFormatQrCodes
    }

    private fun qrCodeBackgroundColor(): Int = ContextCompat.getColor(this, R.color.oled_colorBackground)

    private fun generateQrCode() {
        val amount = amountTextView.getText().toString()
        val recipientName = recipientNameTextView.getText().toString()
        val description = descriptionTextView.getText().toString()

        val displayMetrics = Resources.getSystem().getDisplayMetrics()
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val size = min(width, height)

        viewModel.generateQrCode(amount, recipientName, description, viewModel.isForceUriFormatQrCodes.value!!, size, qrCodeBackgroundColor())
    }
}

class ActivityUriPaymentViewModel : ViewModel() {
    var addressValue: String? = null

    val qrCodeJob: AtomicReference<Job?> = AtomicReference(null)

    val showIconOnQrCodes: MutableLiveData<Boolean> = MutableLiveData(false)
    val isMaxBrightnessQrCodes: MutableLiveData<Boolean> = MutableLiveData(false)
    val isForceUriFormatQrCodes: MutableLiveData<Boolean> = MutableLiveData(false)

    val qrCode: MutableLiveData<Pair<Bitmap, QrCodeStatus>?> = MutableLiveData()

    fun generateQrCode(
        amount: String,
        recipientName: String,
        description: String,
        forceUriFormat: Boolean,
        size: Int,
        backgroundColor: Int,
    ) {
        qrCodeJob.getAndUpdate { job ->
            job?.cancel()
            qrCode.value =
                qrCode.value?.first?.let {
                    Pair(it, QrCodeStatus.ABSOLETE)
                }
            viewModelScope.launch {
                withContext(Dispatchers.Default) {
                    val qrCodeValue =
                        if (forceUriFormat || !amount.isBlank() || !recipientName.isBlank() || !description.isBlank()) {
                            val uriPaymentAddresses = listOf(addressValue!!)
                            val uriPaymentAmounts = if (amount.isBlank()) emptyList() else listOf(amount)
                            val uriPaymentRecipientNames = if (recipientName.isBlank()) emptyList() else listOf(recipientName)
                            val uriPaymentDescription = if (description.isBlank()) null else description
                            val uriPayment = UriPayment(uriPaymentAddresses, uriPaymentAmounts, uriPaymentRecipientNames, uriPaymentDescription)
                            val uriPaymentString = uriPayment.toUriString()
                            uriPaymentString
                        } else {
                            addressValue!!
                        }
                    val (bitmap, time) =
                        measureTimedValue {
                            QrCodeHelper.generateQrCode(qrCodeValue, size, size, backgroundColor)
                        }
                    Timber.d("Generated QR code bitmap in ${time.toInt(DurationUnit.MILLISECONDS)} milliseconds")
                    if (isActive) {
                        qrCode.postValue(Pair(bitmap!!, QrCodeStatus.CURRENT))
                    }
                }
            }
        }
    }
}

enum class QrCodeStatus {
    CURRENT,
    ABSOLETE,
}
