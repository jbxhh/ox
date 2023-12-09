package net.mynero.wallet.fragment.settings

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.progressindicator.CircularProgressIndicator
import net.mynero.wallet.R
import net.mynero.wallet.data.Node
import net.mynero.wallet.data.Node.Companion.fromJson
import net.mynero.wallet.fragment.dialog.AddNodeBottomSheetDialog
import net.mynero.wallet.fragment.dialog.AddNodeBottomSheetDialog.AddNodeListener
import net.mynero.wallet.fragment.dialog.EditNodeBottomSheetDialog
import net.mynero.wallet.fragment.dialog.EditNodeBottomSheetDialog.EditNodeListener
import net.mynero.wallet.fragment.dialog.NodeSelectionBottomSheetDialog
import net.mynero.wallet.fragment.dialog.NodeSelectionBottomSheetDialog.NodeSelectionDialogListener
import net.mynero.wallet.fragment.dialog.PasswordBottomSheetDialog
import net.mynero.wallet.fragment.dialog.PasswordBottomSheetDialog.PasswordListener
import net.mynero.wallet.fragment.dialog.WalletKeysBottomSheetDialog
import net.mynero.wallet.model.EnumTorState
import net.mynero.wallet.service.BalanceService
import net.mynero.wallet.service.HistoryService
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.ProxyService
import net.mynero.wallet.util.Constants
import org.json.JSONArray

