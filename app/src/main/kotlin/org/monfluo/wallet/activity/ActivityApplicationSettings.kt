package org.monfluo.wallet.activity

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.BuildConfig
import org.monfluo.wallet.R
import org.monfluo.wallet.activity.ActivityNodeList
import org.monfluo.wallet.model.MoneroNetworkType
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.model.WalletManager
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.component.ActivityMonero
import timber.log.Timber
import kotlin.math.max

class ActivityApplicationSettings : ActivityMonero() {
    private val viewModel: ActivityApplicationSettingsViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView
    private lateinit var walletProxyAddressEditText: EditText
    private lateinit var selectNodeButton: Button
    private lateinit var multiWalletModeSwitch: SwitchCompat
    private lateinit var multiAccountModeSwitch: SwitchCompat
    private lateinit var streetModeSwitch: SwitchCompat
    private lateinit var allowFeeOverrideSwitch: SwitchCompat
    private lateinit var allowCopyingSecretsToClipboardSwitch: SwitchCompat
    private lateinit var enableSecureFlagSwitch: SwitchCompat
    private lateinit var hideNotificationContentSwitch: SwitchCompat
    private lateinit var showTransferNotificationsSwitch: SwitchCompat
    private lateinit var showIconOnQrCodesSwitch: SwitchCompat
    private lateinit var autoRotateAddressesSwitch: SwitchCompat
    private lateinit var enoteManagementSwitch: SwitchCompat
    private lateinit var balanceTapTogglesStreetModeSwitch: SwitchCompat
    private lateinit var maxBrightnessQrCodesSwitch: SwitchCompat
    private lateinit var forceUriFormatQrCodesSwitch: SwitchCompat
    private lateinit var proxySwitch: SwitchCompat
    private lateinit var proxySettingsLayout: ConstraintLayout
    private lateinit var saveProxyButton: Button
    private lateinit var aboutMonfluoTextView: TextView
    private lateinit var buildApplicationIdTextView: TextView
    private lateinit var buildApplicationVersionTextView: TextView
    private lateinit var buildMoneroVersionTextView: TextView
    private lateinit var enableAddressBookSwitch: SwitchCompat
    private lateinit var developerModeSwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_application_settings)

        layoutView = findViewById(R.id.activity_application_settings_layout)
        backImageView = findViewById(R.id.back_imageview)
        selectNodeButton = findViewById(R.id.select_node_button)
        multiWalletModeSwitch = findViewById(R.id.enable_multiple_wallets_switch)
        multiAccountModeSwitch = findViewById(R.id.enable_multiple_accounts_switch)
        streetModeSwitch = findViewById(R.id.street_mode_switch)
        allowFeeOverrideSwitch = findViewById(R.id.manual_fee_selection_switch)
        allowCopyingSecretsToClipboardSwitch = findViewById(R.id.allow_copying_secrets_to_clipboard_switch)
        showTransferNotificationsSwitch = findViewById(R.id.show_transfer_notifications_switch)
        showIconOnQrCodesSwitch = findViewById(R.id.show_icon_on_qr_codes_switch)
        autoRotateAddressesSwitch = findViewById(R.id.auto_rotate_addresses_switch)
        enoteManagementSwitch = findViewById(R.id.enable_enote_management_switch)
        balanceTapTogglesStreetModeSwitch = findViewById(R.id.balance_tap_toggles_street_mode_switch)
        maxBrightnessQrCodesSwitch = findViewById(R.id.max_brightness_qr_codes_switch)
        forceUriFormatQrCodesSwitch = findViewById(R.id.force_uri_format_qr_codes_switch)
        enableSecureFlagSwitch = findViewById(R.id.enable_secure_flag_switch)
        hideNotificationContentSwitch = findViewById(R.id.hide_notification_content_switch)
        proxySwitch = findViewById(R.id.proxy_switch)
        proxySettingsLayout = findViewById(R.id.wallet_proxy_settings_layout)
        walletProxyAddressEditText = findViewById(R.id.wallet_proxy_address_edittext)
        saveProxyButton = findViewById(R.id.save_proxy_button)
        aboutMonfluoTextView = findViewById(R.id.about_monfluo_textview)
        buildApplicationIdTextView = findViewById(R.id.monfluo_build_application_id_textview)
        buildApplicationVersionTextView = findViewById(R.id.monfluo_build_application_version_textview)
        buildMoneroVersionTextView = findViewById(R.id.monfluo_build_monero_version_textview)
        enableAddressBookSwitch = findViewById(R.id.enable_address_book_switch)
        developerModeSwitch = findViewById(R.id.developer_mode_switch)

        val isMultiWalletMode = PreferenceUtils.isMultiWalletMode(applicationContext)
        val isMultiAccountMode = PreferenceUtils.isMultiAccountMode(applicationContext)
        val isStreetMode = PreferenceUtils.isStreetMode(applicationContext)
        val isAllowFeeOverride = PreferenceUtils.isAllowFeeOverride(applicationContext)
        val isAllowCopyingSecrets = PreferenceUtils.isAllowCopyingSecrets(applicationContext)
        val isShowTransferNotifications = PreferenceUtils.isShowTransferNotifications(applicationContext)
        val isShowIconOnQrCodes = PreferenceUtils.isShowIconOnQrCodes(applicationContext)
        val isAutoRotateAddresses = PreferenceUtils.isAutoRotateAddresses(applicationContext)
        val isEnoteManagementEnabled = PreferenceUtils.isEnableEnoteManagement(applicationContext)
        val isBalanceTapTogglesStreetMode = PreferenceUtils.isBalanceTapTogglesStreetMode(applicationContext)
        val isMaxBrightnessQrCodes = PreferenceUtils.isMaxBrightnessQrCodes(applicationContext)
        val isForceUriFormatQrCodes = PreferenceUtils.isForceUriFormatQrCodes(applicationContext)
        val isEnableSecureFlag = PreferenceUtils.isSecureFlagEnabled(applicationContext)
        val isHideNotificationContent = PreferenceUtils.isHideNotificationContent(applicationContext)
        val isAddressBookEnabled = PreferenceUtils.isEnableAddressBook(applicationContext)
        val isDeveloperModeEnabled = PreferenceUtils.isDeveloperModeEnabled(applicationContext)
        val isUseProxy = PreferenceUtils.isUseProxy(applicationContext)

        multiWalletModeSwitch.isChecked = isMultiWalletMode
        multiAccountModeSwitch.isChecked = isMultiAccountMode
        streetModeSwitch.isChecked = isStreetMode
        allowFeeOverrideSwitch.isChecked = isAllowFeeOverride
        allowCopyingSecretsToClipboardSwitch.isChecked = isAllowCopyingSecrets
        showTransferNotificationsSwitch.isChecked = isShowTransferNotifications
        showIconOnQrCodesSwitch.isChecked = isShowIconOnQrCodes
        autoRotateAddressesSwitch.isChecked = isAutoRotateAddresses
        enoteManagementSwitch.isChecked = isEnoteManagementEnabled
        balanceTapTogglesStreetModeSwitch.isChecked = isBalanceTapTogglesStreetMode
        maxBrightnessQrCodesSwitch.isChecked = isMaxBrightnessQrCodes
        forceUriFormatQrCodesSwitch.isChecked = isForceUriFormatQrCodes
        enableSecureFlagSwitch.isChecked = isEnableSecureFlag
        hideNotificationContentSwitch.isChecked = isHideNotificationContent
        enableAddressBookSwitch.isChecked = isAddressBookEnabled
        developerModeSwitch.isChecked = isDeveloperModeEnabled
        proxySwitch.isChecked = isUseProxy

        walletProxyAddressEditText.setText(PreferenceUtils.getProxy(this) ?: "")

        aboutMonfluoTextView.movementMethod = LinkMovementMethod.getInstance()

        buildApplicationIdTextView.text = BuildConfig.APPLICATION_ID
        buildApplicationVersionTextView.text = getString(R.string.settings_build_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        buildMoneroVersionTextView.text = WalletManager.moneroVersion()

        bindWindowInsetsListener()
        bindListeners()
        bindObservers()
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
        enoteManagementSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setEnableEnoteManagement(applicationContext, b)
        }
        balanceTapTogglesStreetModeSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setBalanceTapTogglesStreetMode(applicationContext, b)
        }
        maxBrightnessQrCodesSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setMaxBrightnessQrCodes(applicationContext, b)
        }
        forceUriFormatQrCodesSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setForceUriFormatQrCodes(applicationContext, b)
        }
        enableSecureFlagSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setSecureFlagEnabled(applicationContext, b)
            if (b) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                Timber.d("Adding FLAG_SECURE to window from settings")
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                Timber.d("Clearing FLAG_SECURE from window from settings")
            }
        }
        hideNotificationContentSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setHideNotificationContent(applicationContext, b)
        }
        proxySwitch.setOnCheckedChangeListener { _, b: Boolean ->
            PreferenceUtils.setUseProxy(applicationContext, b)
        }
        enableAddressBookSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setEnableAddressBook(applicationContext, b)
        }
        developerModeSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setDeveloperModeEnabled(applicationContext, b)
        }

        selectNodeButton.setOnClickListener {
            startActivity(Intent(this, ActivityNodeList::class.java))
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

    private fun bindObservers() {
        viewModel.walletNetworkType.observe(this) { networkType ->
            val node = PreferenceUtils.getDefaultNode(this, networkType)
            selectNodeButton.text = getString(R.string.node_button_text, node?.name ?: "null")
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

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService.getWallet()?.let { wallet ->
            viewModel.walletNetworkType.value = wallet.networkType()
        }
    }

    override fun onWalletUpdated(wallet: Wallet) {
        viewModel.walletNetworkType.postValue(wallet.networkType())
    }

    override fun onProxyUpdated(
        proxy: String,
        success: Boolean,
    ) {
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

internal class ActivityApplicationSettingsViewModel : ViewModel() {
    val walletNetworkType: MutableLiveData<MoneroNetworkType> = MutableLiveData(MoneroNetworkType.NetworkType_Mainnet)
}
