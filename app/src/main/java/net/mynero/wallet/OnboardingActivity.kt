package net.mynero.wallet

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.mynero.wallet.data.Node
import net.mynero.wallet.fragment.dialog.AddNodeBottomSheetDialog
import net.mynero.wallet.fragment.dialog.NodeSelectionBottomSheetDialog
import net.mynero.wallet.livedata.combineLiveDatas
import net.mynero.wallet.model.EnumTorState
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.service.MoneroHandlerThread
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.service.ProxyService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.RestoreHeight
import java.io.File
import java.util.Calendar

class OnboardingActivity : AppCompatActivity() {

    private lateinit var mViewModel: OnboardingViewModel
    private lateinit var walletProxyAddressEditText: EditText
    private lateinit var walletProxyPortEditText: EditText
    private lateinit var walletPasswordEditText: EditText
    private lateinit var walletPasswordConfirmEditText: EditText
    private lateinit var walletSeedEditText: EditText
    private lateinit var walletRestoreHeightEditText: EditText
    private lateinit var createWalletButton: Button
    private lateinit var moreOptionsDropdownTextView: TextView
    private lateinit var torSwitch: SwitchCompat
    private lateinit var advancedOptionsLayout: ConstraintLayout
    private lateinit var moreOptionsChevronImageView: ImageView
    private lateinit var seedOffsetCheckbox: CheckBox
    private lateinit var selectNodeButton: Button
    private lateinit var showXmrchanSwitch: SwitchCompat
    private lateinit var xmrchanOnboardingImage: ImageView
    private lateinit var seedTypeButton: Button
    private lateinit var seedTypeDescTextView: TextView
    private lateinit var useBundledTor: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        mViewModel = ViewModelProvider(this)[OnboardingViewModel::class.java]
        selectNodeButton = findViewById(R.id.select_node_button)
        walletPasswordEditText = findViewById(R.id.wallet_password_edittext)
        walletPasswordConfirmEditText = findViewById(R.id.wallet_password_confirm_edittext)
        walletSeedEditText = findViewById(R.id.wallet_seed_edittext)
        walletRestoreHeightEditText = findViewById(R.id.wallet_restore_height_edittext)
        createWalletButton = findViewById(R.id.create_wallet_button)
        moreOptionsDropdownTextView = findViewById(R.id.advanced_settings_dropdown_textview)
        moreOptionsChevronImageView = findViewById(R.id.advanced_settings_chevron_imageview)
        torSwitch = findViewById(R.id.tor_onboarding_switch)
        seedOffsetCheckbox = findViewById(R.id.seed_offset_checkbox)
        walletProxyAddressEditText = findViewById(R.id.wallet_proxy_address_edittext)
        walletProxyPortEditText = findViewById(R.id.wallet_proxy_port_edittext)
        advancedOptionsLayout = findViewById(R.id.more_options_layout)
        showXmrchanSwitch = findViewById(R.id.show_xmrchan_switch)
        xmrchanOnboardingImage = findViewById(R.id.xmrchan_onboarding_imageview)
        seedTypeButton = findViewById(R.id.seed_type_button)
        seedTypeDescTextView = findViewById(R.id.seed_type_desc_textview)
        useBundledTor = findViewById(R.id.bundled_tor_checkbox)

        seedOffsetCheckbox.isChecked = mViewModel.useOffset
        val usingProxy = ProxyService.instance?.usingProxy == true
        val usingBundledTor = ProxyService.instance?.useBundledTor == true

        torSwitch.isChecked = usingProxy
        useBundledTor.isChecked = usingBundledTor
        useBundledTor.isEnabled = usingProxy
        walletProxyAddressEditText.isEnabled = usingProxy && !usingBundledTor
        walletProxyPortEditText.isEnabled = usingProxy && !usingBundledTor

        val node = PrefService.instance?.node // should be using default here
        selectNodeButton.text = getString(R.string.node_button_text, node?.address)

