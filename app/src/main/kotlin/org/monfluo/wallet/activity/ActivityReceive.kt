package org.monfluo.wallet.activity

import android.content.Intent
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.monfluo.wallet.adapter.SubaddressAdapter
import org.monfluo.wallet.adapter.SubaddressAdapter.SubaddressAdapterListener
import org.monfluo.wallet.data.Subaddress
import org.monfluo.wallet.fragment.dialog.EditAddressLabelBottomSheetDialog
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.SubaddressUpdateReason
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Helper.clipBoardCopy
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.QrCodeHelper
import org.monfluo.wallet.util.acitivity.MoneroActivity
import org.monfluo.wallet.R
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

class ActivityReceive : MoneroActivity() {

    private val viewModel: ReceiveViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView
    private lateinit var goToAccountListImageView: ImageView

    private lateinit var addressTextView: TextView
    private lateinit var addressLabelTextView: TextView
    private lateinit var addressQrCodeImageView: ImageView
    private lateinit var addressQrCodeIconImageView: ImageView
    private lateinit var copyAddressImageButton: ImageButton
    private lateinit var freshAddressImageView : ImageButton
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receive)

        layoutView = findViewById(R.id.receive_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        goToAccountListImageView = findViewById(R.id.go_to_account_list_imageview)
        addressQrCodeImageView = findViewById(R.id.address_qrcode_imageview)
        addressQrCodeIconImageView = findViewById(R.id.address_qrcode_icon_imageview)
        addressTextView = findViewById(R.id.address_textview)
        addressLabelTextView = findViewById(R.id.address_label_textview)
        copyAddressImageButton = findViewById(R.id.copy_address_imagebutton)
        freshAddressImageView = findViewById(R.id.fresh_address_imageview)
        recyclerView = findViewById(R.id.address_list_recyclerview)

        bindWindowInsetsListener()
        bindListeners()
        bindObservers()
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
        goToAccountListImageView.setOnClickListener {
            startActivity(Intent(this, ActivityAccountList::class.java))
        }
        freshAddressImageView.setOnClickListener {
            walletService?.generateNewSubaddress("")
        }
    }

    private fun bindObservers() {
        viewModel.showIconOnQrCodes.observe(this) { isShowIconOnQrCodes ->
            if (isShowIconOnQrCodes) {
                addressQrCodeIconImageView.visibility = View.VISIBLE
            } else {
                addressQrCodeIconImageView.visibility = View.GONE
            }
        }

        val subaddressAdapterListener = object : SubaddressAdapterListener {
            override fun onSubaddressSelected(subaddress: Subaddress) {
                viewModel.selectAddress(subaddress, qrCodeBackgroundColor())
            }

            override fun onSubaddressEditLabel(subaddress: Subaddress) {
                editAddressLabel(subaddress)
            }
        }
        val streetMode = PreferenceUtils.isStreetMode(this)
        val adapter = SubaddressAdapter(emptyList(), null, streetMode, subaddressAdapterListener)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        viewModel.selectedSubaddress.observe(this) { address: Subaddress? ->
            if (address != null) {
                setAddress(address)
                adapter.submitSelectedAddress(address)
            }
        }
        viewModel.qrCode.observe(this) { bitmap: Bitmap? ->
            if (bitmap == null) {
                addressQrCodeImageView.visibility = View.INVISIBLE
            } else {
                addressQrCodeImageView.setImageBitmap(bitmap)
                addressQrCodeImageView.visibility = View.VISIBLE
            }
        }
        viewModel.subaddresses.observe(this) { addresses: List<Subaddress> ->
            // We want newer addresses to be shown first
            adapter.submitAddresses(addresses.reversed())
        }
    }

    override fun onResume() {
        super.onResume()

        val isShowIconOnQrCodes = PreferenceUtils.isShowIconOnQrCodes(this)

        viewModel.setShowIconOnQrCodes(isShowIconOnQrCodes)
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService.getWallet()?.let { wallet ->
            viewModel.refreshSubaddresses(this, wallet, qrCodeBackgroundColor())
            autoRotateAddresses()
        }
    }

    override fun onWalletUpdated(wallet: Wallet) {
        viewModel.refreshSubaddresses(this, wallet, qrCodeBackgroundColor())
        autoRotateAddresses()
    }

    override fun onSubaddressesUpdated(reason: SubaddressUpdateReason) {
        walletService?.getWallet()?.let { wallet ->
            val selectLatest = reason == SubaddressUpdateReason.GENERATED_NEW
            viewModel.refreshSubaddresses(this, wallet, qrCodeBackgroundColor(), selectLatest)
        }
    }

    override fun onAccountSet(index: Int) {
        runOnUiThread {
            walletService?.getWallet()?.let { wallet ->
                viewModel.refreshSubaddresses(this, wallet, qrCodeBackgroundColor())
            }
        }
    }

    private fun qrCodeBackgroundColor(): Int = ContextCompat.getColor(this, R.color.oled_colorBackground)

    private fun editAddressLabel(subaddress: Subaddress) {
        val dialog = EditAddressLabelBottomSheetDialog(subaddress.label) {
            walletService?.setSubaddressLabel(subaddress.addressIndex, it)
        }
        dialog.show(supportFragmentManager, "edit_address_dialog")
    }

    private fun setAddress(subaddress: Subaddress) {
        val label = subaddress.displayLabel
        val address = getString(
            R.string.subbaddress_info_subtitle,
            subaddress.addressIndex, subaddress.squashedAddress
        )
        addressLabelTextView.text = label.ifEmpty { address }
        addressLabelTextView.visibility = View.VISIBLE
        addressTextView.text = subaddress.address
        addressTextView.visibility = View.VISIBLE
        copyAddressImageButton.setOnClickListener {
            clipBoardCopy(
                this, "address", subaddress.address
            )
        }
        addressLabelTextView.setOnLongClickListener {
            editAddressLabel(subaddress)
            true
        }
        addressTextView.setOnLongClickListener {
            editAddressLabel(subaddress)
            true
        }
    }

    private fun autoRotateAddresses() {
        if (PreferenceUtils.isAutoRotateAddresses(this)) {
            viewModel.subaddresses.value?.lastOrNull()?.let { lastAddress ->
                if (lastAddress.used) {
                    val lastAutoRotatedAddress = viewModel.lastAutoRotatedAddress.getAndSet(lastAddress.address)
                    if (lastAddress.address != lastAutoRotatedAddress) {
                        Timber.i("Latest address ${lastAddress.address} has been used, auto-rotating it")
                        walletService?.generateNewSubaddress("")
                    } else {
                        Timber.d("Not auto-rotating latest address ${lastAddress.address} because it was already rotated")
                    }
                }
            }
        }
    }
}

