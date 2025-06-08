package org.monfluo.wallet

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import org.monfluo.wallet.data.DefaultNode
import org.monfluo.wallet.data.Node
import org.monfluo.wallet.data.SortOrder
import org.monfluo.wallet.fragment.dialog.NodeSelectionBottomSheetDialog
import org.monfluo.wallet.listener.NodeSelectionDialogListenerAdapter
import org.monfluo.wallet.model.WalletManager
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.acitivity.MoneroActivity
import timber.log.Timber

class ApplicationSettingsActivity : MoneroActivity() {

    private lateinit var walletProxyAddressEditText: EditText
    private lateinit var selectNodeButton: Button
    private lateinit var multiWalletModeSwitch: SwitchCompat
    private lateinit var multiAccountModeSwitch: SwitchCompat
    private lateinit var streetModeSwitch: SwitchCompat
    private lateinit var allowFeeOverrideSwitch: SwitchCompat
    private lateinit var allowCopyingSecretsToClipboardSwitch: SwitchCompat
    private lateinit var showTransferNotificationsSwitch: SwitchCompat
    private lateinit var showIconOnQrCodesSwitch: SwitchCompat
    private lateinit var autoRotateAddressesSwitch: SwitchCompat
    private lateinit var proxySwitch: SwitchCompat
    private lateinit var proxySettingsLayout: ConstraintLayout
    private lateinit var saveProxyButton: Button
    private lateinit var aboutMonfluoTextView: TextView
    private lateinit var buildApplicationIdTextView: TextView
    private lateinit var buildApplicationVersionTextView: TextView
    private lateinit var buildMoneroVersionTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_application_settings)

        selectNodeButton = findViewById(R.id.select_node_button)
        multiWalletModeSwitch = findViewById(R.id.enable_multiple_wallets_switch)
        multiAccountModeSwitch = findViewById(R.id.enable_multiple_accounts_switch)
        streetModeSwitch = findViewById(R.id.street_mode_switch)
        allowFeeOverrideSwitch = findViewById(R.id.allow_fee_override_switch)
        allowCopyingSecretsToClipboardSwitch = findViewById(R.id.allow_copying_secrets_to_clipboard_switch)
        showTransferNotificationsSwitch = findViewById(R.id.show_transfer_notifications_switch)
        showIconOnQrCodesSwitch = findViewById(R.id.show_icon_on_qr_codes_switch)
        autoRotateAddressesSwitch = findViewById(R.id.auto_rotate_addresses_switch)
        proxySwitch = findViewById(R.id.proxy_switch)
        proxySettingsLayout = findViewById(R.id.wallet_proxy_settings_layout)
        walletProxyAddressEditText = findViewById(R.id.wallet_proxy_address_edittext)
        saveProxyButton = findViewById(R.id.save_proxy_button)
        aboutMonfluoTextView = findViewById(R.id.about_monfluo_textview)
        buildApplicationIdTextView = findViewById(R.id.monfluo_build_application_id_textview)
        buildApplicationVersionTextView = findViewById(R.id.monfluo_build_application_version_textview)
        buildMoneroVersionTextView = findViewById(R.id.monfluo_build_monero_version_textview)

        val isMultiWalletMode = PreferenceUtils.isMultiWalletMode(applicationContext)
        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(applicationContext)
        val isStreetMode = PreferenceUtils.isStreetMode(applicationContext)
        val isAllowFeeOverride = PreferenceUtils.isAllowFeeOverride(applicationContext)
        val isAllowCopyingSecrets = PreferenceUtils.isAllowCopyingSecrets(applicationContext)
        val isShowTransferNotifications = PreferenceUtils.isShowTransferNotifications(applicationContext)
        val isShowIconOnQrCodes = PreferenceUtils.isShowIconOnQrCodes(applicationContext)
        val isAutoRotateAddresses = PreferenceUtils.isAutoRotateAddresses(applicationContext)
        val isUseProxy = PreferenceUtils.isUseProxy(applicationContext)

        multiWalletModeSwitch.isChecked = isMultiWalletMode
        multiAccountModeSwitch.isChecked = isMultiAccountMode
        streetModeSwitch.isChecked = isStreetMode
        allowFeeOverrideSwitch.isChecked = isAllowFeeOverride
        allowCopyingSecretsToClipboardSwitch.isChecked = isAllowCopyingSecrets
        showTransferNotificationsSwitch.isChecked = isShowTransferNotifications
        showIconOnQrCodesSwitch.isChecked = isShowIconOnQrCodes
        autoRotateAddressesSwitch.isChecked = isAutoRotateAddresses
        proxySwitch.isChecked = isUseProxy

        walletProxyAddressEditText.setText(PreferenceUtils.getProxy(this) ?: "")

        val node = PreferenceUtils.getOrSetDefaultNode(this, DefaultNode.defaultNode())
        selectNodeButton.text = getString(R.string.node_button_text, node.name)

        aboutMonfluoTextView.movementMethod = LinkMovementMethod.getInstance()

        buildApplicationIdTextView.text = BuildConfig.APPLICATION_ID
        buildApplicationVersionTextView.text = BuildConfig.VERSION_NAME
        buildMoneroVersionTextView.text = WalletManager.moneroVersion()

        bindListeners()
    }

    private fun bindListeners() {
        multiWalletModeSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setMultiWalletMode(applicationContext, b)
        }
        multiAccountModeSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setMultiAccountMode(applicationContext, b)
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
        showIconOnQrCodesSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setShowIconOnQrCodes(applicationContext, b)
        }
        autoRotateAddressesSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setAutoRotateAddresses(applicationContext, b)
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
                    return PreferenceUtils.setNode(this@ApplicationSettingsActivity, node).fold(
                        {
                            selectNodeButton.text = getString(R.string.node_button_text, node.name)
                            Timber.i("Node selected")
                            val proxy = PreferenceUtils.getProxyIfEnabled(this@ApplicationSettingsActivity) ?: ""
                            walletService?.setDaemonAddress(node.address, node.username ?: "", node.password ?: "", node.trusted, proxy)
                            true
                        },
                        {
                            Timber.e(it, "Failed to select node")
                            Toast.makeText(this@ApplicationSettingsActivity, getText(R.string.toast_failed_to_select_node), Toast.LENGTH_SHORT).show()
                            false
                        }
                    )
                }
            }

            val dialog = NodeSelectionBottomSheetDialog(sortOrder, nodes, node, listener)
            dialog.show(supportFragmentManager, "node_selection_dialog")
        }

        saveProxyButton.setOnClickListener {
            val proxyAddress = walletProxyAddressEditText.text.toString()
            PreferenceUtils.setProxy(applicationContext, proxyAddress)
            if (proxySwitch.isChecked) {
                if (proxyAddress.isBlank()) {
                    Toast.makeText(this, getText(R.string.toast_proxy_address_not_set), Toast.LENGTH_SHORT).show()
                } else {
                    val walletService = walletService
                    if (walletService != null) {
                        Toast.makeText(this, getText(R.string.toast_activating_proxy), Toast.LENGTH_SHORT).show()
                        walletService.setProxy(proxyAddress)
                    } else {
                        Toast.makeText(this, getText(R.string.toast_proxy_saved), Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (!walletService?.getProxy().isNullOrEmpty()) {
                Toast.makeText(this, getText(R.string.toast_deactivating_proxy), Toast.LENGTH_SHORT).show()
                walletService?.setProxy("")
            } else {
                Toast.makeText(this, getText(R.string.toast_proxy_saved), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // overridden to not finish the activity if wallet service is not active
    override fun onWalletServiceNotFound() {
        Timber.d("Wallet Service not found")
    }

    // overridden to not finish the activity if wallet service gets disconnected
    override fun onWalletServiceDisconnected() {
        Timber.d("Wallet Service disconnected")
    }

    override fun onProxyUpdated(proxy: String, success: Boolean) {
        runOnUiThread {
            if (proxy.isBlank()) {
                if (success) {
                    Toast.makeText(this, getText(R.string.toast_proxy_deactivated), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getText(R.string.toast_failed_to_deactivate_proxy), Toast.LENGTH_SHORT).show()
                }
            } else {
                if (success) {
                    Toast.makeText(this, getText(R.string.toast_proxy_activated), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getText(R.string.toast_failed_to_activate_proxy), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