class SettingsFragment : Fragment(), PasswordListener, NodeSelectionDialogListener, AddNodeListener,
    EditNodeListener {
    private var mViewModel: SettingsViewModel? = null
    private var walletProxyAddressEditText: EditText? = null
    private var walletProxyPortEditText: EditText? = null
    private var selectNodeButton: Button? = null
    private var streetModeSwitch: SwitchCompat? = null
    private var monerochanSwitch: SwitchCompat? = null
    private var donationSwitch: SwitchCompat? = null
    private var useBundledTor: CheckBox? = null
    private var displaySeedButton: Button? = null
    private var displayUtxosButton: Button? = null
    private var torSwitch: SwitchCompat? = null
    private var proxySettingsLayout: ConstraintLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        displaySeedButton = view.findViewById(R.id.display_seed_button)
        displayUtxosButton = view.findViewById(R.id.display_utxos_button)
        selectNodeButton = view.findViewById(R.id.select_node_button)
        streetModeSwitch = view.findViewById(R.id.street_mode_switch)
        monerochanSwitch = view.findViewById(R.id.monerochan_switch)
        donationSwitch = view.findViewById(R.id.donate_per_tx_switch)
        torSwitch = view.findViewById(R.id.tor_switch)
        proxySettingsLayout = view.findViewById(R.id.wallet_proxy_settings_layout)
        walletProxyAddressEditText = view.findViewById(R.id.wallet_proxy_address_edittext)
        walletProxyPortEditText = view.findViewById(R.id.wallet_proxy_port_edittext)
        useBundledTor = view.findViewById(R.id.bundled_tor_checkbox)

        useBundledTor?.isChecked = ProxyService.instance?.useBundledTor == true
        walletProxyPortEditText?.visibility = if (useBundledTor?.isChecked == true) View.GONE else View.VISIBLE
        walletProxyAddressEditText?.visibility = if (useBundledTor?.isChecked == true) View.GONE else View.VISIBLE
        streetModeSwitch?.isChecked = PrefService.instance?.getBoolean(Constants.PREF_STREET_MODE, false) == true
        monerochanSwitch?.isChecked = PrefService.instance?.getBoolean(Constants.PREF_MONEROCHAN, true) == true
        donationSwitch?.isChecked = PrefService.instance?.getBoolean(Constants.PREF_DONATE_PER_TX, false) == true

        val usesProxy = ProxyService.instance?.usingProxy == true
        val cachedProxyAddress = ProxyService.instance?.proxyAddress ?: return
        val cachedProxyPort = ProxyService.instance?.proxyPort ?: return
        if (ProxyService.instance?.hasProxySet() == true) {
            initProxyStuff(cachedProxyAddress, cachedProxyPort)
        }
        torSwitch?.isChecked = usesProxy
        proxySettingsLayout?.visibility = if (usesProxy) View.VISIBLE else View.GONE
        val node = PrefService.instance?.node // shouldn't use default value here
        selectNodeButton?.text = getString(R.string.node_button_text, node?.address)

        bindListeners()
        bindObservers()
    }

    private fun bindListeners() {
        val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                refreshProxy()
                findNavController().popBackStack()
            }
        }
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, onBackPressedCallback)

        donationSwitch?.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PrefService.instance?.edit()?.putBoolean(Constants.PREF_DONATE_PER_TX, b)?.apply()
        }

        streetModeSwitch?.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PrefService.instance?.edit()?.putBoolean(Constants.PREF_STREET_MODE, b)?.apply()
            BalanceService.instance?.refreshBalance()
        }

        monerochanSwitch?.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PrefService.instance?.edit()?.putBoolean(Constants.PREF_MONEROCHAN, b)?.apply()
            HistoryService.instance?.refreshHistory()
        }

        selectNodeButton?.setOnClickListener {
            activity?.supportFragmentManager?.let { fragmentManager ->
                val dialog = NodeSelectionBottomSheetDialog()
                dialog.listener = this
                dialog.show(fragmentManager, "node_selection_dialog")
            }
        }

        useBundledTor?.setOnCheckedChangeListener { _, isChecked ->
            mViewModel?.setUseBundledTor(isChecked)
        }

        displaySeedButton?.setOnClickListener {
            val usesPassword =
                PrefService.instance?.getBoolean(Constants.PREF_USES_PASSWORD, false) == true
            if (usesPassword) {
                activity?.supportFragmentManager?.let { fragmentManager ->
                    val passwordDialog = PasswordBottomSheetDialog()
                    passwordDialog.canCancel = true
                    passwordDialog.listener = this
                    passwordDialog.show(fragmentManager, "password_dialog")
                }
            } else {
                displaySeedDialog("")
            }
        }

        displayUtxosButton?.setOnClickListener { navigate(R.id.nav_to_utxos) }

        torSwitch?.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            mViewModel?.setUseProxy(b)
        }
    }

    private fun bindObservers() {
        mViewModel?.useProxy?.observe(viewLifecycleOwner) { b ->
            if (b) {
                if (ProxyService.instance?.hasProxySet() == true) {
                    val proxyAddress = ProxyService.instance?.proxyAddress ?: return@observe
                    val proxyPort = ProxyService.instance?.proxyPort ?: return@observe
                    initProxyStuff(proxyAddress, proxyPort)
                }
                proxySettingsLayout?.visibility = View.VISIBLE
            } else {
                proxySettingsLayout?.visibility = View.GONE
            }
            refreshProxy()
        }

        mViewModel?.useBundledTor?.observe(viewLifecycleOwner) { isChecked ->
            walletProxyPortEditText?.visibility = if (isChecked) View.GONE else View.VISIBLE
            walletProxyAddressEditText?.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        val indicatorCircle = view?.findViewById<CircularProgressIndicator>(R.id.settings_tor_loading_progressindicator)
        val torIcon = view?.findViewById<ImageView>(R.id.settings_tor_icon)

        samouraiTorManager?.getTorStateLiveData()?.observe(viewLifecycleOwner) { state ->
            samouraiTorManager.getProxy()?.address()?.let { socketAddress ->
                if(socketAddress.toString().isEmpty()) return@let
                if(mViewModel?.useProxy?.value == true && mViewModel?.useBundledTor?.value == true) {
                    torIcon?.visibility = View.VISIBLE
                    indicatorCircle?.visibility = View.INVISIBLE
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

    private fun refreshProxy() {
        val proxyAddress = walletProxyAddressEditText?.text.toString()
        val proxyPort = walletProxyPortEditText?.text.toString()
        ProxyService.instance?.updateProxy(proxyAddress, proxyPort)
    }

    private fun displaySeedDialog(password: String) {
        activity?.supportFragmentManager?.let { fragmentManager ->
            val informationDialog = WalletKeysBottomSheetDialog()
            informationDialog.password = password
            informationDialog.show(fragmentManager, "information_seed_dialog")
        }
    }

    override fun onPasswordSuccess(password: String) {
        displaySeedDialog(password)
    }

    override fun onPasswordFail() {
        Toast.makeText(context, R.string.bad_password, Toast.LENGTH_SHORT).show()
    }

    private fun initProxyStuff(proxyAddress: String, proxyPort: String) {
        val validIpAddress = Patterns.IP_ADDRESS.matcher(proxyAddress).matches()
        if (validIpAddress) {
            walletProxyAddressEditText?.setText(proxyAddress)
            walletProxyPortEditText?.setText(proxyPort)
        }
    }

    override fun onNodeSelected() {
        val node = PrefService.instance?.node
        selectNodeButton?.text = getString(R.string.node_button_text, node?.address)
        refreshProxy()

        activity?.runOnUiThread {
            Toast.makeText(
                activity,
                activity?.getString(R.string.node_selected, node?.name ?: node?.host),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onClickedAddNode() {
        activity?.supportFragmentManager?.let { fragmentManager ->
            val addNodeDialog = AddNodeBottomSheetDialog()
            addNodeDialog.listener = this
            addNodeDialog.show(fragmentManager, "add_node_dialog")
        }
    }

    override fun onClickedEditNode(node: Node?) {
        activity?.supportFragmentManager?.let { fragmentManager ->
            val editNodeDialog = EditNodeBottomSheetDialog()
            editNodeDialog.listener = this
            editNodeDialog.node = node
            editNodeDialog.show(fragmentManager, "edit_node_dialog")
        }

    }

    override fun onNodeAdded() {
        activity?.supportFragmentManager?.let { fragmentManager ->
            val dialog = NodeSelectionBottomSheetDialog()
            dialog.listener = this
            dialog.show(fragmentManager, "node_selection_dialog")
        }
    }

    private fun navigate(destination: Int) {
        val activity = activity
        if (activity != null) {
            val fm = activity.supportFragmentManager
            val navHostFragment = fm.findFragmentById(R.id.nav_host_fragment) as NavHostFragment?
            navHostFragment?.navController?.navigate(destination)
        }
    }

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

    private fun saveNodesAndReopen(jsonArray: JSONArray) {
        PrefService.instance?.edit()?.putString(Constants.PREF_CUSTOM_NODES, jsonArray.toString())
            ?.apply()
        onNodeAdded()
    }
}