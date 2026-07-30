package org.monfluo.wallet.android.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.adapter.AdapterAddress
import org.monfluo.wallet.android.adapter.AdapterAddress.AdapterAddressListener
import org.monfluo.wallet.android.model.Wallet
import org.monfluo.wallet.android.service.wallet.WalletService
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.Helper.clipBoardCopy
import org.monfluo.wallet.android.util.PreferenceUtils
import org.monfluo.wallet.android.util.component.ActivityMonero
import org.monfluo.wallet.lib.data.AddressView
import kotlin.math.max
import org.monfluo.wallet.lib.wallet.Wallet as NewWallet

class ActivityReceive : ActivityMonero() {
    private val viewModel: ReceiveViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView
    private lateinit var goToAccountListImageView: ImageView

    private lateinit var freshAddressImageView: ImageButton
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
                bottom = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.bottom),
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
                walletService.getNewWalletOrLog()?.let { wallet ->
                    wallet.registerNewAddressForCurrentAccount()
                    wallet.store()
                    viewModel.refreshAddresses(wallet, true)
                }
            }
        }
        createPaymentQrCodeImageView.setOnClickListener {
            viewModel.addressIndex.value?.let { addressIndex ->
                startActivity(
                    Intent(this, ActivityPaymentRequest::class.java).apply {
                        putExtra(Constants.EXTRA_ADDRESS_VALUE, viewModel.addresses.value!![addressIndex]!!.value)
                    },
                )
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

        val adapterAddressListener =
            object : AdapterAddressListener {
                override fun onAddressSelected(address: AddressView) {
                    viewModel.selectAddress(address)
                }

                override fun onAddressEditLabel(address: AddressView) {
                    editAddressLabel(address)
                }

                override fun onAddressCopy(address: AddressView) {
                    clipBoardCopy(this@ActivityReceive, "monero address", address.value)
                }
            }

        val streetMode = PreferenceUtils.isStreetMode(this)
        val adapter = AdapterAddress(emptyList(), -1, streetMode, adapterAddressListener)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        viewModel.addressIndex.observe(this) { addressIndex ->
            adapter.submitSelectedAddressIndex(addressIndex)
        }
        viewModel.addresses.observe(this) { addresses: List<AddressView>? ->
            // We want newer addresses to be shown first
            adapter.submitAddresses((addresses ?: emptyList()).reversed())
        }
    }

    override fun onResume() {
        super.onResume()

        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(this)

        viewModel.multiAccountMode.postValue(isMultiAccountMode)
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService.getNewWalletOrLog()?.let { wallet ->
            showUi()
            viewModel.refreshAddresses(wallet)
        }
    }

    override fun onWalletUpdated(wallet: Wallet) {
        // runOnUiThread {
        //     showUi()
        //     viewModel.refreshAddresses(wallet)
        // }
    }

    override fun onAddressGenerated() {
        runOnUiThread {
            walletService?.getNewWalletOrLog()?.let { wallet ->
                showUi()
                viewModel.refreshAddresses(wallet, true)
            }
        }
    }

    override fun onAddressLabelChanged(
        accountIndex: Int,
        addressIndex: Int,
        label: String,
    ) {
        runOnUiThread {
            walletService?.getNewWalletOrLog()?.let { wallet ->
                showUi()
                viewModel.refreshAddresses(wallet, false)
            }
        }
    }

    override fun onAccountSet(index: Int) {
        runOnUiThread {
            walletService?.getNewWalletOrLog()?.let { wallet ->
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

    private fun editAddressLabel(address: AddressView) {
        startActivity(
            Intent(this, ActivityAddressDetails::class.java).apply {
                putExtra(Constants.EXTRA_ACCOUNT_INDEX, address.accountIndex)
                putExtra(Constants.EXTRA_ADDRESS_INDEX, address.addressIndex)
            },
        )
    }
}

class ReceiveViewModel : ViewModel() {
    val multiAccountMode: MutableLiveData<Boolean> = MutableLiveData(false)
    val addressIndex: MutableLiveData<Int> = MutableLiveData(-1)
    val addresses: MutableLiveData<List<AddressView>> = MutableLiveData(null)

    fun refreshAddresses(
        wallet: NewWallet,
        selectLatest: Boolean = false,
    ) {
        val accountView = wallet.getAccountView(0)
        val addresses = accountView.addresses
        this.addresses.value = addresses

        val addressIndex = this.addressIndex.value!!
        if (addressIndex == -1 || selectLatest) {
            selectAddress(addresses.lastOrNull())
        } else {
            selectAddress(addresses.getOrNull(addressIndex))
        }
    }

    fun selectAddress(subaddress: AddressView?) {
        addressIndex.value = subaddress?.addressIndex ?: -1
    }
}