        bindListeners()
        bindObservers()
    }

    private fun bindObservers() {
        mViewModel.passphrase.observe(this) { text ->
            if (text.isEmpty()) {
                walletPasswordConfirmEditText.text = null
                walletPasswordConfirmEditText.visibility = View.GONE
            } else {
                walletPasswordConfirmEditText.visibility = View.VISIBLE
            }
        }

        mViewModel.showMoreOptions.observe(this) { show: Boolean ->
            if (show) {
                moreOptionsChevronImageView.setImageResource(R.drawable.ic_keyboard_arrow_up)
                advancedOptionsLayout.visibility = View.VISIBLE
            } else {
                moreOptionsChevronImageView.setImageResource(R.drawable.ic_keyboard_arrow_down)
                advancedOptionsLayout.visibility = View.GONE
            }
        }

        mViewModel.enableButton.observe(this) { enable: Boolean ->
            createWalletButton.isEnabled = enable
        }

        mViewModel.seedType.observe(this) { seedType: OnboardingViewModel.SeedType ->
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

        mViewModel.showMonerochan.observe(this) {
            if (it) {
                xmrchanOnboardingImage.visibility = View.VISIBLE
            } else {
                xmrchanOnboardingImage.visibility = View.GONE
            }
        }

        mViewModel.useBundledTor.observe(this) { isChecked ->
            walletProxyPortEditText.isEnabled = !isChecked && mViewModel.useProxy.value == true
            walletProxyAddressEditText.isEnabled = !isChecked && mViewModel.useProxy.value == true
        }

        mViewModel.useProxy.observe(this) { useProxy ->
            useBundledTor.isEnabled = useProxy
            walletProxyAddressEditText.isEnabled = useProxy && mViewModel.useBundledTor.value == false
            walletProxyPortEditText.isEnabled = useProxy && mViewModel.useBundledTor.value == false
        }

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        val indicatorCircle = findViewById<CircularProgressIndicator>(R.id.onboarding_tor_loading_progressindicator)
        val torIcon = findViewById<ImageView>(R.id.onboarding_tor_icon)

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

            indicatorCircle.isIndeterminate = state.progressIndicator == 0
            indicatorCircle.progress = state.progressIndicator

            when (state.state) {
                EnumTorState.OFF -> {
                    torIcon.visibility = View.INVISIBLE
                    indicatorCircle.visibility = View.INVISIBLE
                }

                EnumTorState.STARTING, EnumTorState.STOPPING -> {
                    torIcon.visibility = View.INVISIBLE
                    indicatorCircle.visibility = View.VISIBLE
                }

                else -> {}
            }
        }
    }

    private fun updateProxy(address: String, port: String) {
        walletProxyPortEditText.setText(port)
        walletProxyAddressEditText.setText(address)
    }

    private fun bindListeners() {
        // Disable onBack click
        val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        moreOptionsDropdownTextView.setOnClickListener { mViewModel.onMoreOptionsClicked() }

        moreOptionsChevronImageView.setOnClickListener { mViewModel.onMoreOptionsClicked() }

        seedOffsetCheckbox.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            mViewModel.useOffset = b
        }

        createWalletButton.setOnClickListener {
            onBackPressedCallback.isEnabled = false
            createOrImportWallet(
                walletSeedEditText.text.toString().trim { it <= ' ' },
                walletRestoreHeightEditText.text.toString().trim { it <= ' ' }
            )
        }

        walletPasswordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                mViewModel.setPassphrase(editable.toString())
            }
        })

        walletPasswordConfirmEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                mViewModel.setConfirmedPassphrase(editable.toString())
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

        torSwitch.setOnCheckedChangeListener { _, b: Boolean ->
            mViewModel.setUseProxy(b)
        }

        walletProxyPortEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                val text = editable.toString()
                mViewModel.setProxyPort(text)
            }
        })

        walletProxyAddressEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                val text = editable.toString()
                mViewModel.setProxyAddress(text)
            }
        })

        showXmrchanSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            mViewModel.setMonerochan(b)
        }

        selectNodeButton.setOnClickListener {
            val activity = this
            supportFragmentManager.let { fragmentManager ->
                val dialog = NodeSelectionBottomSheetDialog()
                dialog.listener = object : NodeSelectionBottomSheetDialog.NodeSelectionDialogListener {
                    override fun onNodeSelected() {
                        val node = PrefService.instance?.node
                        selectNodeButton.text = getString(R.string.node_button_text, node?.address)
                        Toast.makeText(
                            activity,
                            getString(R.string.node_selected, node?.name ?: node?.host),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    override fun onClickedEditNode(node: Node?) {}
                    override fun onClickedAddNode() {}
                }
                dialog.show(fragmentManager, "node_selection_dialog")
            }
        }

        useBundledTor.setOnCheckedChangeListener { _, isChecked ->
            mViewModel.setUseBundledTor(isChecked)
        }
    }

    private fun toggleSeedType() {
        val seedType = mViewModel.seedType.value ?: return
        var newSeedType = OnboardingViewModel.SeedType.UNKNOWN
        if (seedType == OnboardingViewModel.SeedType.POLYSEED) {
            newSeedType = OnboardingViewModel.SeedType.LEGACY
        } else if (seedType == OnboardingViewModel.SeedType.LEGACY) {
            newSeedType = OnboardingViewModel.SeedType.POLYSEED
        }
        mViewModel.setSeedType(newSeedType)
    }

    private fun createOrImportWallet(
        walletSeed: String,
        restoreHeightText: String
    ) {
        this.let { act ->
            lifecycleScope.launch(Dispatchers.IO) {
                mViewModel.createOrImportWallet(
                    act,
                    walletSeed,
                    restoreHeightText,
                    mViewModel.useOffset,
                    applicationContext
                )
            }
        }
    }
}

