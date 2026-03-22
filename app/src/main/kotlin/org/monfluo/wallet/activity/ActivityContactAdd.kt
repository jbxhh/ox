package org.monfluo.wallet.activity

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import org.monfluo.wallet.R
import org.monfluo.wallet.model.Contact
import org.monfluo.wallet.util.component.ActivityMonero
import kotlin.math.max

class ActivityContactAdd : ActivityMonero() {
    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var contactNameEditText: EditText
    private lateinit var contactAddressEditText: EditText
    private lateinit var addContactButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_add)

        layoutView = findViewById(R.id.activity_contact_add_layout)
        backImageView = findViewById(R.id.back_imageview)
        contactNameEditText = findViewById(R.id.contact_name_edittext)
        contactAddressEditText = findViewById(R.id.contact_address_edittext)
        addContactButton = findViewById(R.id.add_contact_button)

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
        addContactButton.setOnClickListener {
            walletService?.let { walletService ->
                walletService.getWalletOrLog()?.let { wallet ->
                    if (wallet.addContact(Contact(contactNameEditText.text.toString(), contactAddressEditText.text.toString()))) {
                        Toast.makeText(this, getText(R.string.activity_contact_add_toast_contact_created), Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, getText(R.string.activity_contact_add_toast_failed_to_create_contact), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
