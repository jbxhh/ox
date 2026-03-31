package org.monfluo.wallet.android.activity

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.model.Contact
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.Helper
import org.monfluo.wallet.android.util.TextHelper
import org.monfluo.wallet.android.util.component.ActivityMonero
import timber.log.Timber
import kotlin.math.max

class ActivityContactDetails : ActivityMonero() {
    private val viewModel: ActivityContactDetailsViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var contactNameEditText: EditText
    private lateinit var contactAddressTextView: TextView
    private lateinit var deleteContactButton: Button
    private lateinit var saveContactButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_details)

        layoutView = findViewById(R.id.activity_contact_details_view)
        backImageView = findViewById(R.id.back_imageview)
        contactNameEditText = findViewById(R.id.contact_name_edittext)
        contactAddressTextView = findViewById(R.id.contact_address_textview)
        deleteContactButton = findViewById(R.id.delete_contact_button)
        saveContactButton = findViewById(R.id.save_contact_button)

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
        contactAddressTextView.setOnLongClickListener {
            viewModel.contact?.address?.let { address ->
                Helper.clipBoardCopy(this, "contact address", address)
            }
            return@setOnLongClickListener true
        }
        deleteContactButton.setOnClickListener {
            walletService?.let { walletService ->
                walletService.getWalletOrLog()?.let { wallet ->
                    if (wallet.deleteContact(viewModel.contact!!)) {
                        Toast.makeText(this, getText(R.string.activity_contact_details_toast_contact_deleted), Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, getText(R.string.activity_contact_details_toast_failed_to_delete_contact), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        saveContactButton.setOnClickListener {
            walletService?.let { walletService ->
                walletService.getWalletOrLog()?.let { wallet ->
                    if (wallet.renameContact(viewModel.contact!!.copy(name = contactNameEditText.text.toString()))) {
                        Toast.makeText(this, getText(R.string.activity_contact_details_toast_contact_saved), Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, getText(R.string.activity_contact_details_toast_failed_to_save_contact), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun init() {
        if (viewModel.contact == null) {
            val extras = intent.extras
            if (extras != null && extras.containsKey(Constants.EXTRA_CONTACT)) {
                val contact = extras.getParcelable<Contact>(Constants.EXTRA_CONTACT)!!
                viewModel.contact = contact
                contactNameEditText.setText(contact.name)
                contactAddressTextView.text = TextHelper.formatAddress(contact.address)
            } else {
                Timber.e("Contact is null in the view model and it was not present in extras")
                finish()
            }
        }
    }
}

internal class ActivityContactDetailsViewModel : ViewModel() {
    var contact: Contact? = null
}
