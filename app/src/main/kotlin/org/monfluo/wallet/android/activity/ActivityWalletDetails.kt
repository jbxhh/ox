package org.monfluo.wallet.android.activity

import android.app.AlertDialog
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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.Helper
import org.monfluo.wallet.android.util.PreferenceUtils
import org.monfluo.wallet.android.util.component.ActivityBase
import org.monfluo.wallet.lib.Monfluo
import kotlin.math.max

class ActivityWalletDetails : ActivityBase() {
    private val viewModel: ActivityWalletDetailsViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var walletNameEditText: EditText
    private lateinit var deleteButton: Button
    private lateinit var doneButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_details)

        layoutView = findViewById(R.id.activity_wallet_details_layout)
        backImageView = findViewById(R.id.back_imageview)
        walletNameEditText = findViewById(R.id.wallet_name_edittext)
        doneButton = findViewById(R.id.done_editing_button)
        deleteButton = findViewById(R.id.delete_node_button)

        bindWindowInsetsListener()
        bindListeners()
        bindObservers()
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
        doneButton.setOnClickListener {
            val wallet = viewModel.wallet.value!!
            val newWalletName = walletNameEditText.text.toString()
            if (wallet != newWalletName) {
                Monfluo.renameWallet(Helper.getWalletRoot(this).absolutePath, wallet, newWalletName)
                PreferenceUtils.renameWalletNetworkType(this@ActivityWalletDetails, wallet, newWalletName)
            }
            finish()
        }
        deleteButton.setOnClickListener {
            val wallet = viewModel.wallet.value!!

            AlertDialog
                .Builder(this)
                .setTitle(getString(R.string.dialog_delete_wallet_title))
                .setMessage(getString(R.string.dialog_delete_wallet_message))
                .setPositiveButton(getString(R.string.dialog_delete_wallet_action_yes)) { _, _ ->
                    Monfluo.deleteWallet(Helper.getWalletRoot(this).absolutePath, wallet)
                    PreferenceUtils.deleteWalletNetworkType(this@ActivityWalletDetails, wallet)
                    finish()
                }.setNegativeButton(getString(R.string.dialog_delete_wallet_action_no), null)
                .create()
                .show()
        }
    }

    private fun bindObservers() {
        viewModel.wallet.observe(this) { wallet ->
            if (wallet != null && walletNameEditText.text.isEmpty()) {
                walletNameEditText.setText(wallet)
            }
        }
    }

    private fun init() {
        val wallet = intent.getStringExtra(Constants.EXTRA_WALLET_NAME)
        if (viewModel.wallet.value == null) {
            viewModel.wallet.value = wallet
        }
    }
}

internal class ActivityWalletDetailsViewModel : ViewModel() {
    val wallet: MutableLiveData<String?> = MutableLiveData(null)
}
