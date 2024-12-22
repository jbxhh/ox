package net.mynero.wallet

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.material.progressindicator.CircularProgressIndicator
import net.mynero.wallet.data.DefaultNode
import net.mynero.wallet.data.Node
import net.mynero.wallet.fragment.dialog.NodeSelectionBottomSheetDialog
import net.mynero.wallet.fragment.dialog.WalletKeysBottomSheetDialog
import net.mynero.wallet.listener.NodeSelectionDialogListenerAdapter
import net.mynero.wallet.model.EnumTorState
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.ProxyService
import net.mynero.wallet.service.wallet.WalletServiceObserver
import net.mynero.wallet.service.wallet.WalletService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.PreferenceUtils
import timber.log.Timber

class SettingsActivity : AppCompatActivity(), WalletServiceObserver {

    private val viewModel: SettingsViewModel by viewModels()

    private var walletService: WalletService? = null

    private lateinit var walletProxyAddressEditText: EditText
    private lateinit var walletProxyPortEditText: EditText
    private lateinit var selectNodeButton: Button
    private lateinit var streetModeSwitch: SwitchCompat
    private lateinit var allowFeeOverrideSwitch: SwitchCompat
    private lateinit var useBundledTor: CheckBox
    private lateinit var displaySeedButton: Button
    private lateinit var displayUtxosButton: Button
    private lateinit var proxySwitch: SwitchCompat
    private lateinit var saveProxyButton: Button

    private val askForWalletPasswordAndDisplayWalletKeys = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val password = result.data?.extras?.getString(Constants.EXTRA_WALLET_PASSWORD)
            password?.let { displaySeedDialog(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        displaySeedButton = findViewById(R.id.display_seed_button)
        displayUtxosButton = findViewById(R.id.display_utxos_button)
        selectNodeButton = findViewById(R.id.select_node_button)
        streetModeSwitch = findViewById(R.id.street_mode_switch)
        allowFeeOverrideSwitch = findViewById(R.id.allow_fee_override_switch)
        proxySwitch = findViewById(R.id.proxy_switch)
        val proxySettingsLayout = findViewById<ConstraintLayout>(R.id.wallet_proxy_settings_layout)
        walletProxyAddressEditText = findViewById(R.id.wallet_proxy_address_edittext)
        walletProxyPortEditText = findViewById(R.id.wallet_proxy_port_edittext)
        useBundledTor = findViewById(R.id.bundled_tor_checkbox)
        saveProxyButton = findViewById(R.id.save_proxy_button)

        val cachedProxyAddress = ProxyService.instance?.proxyAddress ?: return
        val cachedProxyPort = ProxyService.instance?.proxyPort ?: return
        val cachedUsingProxy = ProxyService.instance?.usingProxy == true
        val cachedUsingBundledTor = ProxyService.instance?.useBundledTor == true

//        walletProxyPortEditText.isEnabled = !cachedUsingBundledTor
//        walletProxyAddressEditText.isEnabled = !cachedUsingBundledTor
        proxySettingsLayout.visibility = View.VISIBLE

        streetModeSwitch.isChecked = PreferenceUtils.isStreetMode(applicationContext)
        allowFeeOverrideSwitch.isChecked = PreferenceUtils.isAllowFeeOverride(applicationContext)
        useBundledTor.isChecked = cachedUsingBundledTor
        proxySwitch.isChecked = cachedUsingProxy
        updateProxy(cachedProxyAddress, cachedProxyPort)

        val node = PreferenceUtils.getOrSetDefaultNode(this, DefaultNode.defaultNode())
        selectNodeButton.text = getString(R.string.node_button_text, node.name)

        bindListeners()
        bindObservers()

        bindService(Intent(applicationContext, WalletService::class.java), connection, 0)
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val walletService = (service as WalletService.WalletServiceBinder).service
            this@SettingsActivity.walletService = walletService
            walletService.addObserver(this@SettingsActivity)
        }

        override fun onServiceDisconnected(className: ComponentName) {
            walletService = null
        }
    }

    private fun bindListeners() {
        streetModeSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setStreetMode(applicationContext, b)
        }

        allowFeeOverrideSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PreferenceUtils.setAllowFeeOverride(applicationContext, b)
        }

        selectNodeButton.setOnClickListener {
            val nodes = PreferenceUtils.getOrSetDefaultNodes(this, DefaultNode.defaultNodes())
            val node = PreferenceUtils.getOrSetDefaultNode(this, DefaultNode.defaultNode())

            val listener = object : NodeSelectionDialogListenerAdapter(
                activity = this,
                nodes = nodes,
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

            val dialog = NodeSelectionBottomSheetDialog(nodes, node, listener)
            dialog.show(supportFragmentManager, "node_selection_dialog")
        }

        useBundledTor.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setUseBundledTor(isChecked)
        }

        displaySeedButton.setOnClickListener {
            val intent = Intent(this, PasswordActivity::class.java)
            // TODO: use real wallet name here
            intent.putExtra(Constants.EXTRA_WALLET_NAME, Constants.DEFAULT_WALLET_NAME)
            askForWalletPasswordAndDisplayWalletKeys.launch(intent)
        }

        displayUtxosButton.setOnClickListener {
            startActivity(Intent(this, EnotesActivity::class.java))
        }

        proxySwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            viewModel.setUseProxy(b)
        }

        saveProxyButton.setOnClickListener {
            val proxyAddress = walletProxyAddressEditText.text.toString()
            val proxyPort = walletProxyPortEditText.text.toString()
            val proxy = if (proxyAddress.isNotBlank() && proxyPort.isNotBlank()) "$proxyAddress:$proxyPort" else ""
            PreferenceUtils.setProxy(applicationContext, proxyPort)
            if (proxySwitch.isChecked) {
                Toast.makeText(this, "Activating proxy", Toast.LENGTH_SHORT).show()
                walletService?.setProxy(proxy)
            } else {
                if (walletService?.getProxy() != "") {
                    Toast.makeText(this, "Deactivating proxy", Toast.LENGTH_SHORT).show()
                    walletService?.setProxy("")
                }
            }
        }
    }

    private fun bindObservers() {
        viewModel.useProxy.observe(this) { useProxy ->
            useBundledTor.isEnabled = useProxy

//            Utils.refreshProxy(walletProxyAddressEditText.text.toString(), walletProxyPortEditText.text.toString())
        }

        viewModel.useBundledTor.observe(this) { isChecked ->
            // TODO: bundled tor support
        }

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        val indicatorCircle =
            findViewById<CircularProgressIndicator>(R.id.settings_tor_loading_progressindicator)
        val torIcon = findViewById<ImageView>(R.id.settings_tor_icon)

        samouraiTorManager?.getTorStateLiveData()?.observe(this) { state ->
            samouraiTorManager.getProxy()?.address()?.let { socketAddress ->
                if (socketAddress.toString().isEmpty()) return@let
                if (viewModel.useProxy.value == true && viewModel.useBundledTor.value == true) {
                    torIcon?.visibility = View.VISIBLE
                    indicatorCircle?.visibility = View.INVISIBLE
                    val proxyString = socketAddress.toString().substring(1)
                    val address = proxyString.split(":")[0]
                    val port = proxyString.split(":")[1]
                    updateProxy(address, port)
                }
            }

            indicatorCircle?.isIndeterminate = state.progressIndicator == 0
            indicatorCircle?.progress = state.progressIndicator

            when (state.state) {
                EnumTorState.OFF -> {
                    torIcon?.visibility = View.INVISIBLE
                    indicatorCircle?.visibility = View.INVISIBLE
                }

                EnumTorState.STARTING, EnumTorState.STOPPING -> {
                    torIcon?.visibility = View.INVISIBLE
                    indicatorCircle?.visibility = View.VISIBLE
                }

                else -> {}
            }
        }
    }

    private fun updateProxy(address: String, port: String) {
        walletProxyPortEditText.setText(port)
        walletProxyAddressEditText.setText(address)
//        Utils.refreshProxy(address, port)
    }

    private fun displaySeedDialog(password: String) {
        val wallet = walletService!!.getWalletOrThrow()
        val usesOffset = PrefService.instance.getBoolean(Constants.PREF_USES_OFFSET, false)
        val seed = wallet.getSeed(if (usesOffset) password else "")
        val privateViewKey = wallet.getSecretViewKey()
        val restoreHeight = wallet.getRestoreHeight()

        val informationDialog = WalletKeysBottomSheetDialog(usesOffset, seed, privateViewKey, restoreHeight)
        informationDialog.show(supportFragmentManager, "information_seed_dialog")
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

class SettingsViewModel : ViewModel() {
    private val _useProxy = MutableLiveData(false)
    val useProxy: LiveData<Boolean> = _useProxy
    private val _useBundledTor = MutableLiveData(false)
    val useBundledTor: LiveData<Boolean> = _useBundledTor

    init {
        _useProxy.value = ProxyService.instance?.usingProxy
        _useBundledTor.value = ProxyService.instance?.useBundledTor
    }

    fun setUseProxy(use: Boolean) {
        _useProxy.value = use
        ProxyService.instance?.usingProxy = use

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        if (use && ProxyService.instance?.useBundledTor == true) {
            samouraiTorManager?.start()
        } else {
            samouraiTorManager?.stop()
        }
    }

    fun setUseBundledTor(use: Boolean) {
        _useBundledTor.value = use
        ProxyService.instance?.useBundledTor = use

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        if (use && ProxyService.instance?.usingProxy == true) {
            samouraiTorManager?.start()
        } else {
            samouraiTorManager?.stop()
        }
    }
}