internal class OnboardingViewModel : ViewModel() {
    var useOffset = true
    private val _showMoreOptions = MutableLiveData(false)
    private val _creatingWallet = MutableLiveData(false)
    private val _seedType = MutableLiveData(SeedType.POLYSEED)
    private val _useProxy = MutableLiveData(false)
    val useProxy: LiveData<Boolean> = _useProxy
    private val _proxyAddress = MutableLiveData("")
    private val _proxyPort = MutableLiveData("")
    private val _useBundledTor = MutableLiveData(false)
    val useBundledTor: LiveData<Boolean> = _useBundledTor
    private val _passphrase = MutableLiveData("")
    val passphrase: LiveData<String> = _passphrase
    private val _confirmedPassphrase = MutableLiveData("")
    private val _showMonerochan = MutableLiveData(Constants.DEFAULT_PREF_MONEROCHAN)
    val showMonerochan: LiveData<Boolean> = _showMonerochan
    var showMoreOptions: LiveData<Boolean> = _showMoreOptions
    var seedType: LiveData<SeedType> = _seedType

    init {
        _useProxy.value = ProxyService.instance?.usingProxy
        _useBundledTor.value = ProxyService.instance?.useBundledTor
    }

    val enableButton = combineLiveDatas(
        seedType,
        _useProxy,
        _proxyAddress,
        _proxyPort,
        _useBundledTor,
        _passphrase,
        _confirmedPassphrase,
        _creatingWallet,
        ProxyService.instance?.samouraiTorManager?.getTorStateLiveData()
    ) { seedType, useProxy, proxyAddress, proxyPort, useBundledTor, passphrase, confirmedPassphrase, creatingWallet, torState ->
        if (seedType == null || useProxy == null || proxyAddress == null || proxyPort == null || useBundledTor == null || passphrase == null || confirmedPassphrase == null || creatingWallet == null) return@combineLiveDatas false
        if ((passphrase.isNotEmpty() || confirmedPassphrase.isNotEmpty()) && passphrase != confirmedPassphrase) return@combineLiveDatas false
        if (creatingWallet) return@combineLiveDatas false
        if (seedType == SeedType.POLYSEED && (passphrase.isEmpty() || confirmedPassphrase.isEmpty())) return@combineLiveDatas false
        if (useProxy && (proxyAddress.isEmpty() || proxyPort.isEmpty()) && !useBundledTor) return@combineLiveDatas false
        val progress = torState?.progressIndicator ?: 0
        if (useBundledTor && progress < 100 && useProxy) return@combineLiveDatas false

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
        mainActivity: Activity,
        walletSeed: String,
        restoreHeightText: String,
        useOffset: Boolean,
        context: Context
    ) {
        val passphrase = _passphrase.value ?: return
        val confirmedPassphrase = _confirmedPassphrase.value ?: return

        val application = mainActivity.application as MoneroApplication
        _creatingWallet.postValue(true)
        val offset = if (useOffset) confirmedPassphrase else ""
        if (passphrase.isNotEmpty()) {
            if (passphrase != confirmedPassphrase) {
                _creatingWallet.postValue(false)
                mainActivity.runOnUiThread {
                    Toast.makeText(
                        mainActivity,
                        application.getString(R.string.invalid_confirmed_password),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
            PrefService.instance?.edit()?.putBoolean(Constants.PREF_USES_PASSWORD, true)
                ?.apply()
        }
        var restoreHeight = newRestoreHeight
        val walletFile = File(mainActivity.applicationInfo.dataDir, Constants.WALLET_NAME)
        var wallet: Wallet? = null
        if (offset.isNotEmpty()) {
            PrefService.instance?.edit()?.putBoolean(Constants.PREF_USES_OFFSET, true)?.apply()
        }
        val seedTypeValue = seedType.value ?: return
        if (walletSeed.isEmpty()) {
            if (seedTypeValue == SeedType.POLYSEED) {
                wallet = if (offset.isEmpty()) {
                    mainActivity.runOnUiThread {
                        _creatingWallet.postValue(false)
                        Toast.makeText(
                            mainActivity,
                            application.getString(R.string.invalid_empty_passphrase),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return
                } else {
                    WalletManager.instance?.createWalletPolyseed(
                        walletFile,
                        passphrase,
                        offset,
                        Constants.MNEMONIC_LANGUAGE
                    )
                }
            } else if (seedTypeValue == SeedType.LEGACY) {
                val tmpWalletFile =
                    File(mainActivity.applicationInfo.dataDir, Constants.WALLET_NAME + "_tmp")
                val tmpWallet =
                    createTempWallet(tmpWalletFile) //we do this to get seed, then recover wallet so we can use seed offset
                tmpWallet?.let {
                    wallet = WalletManager.instance?.recoveryWallet(
                        walletFile,
                        passphrase,
                        tmpWallet.getSeed("") ?: return@let,
                        offset,
                        restoreHeight
                    )
                    tmpWalletFile.delete()
                }
            }
        } else {
            if (getMnemonicType(walletSeed) == SeedType.UNKNOWN) {
                mainActivity.runOnUiThread {
                    _creatingWallet.postValue(false)
                    Toast.makeText(
                        mainActivity,
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
                wallet = WalletManager.instance?.recoveryWalletPolyseed(
                    walletFile,
                    passphrase,
                    walletSeed,
                    offset
                )
            } else if (seedTypeValue == SeedType.LEGACY) {
                wallet = WalletManager.instance?.recoveryWallet(
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
            println("KEK")
            MoneroHandlerThread.init(walletFile, passphrase, context)
            val intent = Intent(mainActivity, HomeActivity::class.java)
            mainActivity.startActivity(intent)
            mainActivity.finish()
        } else {
            mainActivity.runOnUiThread {
                _creatingWallet.postValue(false)
                Toast.makeText(
                    mainActivity,
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

    private fun createTempWallet(tmpWalletFile: File): Wallet? {
        return WalletManager.instance?.createWallet(
            tmpWalletFile,
            "",
            Constants.MNEMONIC_LANGUAGE,
            0
        )
    }

    fun setProxyAddress(address: String) {
        _proxyAddress.value = address
        if (address.isEmpty()) PrefService.instance?.deleteProxy()
        val port = _proxyPort.value ?: return
        ProxyService.instance?.updateProxy(address, port)
    }

    fun setProxyPort(port: String) {
        _proxyPort.value = port
        if (port.isEmpty()) PrefService.instance?.deleteProxy()
        val address = _proxyAddress.value ?: return
        ProxyService.instance?.updateProxy(address, port)
    }

    fun setUseBundledTor(useBundled: Boolean) {
        _useBundledTor.value = useBundled
        ProxyService.instance?.useBundledTor = useBundled

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        if (useBundled && ProxyService.instance?.usingProxy == true) {
            samouraiTorManager?.start()
        } else {
            samouraiTorManager?.stop()
        }
    }

    fun setUseProxy(useProxy: Boolean) {
        _useProxy.value = useProxy
        ProxyService.instance?.usingProxy = useProxy

        val samouraiTorManager = ProxyService.instance?.samouraiTorManager
        if (useProxy && ProxyService.instance?.useBundledTor == true) {
            samouraiTorManager?.start()
        } else {
            samouraiTorManager?.stop()
        }
    }

    fun setPassphrase(passphrase: String) {
        _passphrase.value = passphrase
    }

    fun setConfirmedPassphrase(confirmedPassphrase: String) {
        _confirmedPassphrase.value = confirmedPassphrase
    }

    fun setMonerochan(b: Boolean) {
        _showMonerochan.value = b
        PrefService.instance?.edit()?.putBoolean(Constants.PREF_MONEROCHAN, b)?.apply()
    }

    enum class SeedType(val descResId: Int) {
        LEGACY(R.string.seed_desc_legacy), POLYSEED(R.string.seed_desc_polyseed), UNKNOWN(0)
    }
}