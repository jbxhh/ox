package org.monfluo.wallet

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.AtomicReference
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Helper.clipBoardCopy
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.QrCodeHelper
import org.monfluo.wallet.util.acitivity.MoneroActivity
import timber.log.Timber
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

class ReceiveActivity : MoneroActivity() {

    private val viewModel: ReceiveViewModel by viewModels()

    private lateinit var addressTextView: TextView
    private lateinit var addressLabelTextView: TextView
    private lateinit var addressImageView: ImageView
    private lateinit var copyAddressImageButton: ImageButton
    private lateinit var freshAddressImageView : ImageButton
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receive)

        addressImageView = findViewById(R.id.address_qrcode_imageview)
        addressTextView = findViewById(R.id.address_textview)
        addressLabelTextView = findViewById(R.id.address_label_textview)
        copyAddressImageButton = findViewById(R.id.copy_address_imagebutton)
        freshAddressImageView = findViewById(R.id.fresh_address_imageview)
        recyclerView = findViewById(R.id.address_list_recyclerview)

        bindListeners()
        bindObservers()
    }

    private fun bindListeners() {
        freshAddressImageView.setOnClickListener {
            walletService?.generateNewSubaddress("")
        }
    }

    private fun bindObservers() {
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
                addressImageView.visibility = View.INVISIBLE
            } else {
                addressImageView.setImageBitmap(bitmap)
                addressImageView.visibility = View.VISIBLE
            }
        }
        viewModel.subaddresses.observe(this) { addresses: List<Subaddress> ->
            // We want newer addresses addresses to be shown first
            adapter.submitAddresses(addresses.reversed())
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService.getWallet()?.let { wallet ->
            viewModel.refreshSubaddresses(wallet, qrCodeBackgroundColor())
        }
    }

    override fun onWalletUpdated(wallet: Wallet) {
        viewModel.refreshSubaddresses(wallet, qrCodeBackgroundColor())
    }

    override fun onSubaddressesUpdated() {
        walletService?.getWallet()?.let { wallet ->
            viewModel.refreshSubaddresses(wallet, qrCodeBackgroundColor())
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
}

class ReceiveViewModel : ViewModel() {
    private var qrCodeJob: AtomicReference<Job?> = AtomicReference(null)

    private val _selectedSubaddress = MutableLiveData<Subaddress?>()
    val selectedSubaddress: LiveData<Subaddress?> = _selectedSubaddress

    private val _subaddresses = MutableLiveData<List<Subaddress>>()
    val subaddresses: LiveData<List<Subaddress>> = _subaddresses

    private val _qrCode = MutableLiveData<Bitmap?>()
    val qrCode: LiveData<Bitmap?> = _qrCode

    fun refreshSubaddresses(wallet: Wallet, backgroundColor: Int) {
        val newSubaddresses = (0 until wallet.numSubaddresses).map { wallet.getSubaddressObject(it) }
        _subaddresses.postValue(newSubaddresses)

        val currentSelectedSubaddress = selectedSubaddress.value
        if (currentSelectedSubaddress == null) {
            selectAddress(newSubaddresses.lastOrNull(), backgroundColor)
        } else {
            selectAddress(newSubaddresses.getOrNull(currentSelectedSubaddress.addressIndex), backgroundColor)
        }
    }

    fun selectAddress(subaddress: Subaddress?, backgroundColor: Int) {
        qrCodeJob.getAndUpdate { job ->
            job?.cancel()
            _selectedSubaddress.postValue(subaddress)
            _qrCode.postValue(null)
            subaddress?.let {
                viewModelScope.launch {
                    withContext(Dispatchers.Default) {
                        val (bitmap, time) = measureTimedValue {
                            QrCodeHelper.generateQrCode(subaddress.address, 256, 256, backgroundColor)
                        }
                        Timber.d("Generated QR code bitmap in ${time.toInt(DurationUnit.MILLISECONDS)} milliseconds")
                        if (isActive) {
                            _qrCode.postValue(bitmap)
                        }
                    }
                }
            }
        }
    }
}