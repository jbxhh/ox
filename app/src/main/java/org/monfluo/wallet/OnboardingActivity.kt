package org.monfluo.wallet

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.monfluo.wallet.data.DefaultNode
import org.monfluo.wallet.data.Node
import org.monfluo.wallet.fragment.dialog.NodeSelectionBottomSheetDialog
import org.monfluo.wallet.listener.NodeSelectionDialogListenerAdapter
import org.monfluo.wallet.livedata.combineLiveDatas
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.model.WalletManager
import org.monfluo.wallet.util.Constants
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.RestoreHeight
import org.monfluo.wallet.util.acitivity.WalletOpeningActivity
import timber.log.Timber
import java.io.File
import java.util.Calendar

class OnboardingActivity : WalletOpeningActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    private lateinit var useProxySwitch: SwitchCompat
    private lateinit var walletProxyAddressEditText: EditText
    private lateinit var walletNameEditText: EditText
    private lateinit var walletPasswordEditText: EditText
    private lateinit var walletPasswordConfirmEditText: EditText
    private lateinit var walletSeedEditText: EditText
    private lateinit var walletRestoreHeightEditText: EditText
    private lateinit var createWalletButton: Button
    private lateinit var moreOptionsDropdownTextView: TextView
    private lateinit var advancedOptionsLayout: ConstraintLayout
    private lateinit var moreOptionsChevronImageView: ImageView
    private lateinit var seedOffsetCheckbox: CheckBox
    private lateinit var selectNodeButton: Button
    private lateinit var seedTypeButton: Button
    private lateinit var seedTypeDescTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        selectNodeButton = findViewById(R.id.select_node_button)
        walletNameEditText = findViewById(R.id.wallet_name_edittext)
        walletPasswordEditText = findViewById(R.id.wallet_password_edittext)
        walletPasswordConfirmEditText = findViewById(R.id.wallet_password_confirm_edittext)
        walletSeedEditText = findViewById(R.id.wallet_seed_edittext)
        walletRestoreHeightEditText = findViewById(R.id.wallet_restore_height_edittext)
        createWalletButton = findViewById(R.id.create_wallet_button)
        moreOptionsDropdownTextView = findViewById(R.id.advanced_settings_dropdown_textview)
        moreOptionsChevronImageView = findViewById(R.id.advanced_settings_chevron_imageview)
        seedOffsetCheckbox = findViewById(R.id.seed_offset_checkbox)
        useProxySwitch = findViewById(R.id.use_proxy_switch)
        walletProxyAddressEditText = findViewById(R.id.wallet_proxy_address_edittext)
        advancedOptionsLayout = findViewById(R.id.more_options_layout)
        seedTypeButton = findViewById(R.id.seed_type_button)
        seedTypeDescTextView = findViewById(R.id.seed_type_desc_textview)

        val useProxy = PreferenceUtils.isUseProxy(this)

        useProxySwitch.isChecked = useProxy

        val node = PreferenceUtils.getOrSetDefaultNode(this, DefaultNode.defaultNode())
        selectNodeButton.text = getString(R.string.node_button_text, node.name)

        walletNameEditText.setText(getWalletNameSuggestion())

        bindListeners()
        bindObservers()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!intent.getBooleanExtra(Constants.EXTRA_PREVENT_GOING_BACK, false)) {
                    finish()
                }
            }
        })
    }

    private fun bindObservers() {
        viewModel.passphrase.observe(this) { text ->
            if (text.isEmpty()) {
                walletPasswordConfirmEditText.text = null
                walletPasswordConfirmEditText.visibility = View.GONE
            } else {
                walletPasswordConfirmEditText.visibility = View.VISIBLE
            }
        }

        viewModel.showMoreOptions.observe(this) { show: Boolean ->
            if (show) {
                moreOptionsChevronImageView.setImageResource(R.drawable.ic_keyboard_arrow_up)
                advancedOptionsLayout.visibility = View.VISIBLE
            } else {
                moreOptionsChevronImageView.setImageResource(R.drawable.ic_keyboard_arrow_down)
                advancedOptionsLayout.visibility = View.GONE
            }
        }

        viewModel.enableButton.observe(this) { enable: Boolean ->
            createWalletButton.isEnabled = enable
        }

        viewModel.seedType.observe(this) { seedType: OnboardingViewModel.SeedType ->
            seedTypeButton.text = seedType.toString()
            seedTypeDescTextView.text = getText(seedType.descResId)
            if (seedType == OnboardingViewModel.SeedType.LEGACY) {
                seedOffsetCheckbox.visibility = View.VISIBLE
                walletRestoreHeightEditText.visibility = View.VISIBLE
                walletPasswordEditText.hint = getString(R.string.password_optional)
                walletSeedEditText.hint = getString(R.string.recovery_phrase_optional_legacy)
            } else {
                seedOffsetCheckbox.visibility = View.GONE
                walletRestoreHeightEditText.visibility = View.GONE
                walletPasswordEditText.hint = getString(R.string.password_non_optional)
                walletSeedEditText.hint = getString(R.string.recovery_phrase_optional_polyseed)
            }
        }

        viewModel.useProxy.observe(this) { useProxy ->
            walletProxyAddressEditText.isEnabled = useProxy
        }
    }

    private fun bindListeners() {
        moreOptionsDropdownTextView.setOnClickListener { viewModel.onMoreOptionsClicked() }

        moreOptionsChevronImageView.setOnClickListener { viewModel.onMoreOptionsClicked() }

        useProxySwitch.setOnCheckedChangeListener { _, b ->
            PreferenceUtils.setUseProxy(this, b)
            viewModel.setUseProxy(b)
        }

        seedOffsetCheckbox.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            viewModel.setUseOffset(b)
        }

        createWalletButton.setOnClickListener {
            createOrImportWallet(
                walletSeedEditText.text.toString().trim { it <= ' ' },
                walletRestoreHeightEditText.text.toString().trim { it <= ' ' }
            )
        }

        walletPasswordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                viewModel.setPassphrase(editable.toString())
            }
        })

        walletPasswordConfirmEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                viewModel.setConfirmedPassphrase(editable.toString())
            }
        })

        walletSeedEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                val text = editable.toString()
                if (text.isEmpty()) {
                    createWalletButton.setText(R.string.create_wallet)
                } else {
                    createWalletButton.setText(R.string.menu_restore)
                }
            }
        })

        seedTypeButton.setOnClickListener { toggleSeedType() }

        walletProxyAddressEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                val text = editable.toString()
                PreferenceUtils.setProxy(this@OnboardingActivity, text)
                viewModel.setProxyAddress(text)
            }
        })

        selectNodeButton.setOnClickListener {
            val nodes = PreferenceUtils.getOrSetDefaultNodes(this, DefaultNode.defaultNodes())
            val node = PreferenceUtils.getOrSetDefaultNode(this, DefaultNode.defaultNode())

            val listener = object : NodeSelectionDialogListenerAdapter(
                activity = this,
            ) {
                override fun trySelectNode(
                    self: NodeSelectionBottomSheetDialog,
                    node: Node
                ): Boolean {
                    return PreferenceUtils.setNode(this@OnboardingActivity, node).fold(
                        {
                            selectNodeButton.text = getString(R.string.node_button_text, node.name)
                            Timber.i("Node selected")
                            true
                        },
                        {
                            Timber.e(it, "Failed to select node")
                            Toast.makeText(this@OnboardingActivity, "Failed to select node", Toast.LENGTH_SHORT).show()
                            false
                        }
                    )
                }
            }

            val dialog = NodeSelectionBottomSheetDialog(nodes, node, listener)
            dialog.show(supportFragmentManager, "node_selection_dialog")
        }
    }

    private fun toggleSeedType() {
        val seedType = viewModel.seedType.value ?: return
        var newSeedType = OnboardingViewModel.SeedType.UNKNOWN
        if (seedType == OnboardingViewModel.SeedType.POLYSEED) {
            newSeedType = OnboardingViewModel.SeedType.LEGACY
        } else if (seedType == OnboardingViewModel.SeedType.LEGACY) {
            newSeedType = OnboardingViewModel.SeedType.POLYSEED
        }
        viewModel.setSeedType(newSeedType)
    }

    private fun createOrImportWallet(
        walletSeed: String,
        restoreHeightText: String
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.createOrImportWallet(
                this@OnboardingActivity,
                walletNameEditText.text.toString(),
                walletSeed,
                restoreHeightText,
                viewModel.useOffset.value!! || viewModel.seedType.value == OnboardingViewModel.SeedType.POLYSEED,
            )
        }
    }

    private fun getWalletNameSuggestion(): String {
        val walletRoot = Helper.getWalletRoot(this)
        if (!WalletManager.instance.walletExists(walletRoot.resolve(Constants.DEFAULT_WALLET_NAME))) {
            return Constants.DEFAULT_WALLET_NAME
        }
        for (i in 2 until Int.MAX_VALUE) {
            val name = Constants.DEFAULT_WALLET_NAME + i
            if (!WalletManager.instance.walletExists(walletRoot.resolve(name))) {
                return name
            }
        }
        Timber.e("User managed to create 2^31-3 wallets, giving up on finding a good wallet name")
        return Constants.DEFAULT_WALLET_NAME
    }
}

