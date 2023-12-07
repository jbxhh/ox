package net.mynero.wallet.fragment.settings

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
import net.mynero.wallet.model.Wallet.ConnectionStatus
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.BalanceService
import net.mynero.wallet.service.BlockchainService
import net.mynero.wallet.service.DaemonService
import net.mynero.wallet.service.HistoryService
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.ProxyService
import net.mynero.wallet.util.Constants
import org.json.JSONArray
import timber.log.Timber

class SettingsFragment : Fragment(), PasswordListener, NodeSelectionDialogListener, AddNodeListener,
    EditNodeListener {
    private var mViewModel: SettingsViewModel? = null
    private var walletProxyAddressEditText: EditText? = null
    private var walletProxyPortEditText: EditText? = null
    private var selectNodeButton: Button? = null
    private var cachedProxyAddress: String = ""
    private var cachedProxyPort: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        val displaySeedButton = view.findViewById<Button>(R.id.display_seed_button)
        val displayUtxosButton = view.findViewById<Button>(R.id.display_utxos_button)
        selectNodeButton = view.findViewById(R.id.select_node_button)
        val streetModeSwitch = view.findViewById<SwitchCompat>(R.id.street_mode_switch)
        val monerochanSwitch = view.findViewById<SwitchCompat>(R.id.monerochan_switch)
        val donationSwitch = view.findViewById<SwitchCompat>(R.id.donate_per_tx_switch)
        val torSwitch = view.findViewById<SwitchCompat>(R.id.tor_switch)
        val proxySettingsLayout =
            view.findViewById<ConstraintLayout>(R.id.wallet_proxy_settings_layout)
        walletProxyAddressEditText = view.findViewById(R.id.wallet_proxy_address_edittext)
        walletProxyPortEditText = view.findViewById(R.id.wallet_proxy_port_edittext)
        streetModeSwitch.isChecked =
            PrefService.instance?.getBoolean(Constants.PREF_STREET_MODE, false) == true
        streetModeSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PrefService.instance?.edit()?.putBoolean(Constants.PREF_STREET_MODE, b)?.apply()
            BalanceService.instance?.refreshBalance()
        }
        monerochanSwitch.isChecked =
            PrefService.instance?.getBoolean(Constants.PREF_MONEROCHAN, true) == true
        monerochanSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PrefService.instance?.edit()?.putBoolean(Constants.PREF_MONEROCHAN, b)?.apply()
            HistoryService.instance?.refreshHistory()
        }
        donationSwitch.isChecked =
            PrefService.instance?.getBoolean(Constants.PREF_DONATE_PER_TX, false) == true
        donationSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PrefService.instance?.edit()?.putBoolean(Constants.PREF_DONATE_PER_TX, b)?.apply()
        }
        val prefService = PrefService.instance ?: return
        val usesProxy = prefService.getBoolean(Constants.PREF_USES_TOR, false)
        cachedProxyAddress = ProxyService.instance?.proxyAddress ?: return
        cachedProxyPort = ProxyService.instance?.proxyPort ?: return
        if (ProxyService.instance?.hasProxySet() == true) {
            initProxyStuff(cachedProxyAddress, cachedProxyPort)
        }
        torSwitch.isChecked = usesProxy
        if (usesProxy) {
            proxySettingsLayout.visibility = View.VISIBLE
        } else {
            proxySettingsLayout.visibility = View.GONE
        }
        torSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            prefService.edit()?.putBoolean(Constants.PREF_USES_TOR, b)?.apply()
            if (b) {
                if (ProxyService.instance?.hasProxySet() == true) {
                    val proxyAddress = ProxyService.instance?.proxyAddress ?: return@setOnCheckedChangeListener
                    val proxyPort = ProxyService.instance?.proxyPort ?: return@setOnCheckedChangeListener
                    initProxyStuff(proxyAddress, proxyPort)
                }
                proxySettingsLayout.visibility = View.VISIBLE
            } else {
                proxySettingsLayout.visibility = View.GONE
            }
            refreshProxy()
        }
        displaySeedButton.setOnClickListener {
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
        displayUtxosButton.setOnClickListener { navigate(R.id.nav_to_utxos) }
        val node = PrefService.instance?.node // shouldn't use default value here
        selectNodeButton?.text = getString(R.string.node_button_text, node?.address)
        selectNodeButton?.setOnClickListener {
            activity?.supportFragmentManager?.let { fragmentManager ->
                val dialog = NodeSelectionBottomSheetDialog()
                dialog.listener = this
                dialog.show(fragmentManager, "node_selection_dialog")
            }
        }

        val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                refreshProxy()
                findNavController().popBackStack()
            }
        }

        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    private fun refreshProxy() {
        val proxyAddress = walletProxyAddressEditText?.text.toString()
        val proxyPort = walletProxyPortEditText?.text.toString()
        if((proxyAddress != cachedProxyAddress) || (proxyPort != cachedProxyPort)) {
            ProxyService.instance?.updateProxy(proxyAddress, proxyPort)
            cachedProxyAddress = proxyAddress
            cachedProxyPort = proxyPort
        }
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