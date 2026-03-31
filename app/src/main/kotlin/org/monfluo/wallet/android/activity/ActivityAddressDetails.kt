package org.monfluo.wallet.android.activity

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.model.Wallet
import org.monfluo.wallet.android.service.wallet.WalletService
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.component.ActivityMonero
import timber.log.Timber
import kotlin.math.max

class ActivityAddressDetails : ActivityMonero() {
    private val viewModel: ActivityAddressDetailsViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var addressNameEditText: EditText
    private lateinit var saveAddressButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!init()) {
            finish()
            return
        }
        setContentView(R.layout.activity_address_details)

        layoutView = findViewById(R.id.activity_address_details_view)
        backImageView = findViewById(R.id.back_imageview)
        addressNameEditText = findViewById(R.id.address_name_edittext)
        saveAddressButton = findViewById(R.id.save_address_button)

        bindWindowInsetsListener()
        bindListeners()
    }

    private fun init(): Boolean {
        if (viewModel.accountIndex == null || viewModel.addressIndex == null) {
            val extras = intent.extras
            if (extras != null && extras.containsKey(Constants.EXTRA_ACCOUNT_INDEX) && extras.containsKey(Constants.EXTRA_ADDRESS_INDEX)) {
                viewModel.accountIndex = extras.getInt(Constants.EXTRA_ACCOUNT_INDEX)
                viewModel.addressIndex = extras.getInt(Constants.EXTRA_ADDRESS_INDEX)
                return true
            } else {
                Timber.e("Address or account index is null in the view model and they were not present in extras")
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
        saveAddressButton.setOnClickListener {
            walletService?.let { walletService ->
                walletService.setAddressLabel(viewModel.accountIndex!!, viewModel.addressIndex!!, addressNameEditText.text.toString())
                finish()
            }
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        updateAddressName()
    }

    override fun onWalletUpdated(wallet: Wallet) {
        runOnUiThread {
            updateAddressName()
        }
    }

    override fun onAddressLabelChanged(
        accountIndex: Int,
        addressIndex: Int,
        label: String,
    ) {
        if (accountIndex == viewModel.accountIndex && addressIndex == viewModel.addressIndex) {
            runOnUiThread {
                updateAddressName(label)
            }
        }
    }

    private fun updateAddressName(label: String? = null) {
        if (addressNameEditText.text.isBlank()) {
            label ?: walletService
                ?.getWallet()
                ?.let { wallet ->
                    wallet.getAddressLabel(viewModel.accountIndex!!, viewModel.addressIndex!!)
                }?.let { newLabel ->
                    addressNameEditText.setText(newLabel)
                }
        }
    }
}

internal class ActivityAddressDetailsViewModel : ViewModel() {
    var accountIndex: Int? = null
    var addressIndex: Int? = null
}
