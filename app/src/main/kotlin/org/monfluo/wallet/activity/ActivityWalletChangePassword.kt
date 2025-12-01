package org.monfluo.wallet.activity

import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import org.monfluo.wallet.fragment.DialogFragmentPassword
import org.monfluo.wallet.model.MoneroUnit
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.util.component.ActivityMonero
import org.monfluo.wallet.util.TextHelper
import org.monfluo.wallet.util.SimpleTextWatcher
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.R
import kotlin.math.max

class ActivityWalletChangePassword : ActivityMonero() {

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var currentPasswordEditText: TextView
    private lateinit var newPasswordEditText: TextView
    private lateinit var newPasswordConfirmEditText: TextView
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_change_password)

        layoutView = findViewById(R.id.activity_wallet_change_password_layout)
        backImageView = findViewById(R.id.back_imageview)
        currentPasswordEditText = findViewById(R.id.current_password_edittext)
        newPasswordEditText = findViewById(R.id.new_password_edittext)
        newPasswordConfirmEditText = findViewById(R.id.new_password_confirm_edittext)
        saveButton = findViewById(R.id.save_button)

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
        newPasswordEditText.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                if (s.toString().isEmpty()) {
                    newPasswordConfirmEditText.visibility = View.GONE
                } else {
                    newPasswordConfirmEditText.visibility = View.VISIBLE
                }
            }
        })
        saveButton.setOnClickListener {
            val walletService = walletService!!
            val wallet = walletService.getWalletOrThrow()

            val currentPassword = currentPasswordEditText.getText().toString()
            val newPassword = newPasswordEditText.getText().toString()
            val newPasswordConfirm = newPasswordConfirmEditText.getText().toString()

            if (wallet.getPassword() != currentPassword) {
                Toast.makeText(this, getText(R.string.activity_wallet_change_password_incorrect_current_password_toast), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword != newPasswordConfirm) {
                Toast.makeText(this, getText(R.string.activity_wallet_change_password_new_password_does_not_match_toast), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            walletService.setPassword(newPassword)
            Toast.makeText(this, getText(R.string.activity_wallet_change_password_changing_password_toast), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onWalletPasswordSet() {
        Toast.makeText(this, getText(R.string.activity_wallet_change_password_password_changed_toast), Toast.LENGTH_SHORT).show()
        finish()
    }
}
