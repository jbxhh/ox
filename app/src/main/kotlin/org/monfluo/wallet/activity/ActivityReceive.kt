package org.monfluo.wallet.activity

import android.content.Intent
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
import org.monfluo.wallet.adapter.AdapterAddress
import org.monfluo.wallet.adapter.AdapterAddress.AdapterAddressListener
import org.monfluo.wallet.data.Address
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Helper.clipBoardCopy
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.QrCodeHelper
import org.monfluo.wallet.util.component.ActivityMonero
import org.monfluo.wallet.R
import org.monfluo.wallet.util.Constants
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

class ActivityReceive : ActivityMonero() {

    private val viewModel: ReceiveViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView
    private lateinit var goToAccountListImageView: ImageView

    private lateinit var mainLayoutView: View
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
        mainLayoutView = findViewById(R.id.activity_receive_main_view)
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
        freshAddressImageView.setOnClickListener {
            walletService?.let { walletService ->
                walletService.getWalletOrLog()?.let { wallet ->
                    walletService.generateNewAddress(wallet.getAccountIndex(), "")
                }
            }
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
        viewModel.multiAccountMode.observe(this) { multiAccountMode ->
            if (multiAccountMode) {
                goToAccountListImageView.visibility = View.VISIBLE
            } else {
                goToAccountListImageView.visibility = View.GONE
            }
        }

        val subAdapterAddressListener = object : AdapterAddressListener {
            override fun onAddressSelected(subaddress: Address) {
                viewModel.selectAddress(subaddress, qrCodeBackgroundColor())
            }

            override fun onAddressEditLabel(subaddress: Address) {
                editAddressLabel(subaddress)
            }
        }
        val streetMode = PreferenceUtils.isStreetMode(this)
        val adapter = AdapterAddress(emptyList(), null, streetMode, subAdapterAddressListener)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        viewModel.selectedAddress.observe(this) { address: Address? ->
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
        viewModel.subaddresses.observe(this) { addresses: List<Address> ->
            // We want newer addresses to be shown first
            adapter.submitAddresses(addresses.reversed())
        }
    }

    override fun onResume() {
        super.onResume()

        val isShowIconOnQrCodes = PreferenceUtils.isShowIconOnQrCodes(this)
        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(this)

        viewModel.setShowIconOnQrCodes(isShowIconOnQrCodes)
        viewModel.multiAccountMode.postValue(isMultiAccountMode)
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService.getWallet()?.let { wallet ->
            showUi()
            viewModel.refreshAddresses(wallet, qrCodeBackgroundColor())
            autoRotateAddresses()
        }
    }

    override fun onWalletUpdated(wallet: Wallet) {
        runOnUiThread {
            showUi()
            viewModel.refreshAddresses(wallet, qrCodeBackgroundColor())
            autoRotateAddresses()
        }
    }

    override fun onAddressGenerated() {
        runOnUiThread {
            walletService?.getWallet()?.let { wallet ->
                showUi()
                viewModel.refreshAddresses(wallet, qrCodeBackgroundColor(), true)
            }
        }
    }

    override fun onAddressLabelChanged(accountIndex: Int, addressIndex: Int, label: String) {
        runOnUiThread {
            walletService?.getWallet()?.let { wallet ->
                showUi()
                viewModel.refreshAddresses(wallet, qrCodeBackgroundColor(), false)
            }
        }
    }

    override fun onAccountSet(index: Int) {
        runOnUiThread {
            walletService?.getWallet()?.let { wallet ->
                showUi()
                viewModel.refreshAddresses(wallet, qrCodeBackgroundColor())
            }
        }
    }

    private fun showUi() {
        mainLayoutView.visibility = View.VISIBLE
        freshAddressImageView.visibility = View.VISIBLE
    }

    private fun qrCodeBackgroundColor(): Int = ContextCompat.getColor(this, R.color.oled_colorBackground)

    private fun editAddressLabel(address: Address) {
        startActivity(Intent(this, ActivityAddressDetails::class.java).apply {
            putExtra(Constants.EXTRA_ACCOUNT_INDEX, address.accountIndex)
            putExtra(Constants.EXTRA_ADDRESS_INDEX, address.addressIndex)
        })
    }

    private fun setAddress(address: Address) {
        addressLabelTextView.text = getString(R.string.address_label, address.addressIndex, address.label)
        addressTextView.text = address.address
        if (address.used) {
            addressTextView.setTextColor(getColor(R.color.text_subtle))
        } else {
            addressTextView.setTextColor(getColor(R.color.text))
        }

        copyAddressImageButton.setOnClickListener {
            clipBoardCopy(this, "address", address.address)
        }
    }

    private fun autoRotateAddresses() {
        if (PreferenceUtils.isAutoRotateAddresses(this)) {
            viewModel.subaddresses.value?.lastOrNull()?.let { lastAddress ->
                if (lastAddress.used) {
                    val lastAutoRotatedAddress = viewModel.lastAutoRotatedAddress.getAndSet(lastAddress.address)
                    if (lastAddress.address != lastAutoRotatedAddress) {
                        Timber.i("Latest address ${lastAddress.address} has been used, auto-rotating it")
                        walletService?.let { walletService ->
                            walletService.getWallet()?.let { wallet ->
                                walletService.generateNewAddress(wallet.getAccountIndex(), "")
                            }
                        }
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

    val multiAccountMode = MutableLiveData(false)

    private val _selectedAddress = MutableLiveData<Address?>()
    val selectedAddress: LiveData<Address?> = _selectedAddress

    private val _subaddresses = MutableLiveData<List<Address>>()
    val subaddresses: LiveData<List<Address>> = _subaddresses

    private val _addressAndQrCode = MutableLiveData<Pair<String, Bitmap?>?>()
    val qrCode: LiveData<Bitmap?> = _addressAndQrCode.map { it?.second }

    fun setShowIconOnQrCodes(showIconOnQrCodes: Boolean) {
        _showIconOnQrCodes.value = showIconOnQrCodes
    }

    fun refreshAddresses(wallet: Wallet, backgroundColor: Int, selectLatest: Boolean = false) {
        val newAddresses = (0 until wallet.numAddresses).map { wallet.getAddressObject(it) }
        _subaddresses.value = newAddresses

        val currentSelectedAddress = selectedAddress.value
        if (currentSelectedAddress == null || selectLatest) {
            selectAddress(newAddresses.lastOrNull(), backgroundColor)
        } else {
            selectAddress(newAddresses.getOrNull(currentSelectedAddress.addressIndex), backgroundColor)
        }
    }

    fun selectAddress(subaddress: Address?, backgroundColor: Int) {
        _selectedAddress.value = subaddress
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
