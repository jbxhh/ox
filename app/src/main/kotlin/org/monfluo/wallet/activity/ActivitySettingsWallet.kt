package org.monfluo.wallet.activity

import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import org.monfluo.wallet.fragment.dialog.PasswordBottomSheetDialog
import org.monfluo.wallet.fragment.dialog.WalletSecretsBottomSheetDialog
import org.monfluo.wallet.util.acitivity.MoneroActivity
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.R
import kotlin.math.max

class ActivitySettingsWallet : MoneroActivity() {

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var displaySeedButton: Button
    private lateinit var closeWalletButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_settings)

        layoutView = findViewById(R.id.activity_wallet_settings_layout)
        backImageView = findViewById(R.id.back_imageview)
        displaySeedButton = findViewById(R.id.display_wallet_secrets_button)
        closeWalletButton = findViewById(R.id.close_wallet_button)

        bindWindowInsetsListener()
        bindListeners()
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
        displaySeedButton.setOnClickListener {
            walletService?.getWallet()?.let { wallet ->
                val passwordDialog = PasswordBottomSheetDialog(
                    wallet.info.path,
                    false,
                    object : PasswordBottomSheetDialog.Listener {
                        override fun onCorrectPasswordSubmitted(self: PasswordBottomSheetDialog, password: String) {
                            self.dismiss()
                            displaySeedDialog()
                        }
                    })
                passwordDialog.show(supportFragmentManager, "password_dialog")
            }
        }
        closeWalletButton.setOnClickListener {
            stopService(Intent(this, WalletService::class.java))
        }
    }

    private fun displaySeedDialog() {
        val wallet = walletService!!.getWallet()
        if (wallet != null) {
            val informationDialog = WalletSecretsBottomSheetDialog(wallet)
            informationDialog.show(supportFragmentManager, "information_seed_dialog")
        } else {
            Toast.makeText(this, getText(R.string.toast_wallet_not_opened), Toast.LENGTH_SHORT).show()
        }
    }
}