internal class OnboardingViewModel : ViewModel() {
    private val _useOffset = MutableLiveData(false)
    val useOffset: LiveData<Boolean> = _useOffset
    private val _showMoreOptions = MutableLiveData(false)
    private val _creatingWallet = MutableLiveData(false)
    private val _seedType = MutableLiveData(SeedType.POLYSEED)
    private val _useProxy = MutableLiveData(false)
    val useProxy: LiveData<Boolean> = _useProxy
    private val _proxyAddress = MutableLiveData("")
    private val _passphrase = MutableLiveData("")
    val passphrase: LiveData<String> = _passphrase
    private val _confirmedPassphrase = MutableLiveData("")
    var showMoreOptions: LiveData<Boolean> = _showMoreOptions
    var seedType: LiveData<SeedType> = _seedType

    val enableButton = combineLiveDatas(
        seedType,
        _useProxy,
        _proxyAddress,
        _passphrase,
        _confirmedPassphrase,
        _creatingWallet,
    ) { seedType, useProxy, proxyAddress, passphrase, confirmedPassphrase, creatingWallet ->
        if (seedType == null || useProxy == null || proxyAddress == null || passphrase == null || confirmedPassphrase == null || creatingWallet == null) return@combineLiveDatas false
        if ((passphrase.isNotEmpty() || confirmedPassphrase.isNotEmpty()) && passphrase != confirmedPassphrase) return@combineLiveDatas false
        if (creatingWallet) return@combineLiveDatas false
        if (seedType == SeedType.POLYSEED && (passphrase.isEmpty() || confirmedPassphrase.isEmpty())) return@combineLiveDatas false
        if (useProxy && proxyAddress.isEmpty()) return@combineLiveDatas false

        return@combineLiveDatas true
    }

