package org.monfluo.wallet

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import org.monfluo.wallet.util.acitivity.MoneroActivity
import kotlin.math.max

class AccountsActivity : MoneroActivity() {

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)

        layoutView = findViewById(R.id.accounts_activity_view)
        backImageView = findViewById(R.id.back_imageview)

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
    }
}