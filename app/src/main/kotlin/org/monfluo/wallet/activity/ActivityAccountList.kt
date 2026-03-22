package org.monfluo.wallet.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.R
import org.monfluo.wallet.adapter.AdapterAccount
import org.monfluo.wallet.model.Account
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.component.ActivityMonero
import kotlin.math.max

class ActivityAccountList : ActivityMonero() {
    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var addAccountButton: Button

    private lateinit var adapterRecyclerView: RecyclerView

    private val adapterAccount: AdapterAccount =
        AdapterAccount(
            listOf(),
            null,
            object : AdapterAccount.Listener {
                override fun onAccountSelected(
                    self: AdapterAccount,
                    account: Account,
                ) {
                    self.setSelectedAccount(account)
                    walletService?.setAccount(account.index)
                }

                override fun onAccountEditClicked(
                    self: AdapterAccount,
                    account: Account,
                ) {
                    startActivity(
                        Intent(this@ActivityAccountList, ActivityAccountDetails::class.java).apply {
                            putExtra(Constants.EXTRA_ACCOUNT_INDEX, account.index)
                        },
                    )
                }
            },
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_list)

        layoutView = findViewById(R.id.accounts_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        addAccountButton = findViewById(R.id.add_account_button)
        adapterRecyclerView = findViewById(R.id.accounts_recyclerview)

        adapterRecyclerView.layoutManager = LinearLayoutManager(this)
        adapterRecyclerView.adapter = adapterAccount

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
        backImageView.setOnClickListener { finish() }
        addAccountButton.setOnClickListener {
            startActivity(Intent(this, ActivityAccountAdd::class.java))
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService?.getWallet()?.let { wallet ->
            updateAdapterAccount(wallet)
        }
    }

    override fun onWalletUpdated(wallet: Wallet) {
        runOnUiThread {
            updateAdapterAccount(wallet)
        }
    }

    override fun onAccountCreated() {
        walletService?.getWallet()?.let { wallet ->
            runOnUiThread {
                updateAdapterAccount(wallet)
                Toast.makeText(this, getText(R.string.toast_new_account_created), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onAccountSet(index: Int) {
        walletService?.getWallet()?.let { wallet ->
            runOnUiThread {
                updateAdapterAccount(wallet)
                Toast.makeText(this, getString(R.string.toast_account_selected, index), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onAccountLabelChanged(
        index: Int,
        label: String,
    ) {
        walletService?.getWallet()?.let { wallet ->
            runOnUiThread {
                updateAdapterAccount(wallet)
            }
        }
    }

    private fun updateAdapterAccount(wallet: Wallet) {
        val accounts =
            (0 until wallet.getNumAccounts()).map {
                val index = it
                val label = wallet.getAccountLabel(index)
                val balance = wallet.getBalance(index)
                Account(index, label, balance)
            }
        adapterAccount.setAccounts(accounts)
        adapterAccount.setSelectedAccount(accounts[wallet.getAccountIndex()])
    }
}
