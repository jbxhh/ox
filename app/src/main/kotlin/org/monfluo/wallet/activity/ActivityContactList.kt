package org.monfluo.wallet.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.R
import org.monfluo.wallet.adapter.AdapterContact
import org.monfluo.wallet.model.Contact
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.component.ActivityMonero
import timber.log.Timber
import kotlin.math.max

class ActivityContactList : ActivityMonero() {
    companion object {
        const val RESULT_OK: Int = 1
    }

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var addContactButton: Button

    private lateinit var adapterRecyclerView: RecyclerView

    private val adapterContact: AdapterContact =
        AdapterContact(
            listOf(),
            object : AdapterContact.Listener {
                override fun onContactSelected(
                    self: AdapterContact,
                    contact: Contact,
                ) {
                    Timber.i("Contact selected, setting result and finishing activity")
                    setResult(
                        RESULT_OK,
                        Intent().apply {
                            putExtra(Constants.EXTRA_CONTACT, contact)
                        },
                    )
                    finish()
                }

                override fun onContactEditClicked(
                    self: AdapterContact,
                    contact: Contact,
                ) {
                    startActivity(
                        Intent(this@ActivityContactList, ActivityContactDetails::class.java).apply {
                            putExtra(Constants.EXTRA_CONTACT, contact)
                        },
                    )
                }
            },
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_list)

        layoutView = findViewById(R.id.contacts_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        addContactButton = findViewById(R.id.add_contact_button)
        adapterRecyclerView = findViewById(R.id.contacts_recyclerview)

        adapterRecyclerView.layoutManager = LinearLayoutManager(this)
        adapterRecyclerView.adapter = adapterContact

        bindWindowInsetsListener()
        bindListeners()
    }

    override fun onResume() {
        super.onResume()
        walletService?.getWallet()?.let { wallet ->
            updateAdapterContact(wallet)
        }
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
        backImageView.setOnClickListener { finish() }
        addContactButton.setOnClickListener {
            startActivity(Intent(this, ActivityContactAdd::class.java))
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService?.getWallet()?.let { wallet ->
            updateAdapterContact(wallet)
        }
    }

    override fun onWalletUpdated(wallet: Wallet) {
        runOnUiThread {
            updateAdapterContact(wallet)
        }
    }

    private fun updateAdapterContact(wallet: Wallet) {
        adapterContact.submitContacts(wallet.getContacts())
    }
}
