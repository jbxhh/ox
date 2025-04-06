package org.monfluo.wallet

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.adapter.AccountAdapter
import org.monfluo.wallet.data.DefaultNode
import org.monfluo.wallet.data.Node
import org.monfluo.wallet.data.SortOrder
import org.monfluo.wallet.fragment.dialog.EditAccountLabelBottomSheetDialog
import org.monfluo.wallet.fragment.dialog.NodeSelectionBottomSheetDialog
import org.monfluo.wallet.fragment.dialog.PasswordBottomSheetDialog
import org.monfluo.wallet.fragment.dialog.WalletSecretsBottomSheetDialog
import org.monfluo.wallet.listener.NodeSelectionDialogListenerAdapter
import org.monfluo.wallet.model.Account
import org.monfluo.wallet.model.Balance
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.acitivity.MoneroActivity
import timber.log.Timber

class SettingsActivity : MoneroActivity() {

    private lateinit var walletProxyAddressEditText: EditText
    private lateinit var selectNodeButton: Button
    private lateinit var multiWalletModeSwitch: SwitchCompat
    private lateinit var multiAccountModeSwitch: SwitchCompat
    private lateinit var streetModeSwitch: SwitchCompat
    private lateinit var allowFeeOverrideSwitch: SwitchCompat
    private lateinit var allowCopyingSecretsToClipboardSwitch: SwitchCompat
    private lateinit var showTransferNotificationsSwitch: SwitchCompat
    private lateinit var displaySeedButton: Button
    private lateinit var displayEnotesButton: Button
    private lateinit var proxySwitch: SwitchCompat
    private lateinit var proxySettingsLayout: ConstraintLayout
    private lateinit var saveProxyButton: Button
    private lateinit var accountSettingsConstraintLayout: ConstraintLayout
    private lateinit var addAccountButton: Button
    private lateinit var accountsRecyclerView: RecyclerView
    private lateinit var aboutMonfluoTextView: TextView

    private lateinit var accountAdapter: AccountAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        displaySeedButton = findViewById(R.id.display_wallet_secrets_button)
        displayEnotesButton = findViewById(R.id.display_utxos_button)
        selectNodeButton = findViewById(R.id.select_node_button)
        multiWalletModeSwitch = findViewById(R.id.enable_multiple_wallets_switch)
        multiAccountModeSwitch = findViewById(R.id.enable_multiple_accounts_switch)
        streetModeSwitch = findViewById(R.id.street_mode_switch)
        allowFeeOverrideSwitch = findViewById(R.id.allow_fee_override_switch)
        allowCopyingSecretsToClipboardSwitch = findViewById(R.id.allow_copying_secrets_to_clipboard_switch)
        showTransferNotificationsSwitch = findViewById(R.id.show_transfer_notifications_switch)
        proxySwitch = findViewById(R.id.proxy_switch)
        proxySettingsLayout = findViewById(R.id.wallet_proxy_settings_layout)
        walletProxyAddressEditText = findViewById(R.id.wallet_proxy_address_edittext)
        saveProxyButton = findViewById(R.id.save_proxy_button)
        accountSettingsConstraintLayout = findViewById(R.id.wallet_account_settings_layout)
        addAccountButton = findViewById(R.id.add_account_button)
        accountsRecyclerView = findViewById(R.id.accounts_recycler_view)
        aboutMonfluoTextView = findViewById(R.id.about_monfluo_textview)

        val isMultiWalletMode = PreferenceUtils.isMultiWalletMode(applicationContext)
        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(applicationContext)
        val isStreetMode = PreferenceUtils.isStreetMode(applicationContext)
        val isAllowFeeOverride = PreferenceUtils.isAllowFeeOverride(applicationContext)
        val isAllowCopyingSecrets = PreferenceUtils.isAllowCopyingSecrets(applicationContext)
        val isShowTransferNotifications = PreferenceUtils.isShowTransferNotifications(applicationContext)
        val isUseProxy = PreferenceUtils.isUseProxy(applicationContext)

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

        multiWalletModeSwitch.isChecked = isMultiWalletMode
        multiAccountModeSwitch.isChecked = isMultiAccountMode
        streetModeSwitch.isChecked = isStreetMode
        allowFeeOverrideSwitch.isChecked = isAllowFeeOverride
        allowCopyingSecretsToClipboardSwitch.isChecked = isAllowCopyingSecrets
        showTransferNotificationsSwitch.isChecked = isShowTransferNotifications
        proxySwitch.isChecked = isUseProxy

