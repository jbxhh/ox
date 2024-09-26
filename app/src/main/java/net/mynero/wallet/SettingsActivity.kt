package net.mynero.wallet

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.progressindicator.CircularProgressIndicator
import net.mynero.wallet.data.Node
import net.mynero.wallet.data.Node.Companion.fromJson
import net.mynero.wallet.fragment.dialog.AddNodeBottomSheetDialog
import net.mynero.wallet.fragment.dialog.EditNodeBottomSheetDialog
import net.mynero.wallet.fragment.dialog.NodeSelectionBottomSheetDialog
import net.mynero.wallet.fragment.dialog.WalletKeysBottomSheetDialog
import net.mynero.wallet.model.EnumTorState
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.BalanceService
import net.mynero.wallet.service.HistoryService
import net.mynero.wallet.service.MoneroHandlerThread
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.ProxyService
import net.mynero.wallet.util.Constants
import org.json.JSONArray
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var mViewModel: SettingsViewModel
    private lateinit var walletProxyAddressEditText: EditText
    private lateinit var walletProxyPortEditText: EditText
    private lateinit var selectNodeButton: Button
    private lateinit var streetModeSwitch: SwitchCompat
    private lateinit var monerochanSwitch: SwitchCompat
    private lateinit var useBundledTor: CheckBox
    private lateinit var displaySeedButton: Button
    private lateinit var displayUtxosButton: Button
    private lateinit var torSwitch: SwitchCompat

    private val askForWalletPasswordAndDisplayWalletKeys = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val password = result.data?.extras?.getString(Constants.EXTRA_WALLET_PASSWORD)
            password?.let { displaySeedDialog(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        mViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        displaySeedButton = findViewById(R.id.display_seed_button)
        displayUtxosButton = findViewById(R.id.display_utxos_button)
        selectNodeButton = findViewById(R.id.select_node_button)
        streetModeSwitch = findViewById(R.id.street_mode_switch)
        monerochanSwitch = findViewById(R.id.monerochan_switch)
        torSwitch = findViewById(R.id.tor_switch)
        val proxySettingsLayout = findViewById<ConstraintLayout>(R.id.wallet_proxy_settings_layout)
        walletProxyAddressEditText = findViewById(R.id.wallet_proxy_address_edittext)
        walletProxyPortEditText = findViewById(R.id.wallet_proxy_port_edittext)
        useBundledTor = findViewById(R.id.bundled_tor_checkbox)

        val cachedProxyAddress = ProxyService.instance?.proxyAddress ?: return
        val cachedProxyPort = ProxyService.instance?.proxyPort ?: return
        val cachedUsingProxy = ProxyService.instance?.usingProxy == true
        val cachedUsingBundledTor = ProxyService.instance?.useBundledTor == true

        walletProxyPortEditText.isEnabled = !cachedUsingBundledTor && cachedUsingProxy
        walletProxyAddressEditText.isEnabled = !cachedUsingBundledTor && cachedUsingProxy
        proxySettingsLayout.visibility = View.VISIBLE

        streetModeSwitch.isChecked =
            PrefService.instance?.getBoolean(Constants.PREF_STREET_MODE, false) == true
        monerochanSwitch.isChecked =
            PrefService.instance?.getBoolean(Constants.PREF_MONEROCHAN, Constants.DEFAULT_PREF_MONEROCHAN) == true
        useBundledTor.isChecked = cachedUsingBundledTor
        torSwitch.isChecked = cachedUsingProxy
        updateProxy(cachedProxyAddress, cachedProxyPort)

        val node = PrefService.instance?.node // shouldn't use default value here
        selectNodeButton.text = getString(R.string.node_button_text, node?.address)

        bindListeners()
        bindObservers()
    }

    private fun bindListeners() {
        val activity = this
        val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                refreshProxy()
                finish()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        streetModeSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PrefService.instance?.edit()?.putBoolean(Constants.PREF_STREET_MODE, b)?.apply()
            BalanceService.instance?.refreshBalance()
        }

        monerochanSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PrefService.instance?.edit()?.putBoolean(Constants.PREF_MONEROCHAN, b)?.apply()
            HistoryService.instance?.refreshHistory()
        }

        selectNodeButton.setOnClickListener {
            supportFragmentManager.let { fragmentManager ->
                val dialog = NodeSelectionBottomSheetDialog()
                dialog.listener = object : NodeSelectionBottomSheetDialog.NodeSelectionDialogListener {
                    override fun onNodeSelected() {
                        val node = PrefService.instance?.node
                        selectNodeButton.text = getString(R.string.node_button_text, node?.address)
                        refreshProxy()

                        runOnUiThread {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.node_selected, node?.name ?: node?.host),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onClickedEditNode(node: Node?) {
                        val editNodeDialog = EditNodeBottomSheetDialog()
                        editNodeDialog.listener = object : EditNodeBottomSheetDialog.EditNodeListener {
                            override fun onNodeDeleted(node: Node?) {
                                try {
                                    val nodesArray = PrefService.instance?.getString(Constants.PREF_CUSTOM_NODES, "[]")
                                    val jsonArray = JSONArray(nodesArray)
                                    for (i in 0 until jsonArray.length()) {
                                        val nodeJsonObject = jsonArray.getJSONObject(i)
                                        val savedNode = fromJson(nodeJsonObject)
                                        if (savedNode?.toNodeString() == node?.toNodeString()) jsonArray.remove(i)
                                    }
                                    saveNodesAndReopen(jsonArray)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            override fun onNodeEdited(oldNode: Node?, newNode: Node?) {
                                try {
                                    val nodesArray = PrefService.instance?.getString(Constants.PREF_CUSTOM_NODES, "[]")
                                    val jsonArray = JSONArray(nodesArray)
                                    for (i in 0 until jsonArray.length()) {
                                        val nodeJsonObject = jsonArray.getJSONObject(i)
                                        val savedNode = fromJson(nodeJsonObject)
                                        if (savedNode?.toNodeString() == oldNode?.toNodeString()) jsonArray.put(
                                            i,
                                            newNode?.toJson()
                                        )
                                    }
                                    saveNodesAndReopen(jsonArray)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                        }
                        editNodeDialog.node = node
                        editNodeDialog.show(fragmentManager, "edit_node_dialog")
                    }

                    override fun onClickedAddNode() {
                        activity.supportFragmentManager.let { fragmentManager ->
                            val addNodeDialog = AddNodeBottomSheetDialog()
                            addNodeDialog.listener = object : AddNodeBottomSheetDialog.AddNodeListener {
                                override fun onNodeAdded() {}
                            }
                            addNodeDialog.show(fragmentManager, "add_node_dialog")
                        }
                    }

                }
                dialog.show(fragmentManager, "node_selection_dialog")
            }
        }

        useBundledTor.setOnCheckedChangeListener { _, isChecked ->
            mViewModel.setUseBundledTor(isChecked)
        }

        displaySeedButton.setOnClickListener {
            val usesPassword =
                PrefService.instance?.getBoolean(Constants.PREF_USES_PASSWORD, false) == true
            if (usesPassword) {
                val intent = Intent(this, PasswordActivity::class.java)
                askForWalletPasswordAndDisplayWalletKeys.launch(intent)
            } else {
                displaySeedDialog("")
            }
        }

        displayUtxosButton.setOnClickListener {
            startActivity(Intent(this, UtxosActivity::class.java))
        }

        torSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            mViewModel.setUseProxy(b)
        }
    }

    private fun bindObservers() {
        mViewModel.useProxy.observe(this) { useProxy ->
            useBundledTor.isEnabled = useProxy
            walletProxyPortEditText.isEnabled = useProxy && mViewModel.useBundledTor.value == false
            walletProxyAddressEditText.isEnabled = useProxy && mViewModel.useBundledTor.value == false

            refreshProxy()
        }

        mViewModel.useBundledTor.observe(this) { isChecked ->
            walletProxyPortEditText.isEnabled = !isChecked && mViewModel.useProxy.value == true
            walletProxyAddressEditText.isEnabled = !isChecked && mViewModel.useProxy.value == true
        }

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        val indicatorCircle =
            findViewById<CircularProgressIndicator>(R.id.settings_tor_loading_progressindicator)
        val torIcon = findViewById<ImageView>(R.id.settings_tor_icon)

        samouraiTorManager?.getTorStateLiveData()?.observe(this) { state ->
            samouraiTorManager.getProxy()?.address()?.let { socketAddress ->
                if (socketAddress.toString().isEmpty()) return@let
                if (mViewModel.useProxy.value == true && mViewModel.useBundledTor.value == true) {
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
        refreshProxy()
    }

    private fun refreshProxy() {
        val proxyAddress = walletProxyAddressEditText.text.toString()
        val proxyPort = walletProxyPortEditText.text.toString()
        val savedProxyAddress = ProxyService.instance?.proxyAddress
        val savedProxyPort = ProxyService.instance?.proxyPort
        val currentWalletProxy = WalletManager.instance?.proxy
        val newProxy = "$proxyAddress:$proxyPort"
        if (proxyAddress != savedProxyAddress || proxyPort != savedProxyPort || (newProxy != currentWalletProxy && newProxy != ":"))
            ProxyService.instance?.updateProxy(proxyAddress, proxyPort)
    }

    private fun displaySeedDialog(password: String) {
        val informationDialog = WalletKeysBottomSheetDialog()
        informationDialog.password = password
        informationDialog.show(supportFragmentManager, "information_seed_dialog")
    }

    private fun saveNodesAndReopen(jsonArray: JSONArray) {
        PrefService.instance?.edit()?.putString(Constants.PREF_CUSTOM_NODES, jsonArray.toString())
            ?.apply()
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