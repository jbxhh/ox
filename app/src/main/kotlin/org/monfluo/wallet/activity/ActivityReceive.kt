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

    private lateinit var freshAddressImageView : ImageButton
    private lateinit var createPaymentQrCodeImageView: ImageButton
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receive)

        layoutView = findViewById(R.id.receive_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        goToAccountListImageView = findViewById(R.id.go_to_account_list_imageview)
        freshAddressImageView = findViewById(R.id.fresh_address_imageview)
        createPaymentQrCodeImageView = findViewById(R.id.create_payment_qr_code_imageview)
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
        createPaymentQrCodeImageView.setOnClickListener {
            viewModel.selectedAddress.value?.let { selectedAddress ->
                startActivity(Intent(this, ActivityPaymentRequest::class.java).apply {
                    putExtra(Constants.EXTRA_ADDRESS_VALUE, selectedAddress.address)
                })
            }
        }
    }

    private fun bindObservers() {
        viewModel.multiAccountMode.observe(this) { multiAccountMode ->
            if (multiAccountMode) {
                goToAccountListImageView.visibility = View.VISIBLE
            } else {
                goToAccountListImageView.visibility = View.GONE
            }
        }

        val adapterAddressListener = object : AdapterAddressListener {
            override fun onAddressSelected(address: Address) {
                viewModel.selectAddress(address)
            }

            override fun onAddressEditLabel(address: Address) {
                editAddressLabel(address)
            }

            override fun onAddressCopy(address: Address) {
                clipBoardCopy(this@ActivityReceive, "monero address", address.address)
            }
        }

        val streetMode = PreferenceUtils.isStreetMode(this)
        val adapter = AdapterAddress(emptyList(), null, streetMode, adapterAddressListener)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        viewModel.selectedAddress.observe(this) { address: Address? ->
            if (address != null) {
                adapter.submitSelectedAddress(address)
            }
        }
        viewModel.addresses.observe(this) { addresses: List<Address> ->
            // We want newer addresses to be shown first
            adapter.submitAddresses(addresses.reversed())
        }
    }

    override fun onResume() {
        super.onResume()

        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(this)

        viewModel.multiAccountMode.postValue(isMultiAccountMode)
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService.getWallet()?.let { wallet ->
            showUi()
            viewModel.refreshAddresses(wallet)
            autoRotateAddresses()
        }
    }

    override fun onWalletUpdated(wallet: Wallet) {
        runOnUiThread {
            showUi()
            viewModel.refreshAddresses(wallet)
            autoRotateAddresses()
        }
    }

    override fun onAddressGenerated() {
        runOnUiThread {
            walletService?.getWallet()?.let { wallet ->
                showUi()
                viewModel.refreshAddresses(wallet, true)
            }
        }
    }

    override fun onAddressLabelChanged(accountIndex: Int, addressIndex: Int, label: String) {
        runOnUiThread {
            walletService?.getWallet()?.let { wallet ->
                showUi()
                viewModel.refreshAddresses(wallet, false)
            }
        }
    }

    override fun onAccountSet(index: Int) {
        runOnUiThread {
            walletService?.getWallet()?.let { wallet ->
                showUi()
                viewModel.refreshAddresses(wallet)
            }
        }
    }

    private fun showUi() {
        recyclerView.visibility = View.VISIBLE
        freshAddressImageView.visibility = View.VISIBLE
        createPaymentQrCodeImageView.visibility = View.VISIBLE
    }

    private fun editAddressLabel(address: Address) {
        startActivity(Intent(this, ActivityAddressDetails::class.java).apply {
            putExtra(Constants.EXTRA_ACCOUNT_INDEX, address.accountIndex)
            putExtra(Constants.EXTRA_ADDRESS_INDEX, address.addressIndex)
        })
    }

    private fun autoRotateAddresses() {
        if (PreferenceUtils.isAutoRotateAddresses(this)) {
            viewModel.addresses.value?.lastOrNull()?.let { lastAddress ->
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

    // we need to store this in order to avoid rotating a single address more than once
    // which might happen when we get second callback before new address has been generated
    val lastAutoRotatedAddress: AtomicReference<String?> = AtomicReference(null)

    val multiAccountMode = MutableLiveData(false)

    private val _selectedAddress = MutableLiveData<Address?>()
    val selectedAddress: LiveData<Address?> = _selectedAddress

    private val _addresses = MutableLiveData<List<Address>>()
    val addresses: LiveData<List<Address>> = _addresses

    fun refreshAddresses(wallet: Wallet, selectLatest: Boolean = false) {
        val newAddresses = (0 until wallet.numAddresses).map { wallet.getAddressObject(it) }
        _addresses.value = newAddresses

        val currentSelectedAddress = selectedAddress.value
        if (currentSelectedAddress == null || selectLatest) {
            selectAddress(newAddresses.lastOrNull())
        } else {
            selectAddress(newAddresses.getOrNull(currentSelectedAddress.addressIndex))
        }
    }

    fun selectAddress(subaddress: Address?) {
        _selectedAddress.value = subaddress
    }
}
