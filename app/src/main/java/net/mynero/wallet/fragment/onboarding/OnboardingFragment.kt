package net.mynero.wallet.fragment.onboarding

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import net.mynero.wallet.MoneroApplication
import net.mynero.wallet.R
import net.mynero.wallet.data.Node
import net.mynero.wallet.fragment.dialog.AddNodeBottomSheetDialog
import net.mynero.wallet.fragment.dialog.AddNodeBottomSheetDialog.AddNodeListener
import net.mynero.wallet.fragment.dialog.NodeSelectionBottomSheetDialog
import net.mynero.wallet.fragment.dialog.NodeSelectionBottomSheetDialog.NodeSelectionDialogListener
import net.mynero.wallet.fragment.onboarding.OnboardingViewModel.SeedType
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.ProxyService
import net.mynero.wallet.util.Constants

class OnboardingFragment : Fragment(), NodeSelectionDialogListener, AddNodeListener {
    private var useOffset = true
    private var mViewModel: OnboardingViewModel? = null
    private var proxyAddressListener: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun afterTextChanged(editable: Editable) {
            if (mViewModel != null) {
                mViewModel?.setProxyAddress(editable.toString())
                mViewModel?.updateProxy(activity?.application as MoneroApplication)
            }
        }
    }
    private var proxyPortListener: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun afterTextChanged(editable: Editable) {
            if (mViewModel != null) {
                mViewModel?.setProxyPort(editable.toString())
                mViewModel?.updateProxy(activity?.application as MoneroApplication)
            }
        }
    }
    private var walletProxyAddressEditText: EditText? = null
    private var walletProxyPortEditText: EditText? = null
    private var walletPasswordEditText: EditText? = null
    private var walletPasswordConfirmEditText: EditText? = null
    private var walletSeedEditText: EditText? = null
    private var walletRestoreHeightEditText: EditText? = null
    private var createWalletButton: Button? = null
    private var moreOptionsDropdownTextView: TextView? = null
    private var torSwitch: SwitchCompat? = null
    private var advancedOptionsLayout: ConstraintLayout? = null
    private var moreOptionsChevronImageView: ImageView? = null
    private var seedOffsetCheckbox: CheckBox? = null
    private var selectNodeButton: Button? = null
    private var showXmrchanSwitch: SwitchCompat? = null
    private var xmrchanOnboardingImage: ImageView? = null
    private var seedTypeButton: Button? = null
    private var seedTypeDescTextView: TextView? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mViewModel = ViewModelProvider(this)[OnboardingViewModel::class.java]
        selectNodeButton = view.findViewById(R.id.select_node_button)
        walletPasswordEditText = view.findViewById(R.id.wallet_password_edittext)
        walletPasswordConfirmEditText = view.findViewById(R.id.wallet_password_confirm_edittext)
        walletSeedEditText = view.findViewById(R.id.wallet_seed_edittext)
        walletRestoreHeightEditText = view.findViewById(R.id.wallet_restore_height_edittext)
        createWalletButton = view.findViewById(R.id.create_wallet_button)
        moreOptionsDropdownTextView = view.findViewById(R.id.advanced_settings_dropdown_textview)
        moreOptionsChevronImageView = view.findViewById(R.id.advanced_settings_chevron_imageview)
        torSwitch = view.findViewById(R.id.tor_onboarding_switch)
        seedOffsetCheckbox = view.findViewById(R.id.seed_offset_checkbox)
        walletProxyAddressEditText = view.findViewById(R.id.wallet_proxy_address_edittext)
        walletProxyPortEditText = view.findViewById(R.id.wallet_proxy_port_edittext)
        advancedOptionsLayout = view.findViewById(R.id.more_options_layout)
        showXmrchanSwitch = view.findViewById(R.id.show_xmrchan_switch)
        xmrchanOnboardingImage = view.findViewById(R.id.xmrchan_onboarding_imageview)
        seedTypeButton = view.findViewById(R.id.seed_type_button)
        seedTypeDescTextView = view.findViewById(R.id.seed_type_desc_textview)
        bindListeners()
        bindObservers()
    }

    private fun bindObservers() {
        mViewModel?.showMoreOptions?.observe(viewLifecycleOwner) { show: Boolean ->
            if (show) {
                moreOptionsChevronImageView?.setImageResource(R.drawable.ic_keyboard_arrow_up)
                advancedOptionsLayout?.visibility = View.VISIBLE
            } else {
                moreOptionsChevronImageView?.setImageResource(R.drawable.ic_keyboard_arrow_down)
                advancedOptionsLayout?.visibility = View.GONE
            }
        }
        mViewModel?.enableCreateButton?.observe(viewLifecycleOwner) { enable: Boolean ->
            createWalletButton?.isEnabled = enable
        }
        mViewModel?.seedType?.observe(viewLifecycleOwner) { seedType: SeedType ->
            seedTypeButton?.text = seedType.toString()
            seedTypeDescTextView?.text = getText(seedType.descResId)
            if (seedType == SeedType.LEGACY) {
                seedOffsetCheckbox?.visibility = View.VISIBLE
                walletRestoreHeightEditText?.visibility = View.VISIBLE
                walletPasswordEditText?.hint = getString(R.string.password_optional)
                walletSeedEditText?.hint = getString(R.string.recovery_phrase_optional_legacy)
            } else {
                seedOffsetCheckbox?.visibility = View.GONE
                walletRestoreHeightEditText?.visibility = View.GONE
                walletPasswordEditText?.hint = getString(R.string.password_non_optional)
                walletSeedEditText?.hint = getString(R.string.recovery_phrase_optional_polyseed)
            }
        }
    }

    private fun bindListeners() {
        seedOffsetCheckbox?.isChecked = useOffset
        // Disable onBack click
        val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        }
        val activity = activity
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, onBackPressedCallback)
        moreOptionsDropdownTextView?.setOnClickListener { mViewModel?.onMoreOptionsClicked() }
        moreOptionsChevronImageView?.setOnClickListener { mViewModel?.onMoreOptionsClicked() }
        seedOffsetCheckbox?.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            useOffset = b
        }
        createWalletButton?.setOnClickListener {
            prepareDefaultNode()
            onBackPressedCallback.isEnabled = false
            (getActivity()?.application as MoneroApplication).executor?.execute {
                createOrImportWallet(
                    walletPasswordEditText?.text.toString(),
                    walletPasswordConfirmEditText?.text.toString(),
                    walletSeedEditText?.text.toString().trim { it <= ' ' },
                    walletRestoreHeightEditText?.text.toString().trim { it <= ' ' }
                )
            }
        }
        walletPasswordEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                val text = editable.toString()
                if (text.isEmpty()) {
                    walletPasswordConfirmEditText?.text = null
                    walletPasswordConfirmEditText?.visibility = View.GONE
                } else {
                    walletPasswordConfirmEditText?.visibility = View.VISIBLE
                }
            }
        })
        walletSeedEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                val text = editable.toString()
                if (text.isEmpty()) {
                    createWalletButton?.setText(R.string.create_wallet)
                } else {
                    createWalletButton?.setText(R.string.menu_restore)
                }
            }
        })
        seedTypeButton?.setOnClickListener { toggleSeedType() }
        torSwitch?.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PrefService.instance?.edit()?.putBoolean(Constants.PREF_USES_TOR, b)?.apply()
            removeProxyTextListeners()
            if (b) {
                if (ProxyService.instance?.hasProxySet() == true) {
                    val proxyAddress =
                        ProxyService.instance?.proxyAddress ?: return@setOnCheckedChangeListener
                    val proxyPort =
                        ProxyService.instance?.proxyPort ?: return@setOnCheckedChangeListener
                    initProxyStuff(proxyAddress, proxyPort)
                } else {
                    initProxyStuff("127.0.0.1", "9050")
                }
                addProxyTextListeners()
            }
            mViewModel?.updateProxy(getActivity()?.application as MoneroApplication)
        }
        showXmrchanSwitch?.isChecked =
            PrefService.instance?.getBoolean(Constants.PREF_MONEROCHAN, true) == true
        showXmrchanSwitch?.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            PrefService.instance?.edit()?.putBoolean(Constants.PREF_MONEROCHAN, b)?.apply()
            if (b) {
                xmrchanOnboardingImage?.visibility = View.VISIBLE
            } else {
                xmrchanOnboardingImage?.visibility = View.GONE
            }
        }
        val node = PrefService.instance?.node // should be using default here
        selectNodeButton?.text = getString(R.string.node_button_text, node?.address)
        selectNodeButton?.setOnClickListener {
            activity?.supportFragmentManager?.let { fragmentManager ->
                val dialog = NodeSelectionBottomSheetDialog()
                dialog.listener = this
                dialog.show(fragmentManager, "node_selection_dialog")
            }
        }
    }

    private fun toggleSeedType() {
        val seedType = mViewModel?.seedType?.value ?: return
        var newSeedType = SeedType.UNKNOWN
        if (seedType == SeedType.POLYSEED) {
            newSeedType = SeedType.LEGACY
        } else if (seedType == SeedType.LEGACY) {
            newSeedType = SeedType.POLYSEED
        }
        mViewModel?.setSeedType(newSeedType)
    }

    private fun prepareDefaultNode() {
        PrefService.instance?.node
    }

    private fun createOrImportWallet(
        walletPassword: String,
        confirmedPassword: String,
        walletSeed: String,
        restoreHeightText: String
    ) {
        val activity: Activity? = activity
        if (activity != null) {
            mViewModel?.createOrImportWallet(
                activity,
                walletPassword,
                confirmedPassword,
                walletSeed,
                restoreHeightText,
                useOffset
            )
        }
    }

    private fun removeProxyTextListeners() {
        walletProxyAddressEditText?.removeTextChangedListener(proxyAddressListener)
        walletProxyPortEditText?.removeTextChangedListener(proxyPortListener)
    }

    private fun addProxyTextListeners() {
        walletProxyAddressEditText?.addTextChangedListener(proxyAddressListener)
        walletProxyPortEditText?.addTextChangedListener(proxyPortListener)
    }

    private fun initProxyStuff(proxyAddress: String, proxyPort: String) {
        val validIpAddress = Patterns.IP_ADDRESS.matcher(proxyAddress).matches()
        if (validIpAddress) {
            mViewModel?.setProxyAddress(proxyAddress)
            mViewModel?.setProxyPort(proxyPort)
            walletProxyAddressEditText?.setText(proxyAddress)
            walletProxyPortEditText?.setText(proxyPort)
        }
    }

    override fun onNodeSelected() {
        val node = PrefService.instance?.node
        selectNodeButton?.text = getString(R.string.node_button_text, node?.address)
        Toast.makeText(
            activity,
            getString(R.string.node_selected),
            Toast.LENGTH_SHORT
        ).show()
        mViewModel?.updateProxy(activity?.application as MoneroApplication)
    }

    override fun onClickedEditNode(node: Node?) {}
    override fun onClickedAddNode() {
        activity?.supportFragmentManager?.let { fragmentManager ->
            val addNodeDialog = AddNodeBottomSheetDialog()
            addNodeDialog.listener = this
            addNodeDialog.show(fragmentManager, "add_node_dialog")
        }
    }

    override fun onNodeAdded() {
        activity?.supportFragmentManager?.let { fragmentManager ->
            val dialog = NodeSelectionBottomSheetDialog()
            dialog.listener = this
            dialog.show(fragmentManager, "node_selection_dialog")
        }
    }
}