    fun onMoreOptionsClicked() {
        val currentValue = showMoreOptions.value ?: false
        val newValue = !currentValue
        _showMoreOptions.value = newValue
    }

    fun setSeedType(seedType: SeedType?) {
        _seedType.value = seedType
    }

    fun createOrImportWallet(
        activity: OnboardingActivity,
        walletName: String,
        walletSeed: String,
        restoreHeightText: String,
        useOffset: Boolean,
    ) {
        val passphrase = _passphrase.value ?: return
        val confirmedPassphrase = _confirmedPassphrase.value ?: return

        val application = activity.application as MoneroApplication
        _creatingWallet.postValue(true)
        val offset = if (useOffset) confirmedPassphrase else ""
        if (passphrase.isNotEmpty()) {
            if (passphrase != confirmedPassphrase) {
                _creatingWallet.postValue(false)
                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        application.getString(R.string.invalid_confirmed_password),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
        }
        var restoreHeight = newRestoreHeight
        val walletFile = File(Helper.getWalletRoot(activity), walletName)
        var wallet: Wallet? = null
        val seedTypeValue = seedType.value ?: return
        if (walletSeed.isEmpty()) {
            if (seedTypeValue == SeedType.POLYSEED) {
                wallet = if (offset.isEmpty()) {
                    activity.runOnUiThread {
                        _creatingWallet.postValue(false)
                        Toast.makeText(
                            activity,
                            application.getString(R.string.invalid_empty_passphrase),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return
                } else {
                    WalletManager.instance.createWalletPolyseed(
                        walletFile,
                        passphrase,
                        offset,
                        Constants.MNEMONIC_LANGUAGE
                    )
                }
            } else if (seedTypeValue == SeedType.LEGACY) {
                val tmpWalletFile =
                    File(activity.applicationInfo.dataDir, walletName + "_tmp")
                val tmpWallet = createTempWallet(tmpWalletFile) //we do this to get seed, then recover wallet so we can use seed offset
                wallet = WalletManager.instance.recoveryWallet(
                    walletFile,
                    passphrase,
                    tmpWallet.getSeed(""),
                    offset,
                    restoreHeight
                )
                tmpWalletFile.delete()
            }
        } else {
            if (getMnemonicType(walletSeed) == SeedType.UNKNOWN) {
                activity.runOnUiThread {
                    _creatingWallet.postValue(false)
                    Toast.makeText(
                        activity,
                        application.getString(R.string.invalid_mnemonic_code),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
            if (restoreHeightText.isNotEmpty()) {
                restoreHeight = restoreHeightText.toLong()
            }
            if (seedTypeValue == SeedType.POLYSEED) {
                wallet = WalletManager.instance.recoveryWalletPolyseed(
                    walletFile,
                    passphrase,
                    walletSeed,
                    offset
                )
            } else if (seedTypeValue == SeedType.LEGACY) {
                wallet = WalletManager.instance.recoveryWallet(
                    walletFile,
                    passphrase,
                    walletSeed,
                    offset,
                    restoreHeight
                )
            }
        }
        val walletStatus = wallet?.status
        wallet?.close()
        val ok = walletStatus?.isOk
        walletFile.delete() // cache is broken for some reason when recovering wallets. delete the file here. this happens in monerujo too.
        if (ok == true) {
            activity.openWallet(walletFile.absolutePath, passphrase)
        } else {
            activity.runOnUiThread {
                _creatingWallet.postValue(false)
                Toast.makeText(
                    activity,
                    application.getString(
                        R.string.create_wallet_failed,
                        walletStatus?.errorString
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val newRestoreHeight: Long
        get() {
            val restoreDate = Calendar.getInstance()
            restoreDate.add(Calendar.DAY_OF_MONTH, 0)
            return RestoreHeight.instance?.getHeight(restoreDate.time) ?: 0
        }

    private fun getMnemonicType(seed: String): SeedType {
        val words = seed.split("\\s".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val seedTypeValue = seedType.value ?: return SeedType.LEGACY
        return if (words.size == 16 && seedTypeValue == SeedType.POLYSEED) {
            SeedType.POLYSEED
        } else if (words.size == 25 && seedTypeValue == SeedType.LEGACY) {
            SeedType.LEGACY
        } else {
            SeedType.UNKNOWN
        }
    }

    private fun createTempWallet(tmpWalletFile: File): Wallet {
        return WalletManager.instance.createWallet(
            tmpWalletFile,
            "",
            Constants.MNEMONIC_LANGUAGE,
            0
        )
    }

    fun setUseOffset(useOffset: Boolean) {
        _useOffset.value = useOffset
    }

    fun setProxyAddress(address: String) {
        _proxyAddress.value = address
    }

    fun setUseProxy(useProxy: Boolean) {
        _useProxy.value = useProxy
    }

    fun setPassphrase(passphrase: String) {
        _passphrase.value = passphrase
    }

    fun setConfirmedPassphrase(confirmedPassphrase: String) {
        _confirmedPassphrase.value = confirmedPassphrase
    }

    enum class SeedType(val descResId: Int) {
        LEGACY(R.string.seed_desc_legacy), POLYSEED(R.string.seed_desc_polyseed), UNKNOWN(0)
    }
}