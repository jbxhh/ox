package org.monfluo.wallet.activity

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
import org.monfluo.wallet.R
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.component.ActivityMonero
import timber.log.Timber
import kotlin.math.max

class ActivityAccountDetails : ActivityMonero() {
    private val viewModel: ActivityAccountDetailsViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var accountNameEditText: EditText
    private lateinit var saveAccountButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_details)

        layoutView = findViewById(R.id.activity_account_details_view)
        backImageView = findViewById(R.id.back_imageview)
        accountNameEditText = findViewById(R.id.account_name_edittext)
        saveAccountButton = findViewById(R.id.save_account_button)

        bindWindowInsetsListener()
        bindListeners()
        init()
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
        saveAccountButton.setOnClickListener {
            walletService?.let { walletService ->
                walletService.setAccountLabel(viewModel.accountIndex!!, accountNameEditText.text.toString())
                finish()
            }
        }
    }

    private fun init() {
        if (viewModel.accountIndex == null) {
            val extras = intent.extras
            if (extras != null && extras.containsKey(Constants.EXTRA_ACCOUNT_INDEX)) {
                viewModel.accountIndex = extras.getInt(Constants.EXTRA_ACCOUNT_INDEX)
            } else {
                Timber.e("Account index is null in the view model and it was not present in extras")
                finish()
            }
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        updateAccountName()
    }

    override fun onWalletUpdated(wallet: Wallet) {
        runOnUiThread {
            updateAccountName()
        }
    }

    override fun onAccountLabelChanged(
        index: Int,
        label: String,
    ) {
        if (index == viewModel.accountIndex) {
            runOnUiThread {
                updateAccountName(label)
            }
        }
    }

    private fun updateAccountName(label: String? = null) {
        if (accountNameEditText.text.isBlank()) {
            label ?: walletService
                ?.getWallet()
                ?.let { wallet ->
                    wallet.getAccountLabel(viewModel.accountIndex!!)
                }?.let { newLabel ->
                    accountNameEditText.setText(newLabel)
                }
        }
    }
}

internal class ActivityAccountDetailsViewModel : ViewModel() {
    var accountIndex: Int? = null
}