        walletProxyAddressEditText.setText(PreferenceUtils.getProxy(this) ?: "")

        val node = PreferenceUtils.getOrSetDefaultNode(this, DefaultNode.defaultNode())
        selectNodeButton.text = getString(R.string.node_button_text, node.name)

        aboutMonfluoTextView.movementMethod = LinkMovementMethod.getInstance();

        bindListeners()
    }

    private fun bindListeners() {
        multiWalletModeSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setMultiWalletMode(applicationContext, b)
        }
        multiAccountModeSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setMultiAccountMode(applicationContext, b)
            if (b) {
                accountSettingsConstraintLayout.visibility = View.VISIBLE
            } else {
                accountSettingsConstraintLayout.visibility = View.GONE
            }
        }
        streetModeSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setStreetMode(applicationContext, b)
        }
        allowFeeOverrideSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setAllowFeeOverride(applicationContext, b)
        }
        allowCopyingSecretsToClipboardSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setAllowCopyingSecrets(applicationContext, b)
        }
        showTransferNotificationsSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setShowTransferNotifications(applicationContext, b)
            if (b) {
                Helper.requestPostNotificationsPermission(this, force = true)
            }
        }
        proxySwitch.setOnCheckedChangeListener { _, b: Boolean ->
            PreferenceUtils.setUseProxy(applicationContext, b)
        }

        selectNodeButton.setOnClickListener {
            val nodes = PreferenceUtils.getOrSetDefaultNodes(this, DefaultNode.defaultNodes())
            val node = PreferenceUtils.getOrSetDefaultNode(this, DefaultNode.defaultNode())
            val sortOrder = SortOrder.fromBoolean(PreferenceUtils.isSortNodesAscending(this))

            val listener = object : NodeSelectionDialogListenerAdapter(
                activity = this,
            ) {
                override fun trySelectNode(
                    self: NodeSelectionBottomSheetDialog,
                    node: Node
                ): Boolean {
                    return PreferenceUtils.setNode(this@SettingsActivity, node).fold(
                        {
                            selectNodeButton.text = getString(R.string.node_button_text, node.name)
                            Timber.i("Node selected")
                            val proxy = PreferenceUtils.getProxyIfEnabled(this@SettingsActivity) ?: ""
                            walletService?.setDaemonAddress(node.address, node.username ?: "", node.password ?: "", node.trusted, proxy)
                            true
                        },
                        {
                            Timber.e(it, "Failed to select node")
                            Toast.makeText(this@SettingsActivity, "Failed to select node", Toast.LENGTH_SHORT).show()
                            false
                        }
                    )
                }
            }

            val dialog = NodeSelectionBottomSheetDialog(sortOrder, nodes, node, listener)
            dialog.show(supportFragmentManager, "node_selection_dialog")
        }

        displaySeedButton.setOnClickListener {
            walletService?.getWallet()?.let { wallet ->
                val passwordDialog = PasswordBottomSheetDialog(wallet.info.path, false, object : PasswordBottomSheetDialog.Listener {
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

        saveProxyButton.setOnClickListener {
            val proxyAddress = walletProxyAddressEditText.text.toString()
            PreferenceUtils.setProxy(applicationContext, proxyAddress)
            if (proxySwitch.isChecked) {
                if (proxyAddress.isBlank()) {
                    Toast.makeText(this, "Proxy address is not set", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Activating proxy", Toast.LENGTH_SHORT).show()
                    walletService?.setProxy(proxyAddress)
                }
            } else if (walletService?.getProxy() != "") {
                Toast.makeText(this, "Deactivating proxy", Toast.LENGTH_SHORT).show()
                walletService?.setProxy("")
            } else {
                Toast.makeText(this, "Proxy settings saved", Toast.LENGTH_SHORT).show()
            }
        }

        addAccountButton.setOnClickListener {
            Toast.makeText(this, "Creating new account, please wait..", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "New account created", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAccountSet(index: Int) {
        runOnUiThread {
            updateAccountAdapter()
            Toast.makeText(this, "Account changed to #${index}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Wallet is not opened yet", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onProxyUpdated(proxy: String, success: Boolean) {
        runOnUiThread {
            if (proxy.isBlank()) {
                if (success) {
                    Toast.makeText(this, "Proxy deactivated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to deactivate proxy", Toast.LENGTH_SHORT).show()
                }
            } else {
                if (success) {
                    Toast.makeText(this, "Proxy activated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to activate proxy", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
