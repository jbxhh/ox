package org.monfluo.wallet

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.adapter.AccountAdapter
import org.monfluo.wallet.fragment.dialog.EditAccountLabelBottomSheetDialog
import org.monfluo.wallet.fragment.dialog.PasswordBottomSheetDialog
import org.monfluo.wallet.fragment.dialog.WalletSecretsBottomSheetDialog
import org.monfluo.wallet.model.Account
import org.monfluo.wallet.model.Balance
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.acitivity.MoneroActivity

class WalletSettingsActivity : MoneroActivity() {

    private lateinit var displaySeedButton: Button
    private lateinit var displayEnotesButton: Button
    private lateinit var accountSettingsConstraintLayout: ConstraintLayout
    private lateinit var addAccountButton: Button
    private lateinit var accountsRecyclerView: RecyclerView

    private lateinit var accountAdapter: AccountAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_settings)

        displaySeedButton = findViewById(R.id.display_wallet_secrets_button)
        displayEnotesButton = findViewById(R.id.display_utxos_button)
        accountSettingsConstraintLayout = findViewById(R.id.wallet_account_settings_layout)
        addAccountButton = findViewById(R.id.add_account_button)
        accountsRecyclerView = findViewById(R.id.accounts_recycler_view)

        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(applicationContext)

        accountAdapter = AccountAdapter(emptyList(), null, object : AccountAdapter.AccountAdapterListener {
            override fun onAccountSelected(account: Account) {
                walletService?.setAccount(account.index)
            }

            override fun onAccountEditLabel(account: Account) {
                val dialog = EditAccountLabelBottomSheetDialog(account.label) {
                    walletService?.setAccountLabel(account.index, it)
                }
                dialog.show(supportFragmentManager, "edit_account_dialog")
            }
        })
        accountsRecyclerView.layoutManager = LinearLayoutManager(this)
        accountsRecyclerView.adapter = accountAdapter

        if (isMultiAccountMode) {
            accountSettingsConstraintLayout.visibility = View.VISIBLE
        }

        bindListeners()
    }

    private fun bindListeners() {
        displaySeedButton.setOnClickListener {
            walletService?.getWallet()?.let { wallet ->
                val passwordDialog = PasswordBottomSheetDialog(
                    wallet.info.path,
                    false,
                    object : PasswordBottomSheetDialog.Listener {
                        override fun onCorrectPasswordSubmitted(self: PasswordBottomSheetDialog, password: String) {
                            self.dismiss()
                            displaySeedDialog()
                        }
                    })
                passwordDialog.show(supportFragmentManager, "password_dialog")
            }
        }

        displayEnotesButton.setOnClickListener {
            startActivity(Intent(this, EnotesActivity::class.java))
        }

        addAccountButton.setOnClickListener {
            Toast.makeText(this, getText(R.string.toast_creating_new_account), Toast.LENGTH_SHORT).show()
            walletService?.createAccount()
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
                val balance = Balance(0, 0, 0, 0)
                Account(index, label, balance)
            }
            accountAdapter.setAccounts(accounts)
            accountAdapter.setSelectedAccount(accounts[wallet.getAccountIndex()])
        }
    }

    private fun displaySeedDialog() {
        val wallet = walletService!!.getWallet()
        if (wallet != null) {
            val informationDialog = WalletSecretsBottomSheetDialog(wallet)
            informationDialog.show(supportFragmentManager, "information_seed_dialog")
        } else {
            Toast.makeText(this, getText(R.string.toast_wallet_not_opened), Toast.LENGTH_SHORT).show()
        }
    }
}