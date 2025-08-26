package org.monfluo.wallet.activity

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Button
import android.widget.Toast
import android.content.Intent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.util.acitivity.MoneroActivity
import org.monfluo.wallet.R
import org.monfluo.wallet.adapter.AccountAdapter
import org.monfluo.wallet.model.Account
import org.monfluo.wallet.model.Balance
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Constants
import kotlin.math.max

class ActivityAccountList : MoneroActivity() {

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var addAccountButton: Button

    private lateinit var adapterRecyclerView: RecyclerView

    private val accountAdapter: AccountAdapter = AccountAdapter(listOf(), null, object : AccountAdapter.Listener {
        override fun onAccountSelected(self: AccountAdapter, account: Account) {
            self.setSelectedAccount(account)
            walletService?.setAccount(account.index)
        }
        override fun onAccountEditClicked(self: AccountAdapter, account: Account) {
            startActivity(Intent(this@ActivityAccountList, ActivityAccountDetails::class.java).apply {
                putExtra(Constants.EXTRA_ACCOUNT_INDEX, account.index)
            })
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_list)

        layoutView = findViewById(R.id.accounts_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        addAccountButton = findViewById(R.id.add_account_button)
        adapterRecyclerView = findViewById(R.id.accounts_recyclerview)

        adapterRecyclerView.layoutManager = LinearLayoutManager(this)
        adapterRecyclerView.adapter = accountAdapter

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
            startActivity(Intent(this, ActivityAccountAdd::class.java))
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        updateAccountAdapter()
    }

    override fun onWalletUpdated(wallet: Wallet) {
        runOnUiThread {
            updateAccountAdapter()
        }
    }

    override fun onAccountCreated() {
        runOnUiThread {
            updateAccountAdapter()
            Toast.makeText(this, getText(R.string.toast_new_account_created), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAccountSet(index: Int) {
        runOnUiThread {
            updateAccountAdapter()
            Toast.makeText(this, getString(R.string.toast_account_selected, index), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAccountLabelChanged(index: Int, label: String) {
        runOnUiThread {
            updateAccountAdapter()
        }
    }

    private fun updateAccountAdapter() {
        walletService?.getWallet()?.let { wallet ->
            val accounts = (0 until wallet.getNumAccounts()).map {
                val index = it
                val label = wallet.getAccountLabel(index)
                val balance = wallet.getBalance(index)
                Account(index, label, balance)
            }
            accountAdapter.setAccounts(accounts)
            accountAdapter.setSelectedAccount(accounts[wallet.getAccountIndex()])
        }
    }
}