class ReceiveViewModel : ViewModel() {

    private val qrCodeJob: AtomicReference<Job?> = AtomicReference(null)
    // we need to store this in order to avoid rotating a single address more than once
    // which might happen when we get second callback before new address has been generated
    val lastAutoRotatedAddress: AtomicReference<String?> = AtomicReference(null)

    private val _showIconOnQrCodes = MutableLiveData(false)
    val showIconOnQrCodes: LiveData<Boolean> = _showIconOnQrCodes

    private val _selectedSubaddress = MutableLiveData<Subaddress?>()
    val selectedSubaddress: LiveData<Subaddress?> = _selectedSubaddress

    private val _subaddresses = MutableLiveData<List<Subaddress>>()
    val subaddresses: LiveData<List<Subaddress>> = _subaddresses

    private val _addressAndQrCode = MutableLiveData<Pair<String, Bitmap?>?>()
    val qrCode: LiveData<Bitmap?> = _addressAndQrCode.map { it?.second }

    fun setShowIconOnQrCodes(showIconOnQrCodes: Boolean) {
        _showIconOnQrCodes.value = showIconOnQrCodes
    }

    fun refreshSubaddresses(activity: Activity, wallet: Wallet, backgroundColor: Int, selectLatest: Boolean = false) {
        activity.runOnUiThread {
            val newSubaddresses = (0 until wallet.numSubaddresses).map { wallet.getSubaddressObject(it) }
            _subaddresses.value = newSubaddresses

            val currentSelectedSubaddress = selectedSubaddress.value
            if (currentSelectedSubaddress == null || selectLatest) {
                selectAddress(newSubaddresses.lastOrNull(), backgroundColor)
            } else {
                selectAddress(newSubaddresses.getOrNull(currentSelectedSubaddress.addressIndex), backgroundColor)
            }
        }
    }

    fun selectAddress(subaddress: Subaddress?, backgroundColor: Int) {
        _selectedSubaddress.value = subaddress
        qrCodeJob.getAndUpdate { job ->
            job?.cancel()
            if (_addressAndQrCode.value?.first != subaddress?.address) {
                _addressAndQrCode.value = null
                subaddress?.let {
                    viewModelScope.launch {
                        withContext(Dispatchers.Default) {
                            val (bitmap, time) = measureTimedValue {
                                QrCodeHelper.generateQrCode(subaddress.address, 256, 256, backgroundColor)
                            }
                            Timber.d("Generated QR code bitmap in ${time.toInt(DurationUnit.MILLISECONDS)} milliseconds")
                            if (isActive) {
                                _addressAndQrCode.postValue(Pair(subaddress.address, bitmap))
                            }
                        }
                    }
                }
            } else {
                null
            }
        }
    }
}
