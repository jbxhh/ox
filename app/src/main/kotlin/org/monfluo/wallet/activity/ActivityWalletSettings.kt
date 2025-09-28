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
import org.monfluo.wallet.fragment.DialogFragmentPassword
import org.monfluo.wallet.util.component.ActivityMonero
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.R
import kotlin.math.max

class ActivityWalletSettings : ActivityMonero() {

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
        displaySeedButton.setOnClickListener {
            walletService?.getWallet()?.let { wallet ->
                val passwordDialog = DialogFragmentPassword(wallet.info.path, false, object : DialogFragmentPassword.Listener {
                    override fun onCorrectPasswordSubmitted(self: DialogFragmentPassword, password: String) {
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
            startActivity(Intent(this, ActivityWalletSecrets::class.java))
        } else {
            Toast.makeText(this, getText(R.string.toast_wallet_not_opened), Toast.LENGTH_SHORT).show()
        }
    }
}
