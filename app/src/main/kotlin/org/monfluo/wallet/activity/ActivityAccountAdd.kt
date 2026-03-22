package org.monfluo.wallet.activity

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import org.monfluo.wallet.R
import org.monfluo.wallet.util.component.ActivityMonero
import kotlin.math.max

class ActivityAccountAdd : ActivityMonero() {
    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var accountNameEditText: EditText
    private lateinit var addAccountButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_add)

        layoutView = findViewById(R.id.activity_account_add_layout)
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
        addAccountButton.setOnClickListener {
            walletService?.let { walletService ->
                walletService.createAccount(accountNameEditText.text.toString())
                finish()
            }
        }
    }
}
