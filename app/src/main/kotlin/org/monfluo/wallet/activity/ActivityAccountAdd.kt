package org.monfluo.wallet.activity

import org.monfluo.wallet.util.acitivity.MoneroActivity
import org.monfluo.wallet.R
import android.view.View
import android.widget.ImageView
import android.widget.EditText
import android.widget.Button
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import kotlin.math.max

class ActivityAccountAdd : MoneroActivity() {

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var accountNameEditText: EditText
    private lateinit var addAccountButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_add)

        layoutView = findViewById(R.id.activity_account_add_layout_view)
        backImageView = findViewById(R.id.back_imageview)
        accountNameEditText = findViewById(R.id.account_name_edittext)
        addAccountButton = findViewById(R.id.add_account_button)

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
        addAccountButton.setOnClickListener {
            walletService?.let { walletService ->
                walletService.createAccount(accountNameEditText.text.toString())
                finish()
            }
        }
    }
}