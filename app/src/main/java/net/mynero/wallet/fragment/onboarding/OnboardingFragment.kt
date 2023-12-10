package net.mynero.wallet.fragment.onboarding

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.mynero.wallet.R
import net.mynero.wallet.data.Node
import net.mynero.wallet.fragment.dialog.AddNodeBottomSheetDialog
import net.mynero.wallet.fragment.dialog.AddNodeBottomSheetDialog.AddNodeListener
import net.mynero.wallet.fragment.dialog.NodeSelectionBottomSheetDialog
import net.mynero.wallet.fragment.dialog.NodeSelectionBottomSheetDialog.NodeSelectionDialogListener
import net.mynero.wallet.fragment.onboarding.OnboardingViewModel.SeedType
import net.mynero.wallet.model.EnumTorState
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.ProxyService

class OnboardingFragment : Fragment(), NodeSelectionDialogListener, AddNodeListener {
    private var useOffset = true
    private var mViewModel: OnboardingViewModel? = null
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
    private var useBundledTor: CheckBox? = null
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
        useBundledTor = view.findViewById(R.id.bundled_tor_checkbox)

        seedOffsetCheckbox?.isChecked = useOffset
        showXmrchanSwitch?.isChecked = true
        val usingProxy = ProxyService.instance?.usingProxy == true
        val usingBundledTor = ProxyService.instance?.useBundledTor == true

        torSwitch?.isChecked = usingProxy
        useBundledTor?.isChecked = usingBundledTor
        useBundledTor?.isEnabled = usingProxy
        walletProxyAddressEditText?.isEnabled = usingProxy && !usingBundledTor
        walletProxyPortEditText?.isEnabled = usingProxy && !usingBundledTor
        walletProxyPortEditText?.visibility = if (usingBundledTor) View.GONE else View.VISIBLE
        walletProxyAddressEditText?.visibility = if (usingBundledTor) View.GONE else View.VISIBLE

        val node = PrefService.instance?.node // should be using default here
        selectNodeButton?.text = getString(R.string.node_button_text, node?.address)

        bindListeners()
        bindObservers()
    }

    private fun bindObservers() {
        mViewModel?.passphrase?.observe(viewLifecycleOwner) { text ->
            if (text.isEmpty()) {
                walletPasswordConfirmEditText?.text = null
                walletPasswordConfirmEditText?.visibility = View.GONE
            } else {
                walletPasswordConfirmEditText?.visibility = View.VISIBLE
            }
        }

        mViewModel?.showMoreOptions?.observe(viewLifecycleOwner) { show: Boolean ->
            if (show) {
                moreOptionsChevronImageView?.setImageResource(R.drawable.ic_keyboard_arrow_up)
                advancedOptionsLayout?.visibility = View.VISIBLE
            } else {
                moreOptionsChevronImageView?.setImageResource(R.drawable.ic_keyboard_arrow_down)
                advancedOptionsLayout?.visibility = View.GONE
            }
        }

        mViewModel?.enableButton?.observe(viewLifecycleOwner) { enable: Boolean ->
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

        mViewModel?.showMonerochan?.observe(viewLifecycleOwner) {
            if (it) {
                xmrchanOnboardingImage?.visibility = View.VISIBLE
            } else {
                xmrchanOnboardingImage?.visibility = View.GONE
            }
        }

        mViewModel?.useBundledTor?.observe(viewLifecycleOwner) { isChecked ->
            walletProxyPortEditText?.visibility = if (isChecked) View.GONE else View.VISIBLE
            walletProxyAddressEditText?.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        mViewModel?.useProxy?.observe(viewLifecycleOwner) { useProxy ->
            useBundledTor?.isEnabled = useProxy
            walletProxyAddressEditText?.isEnabled = useProxy
            walletProxyPortEditText?.isEnabled = useProxy
        }

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        val indicatorCircle =
            view?.findViewById<CircularProgressIndicator>(R.id.onboarding_tor_loading_progressindicator)
        val torIcon = view?.findViewById<ImageView>(R.id.onboarding_tor_icon)

        samouraiTorManager?.getTorStateLiveData()?.observe(viewLifecycleOwner) { state ->
            samouraiTorManager.getProxy()?.address()?.let { socketAddress ->
                if (socketAddress.toString().isEmpty()) return@let
                if (mViewModel?.useProxy?.value == true && mViewModel?.useBundledTor?.value == true) {
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
        walletProxyPortEditText?.setText(port)
        walletProxyAddressEditText?.setText(address)
    }

    private fun bindListeners() {
        // Disable onBack click
        val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        }
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, onBackPressedCallback)

        moreOptionsDropdownTextView?.setOnClickListener { mViewModel?.onMoreOptionsClicked() }

        moreOptionsChevronImageView?.setOnClickListener { mViewModel?.onMoreOptionsClicked() }

        seedOffsetCheckbox?.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            useOffset = b
        }

        createWalletButton?.setOnClickListener {
            onBackPressedCallback.isEnabled = false
            createOrImportWallet(
                walletSeedEditText?.text.toString().trim { it <= ' ' },
                walletRestoreHeightEditText?.text.toString().trim { it <= ' ' }
            )
        }

        walletPasswordEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                mViewModel?.setPassphrase(editable.toString())
            }
        })

        walletPasswordConfirmEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                mViewModel?.setConfirmedPassphrase(editable.toString())
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

        torSwitch?.setOnCheckedChangeListener { _, b: Boolean ->
            mViewModel?.setUseProxy(b)
        }

        walletProxyPortEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                val text = editable.toString()
                mViewModel?.setProxyPort(text)
            }
        })

        walletProxyAddressEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                val text = editable.toString()
                mViewModel?.setProxyAddress(text)
            }
        })

        showXmrchanSwitch?.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            mViewModel?.setMonerochan(b)
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

    private fun createOrImportWallet(
        walletSeed: String,
        restoreHeightText: String
    ) {
        activity?.let { act ->
            lifecycleScope.launch(Dispatchers.IO) {
                mViewModel?.createOrImportWallet(
                    act,
                    walletSeed,
                    restoreHeightText,
                    useOffset
                )
            }
        }
    }

    override fun onNodeSelected() {
        val node = PrefService.instance?.node
        selectNodeButton?.text = getString(R.string.node_button_text, node?.address)
        Toast.makeText(
            activity,
            getString(R.string.node_selected, node?.name ?: node?.host),
            Toast.LENGTH_SHORT
        ).show()
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