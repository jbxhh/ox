package org.monfluo.wallet.android.activity

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.monfluo.wallet.android.MoneroApplication
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.data.SeedType
import org.monfluo.wallet.android.livedata.combineLiveDatas
import org.monfluo.wallet.android.model.MoneroNetworkType
import org.monfluo.wallet.android.model.WalletManager
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.Helper
import org.monfluo.wallet.android.util.PreferenceUtils
import org.monfluo.wallet.android.util.SimpleTextWatcher
import org.monfluo.wallet.android.util.TextHelper
import org.monfluo.wallet.android.util.component.ActivityBase
import org.monfluo.wallet.lib.Monfluo
import org.monfluo.wallet.lib.seed.MoneroSeed
import org.monfluo.wallet.lib.seed.MoneroSeedLanguage
import org.monfluo.wallet.lib.seed.Polyseed
import org.monfluo.wallet.lib.seed.PolyseedLanguage
import org.monfluo.wallet.lib.seed.Seed
import timber.log.Timber
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.math.max

class ActivityWalletCreate : ActivityBase() {
    private val viewModel: CreateWalletViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var applicationSettingsImageView: ImageView
    private lateinit var walletNameEditText: EditText
    private lateinit var walletPasswordEditText: EditText
    private lateinit var walletPasswordConfirmEditText: EditText
    private lateinit var seedTypeButton: Button
    private lateinit var moreOptionsDropdownTextView: TextView
    private lateinit var moreOptionsChevronImageView: ImageView
    private lateinit var advancedOptionsLayout: ConstraintLayout
    private lateinit var seedTypeDescTextView: TextView
    private lateinit var networkTypeButton: Button
    private lateinit var networkTypeLabelTextView: TextView
    private lateinit var walletSeedEditText: EditText
    private lateinit var walletHeightLabelTextView: TextView
    private lateinit var walletRestoreHeightEditText: EditText
    private lateinit var walletSeedOffsetLabelTextView: TextView
    private lateinit var walletSeedOffsetPassphraseEditText: EditText
    private lateinit var createWalletButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_create)

        layoutView = findViewById(R.id.create_wallet_activity_view)
        backImageView = findViewById(R.id.back_imageview)
        applicationSettingsImageView = findViewById(R.id.application_settings_imageview)
        walletNameEditText = findViewById(R.id.wallet_name_edittext)
        walletPasswordEditText = findViewById(R.id.wallet_password_edittext)
        walletPasswordConfirmEditText = findViewById(R.id.wallet_password_confirm_edittext)
        seedTypeButton = findViewById(R.id.seed_type_button)
        moreOptionsDropdownTextView = findViewById(R.id.advanced_settings_dropdown_textview)
        moreOptionsChevronImageView = findViewById(R.id.advanced_settings_chevron_imageview)
        advancedOptionsLayout = findViewById(R.id.more_options_layout)
        seedTypeDescTextView = findViewById(R.id.seed_type_desc_textview)
        networkTypeButton = findViewById(R.id.network_type_button)
        networkTypeLabelTextView = findViewById(R.id.network_type_label_textview)
        walletSeedEditText = findViewById(R.id.wallet_seed_edittext)
        walletHeightLabelTextView = findViewById(R.id.wallet_height_label_textview)
        walletRestoreHeightEditText = findViewById(R.id.wallet_restore_height_edittext)
        walletSeedOffsetLabelTextView = findViewById(R.id.wallet_seed_offset_label_textview)
        walletSeedOffsetPassphraseEditText = findViewById(R.id.wallet_seed_offset_passphrase_edittext)
        createWalletButton = findViewById(R.id.create_wallet_button)

        walletNameEditText.setText(getWalletNameSuggestion())

        bindWindowInsetsListener()
        bindBackPressedCallback()
        bindListeners()
        bindObservers()

        Helper.requestPostNotificationsPermission(this)
    }

    override fun onResume() {
        super.onResume()

        val isDeveloperModeEnabled = PreferenceUtils.isDeveloperModeEnabled(applicationContext)
        viewModel.developerModeEnabled.value = isDeveloperModeEnabled
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

    private fun bindBackPressedCallback() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!intent.getBooleanExtra(Constants.EXTRA_PREVENT_GOING_BACK, false)) {
                        finish()
                    }
                }
            },
        )
    }

    private fun bindListeners() {
        backImageView.setOnClickListener {
            finish()
        }

        applicationSettingsImageView.setOnClickListener {
            startActivity(Intent(this, ActivityApplicationSettings::class.java))
        }

        moreOptionsDropdownTextView.setOnClickListener { viewModel.showMoreOptions.value = !viewModel.showMoreOptions.value!! }

        moreOptionsChevronImageView.setOnClickListener { viewModel.showMoreOptions.value = !viewModel.showMoreOptions.value!! }

        createWalletButton.setOnClickListener {
            createOrImportWallet(
                walletSeedEditText.text.toString().trim(),
                walletRestoreHeightEditText.text.toString().trim(),
            )
        }

        walletPasswordEditText.addTextChangedListener(
            object : SimpleTextWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    viewModel.setPassphrase(editable.toString())
                }
            },
        )

        walletSeedEditText.addTextChangedListener(
            object : SimpleTextWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    val text = editable.toString()
                    colorSeed(editable, PolyseedLanguage.getWords(PolyseedLanguage.English))
                    viewModel.seed.value = text
                    if (text.isEmpty()) {
                        createWalletButton.setText(R.string.create_wallet)
                    } else {
                        createWalletButton.setText(R.string.restore_wallet)
                    }
                }
            },
        )

        walletPasswordConfirmEditText.addTextChangedListener(
            object : SimpleTextWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    viewModel.setConfirmedPassphrase(editable.toString())
                }
            },
        )

        walletSeedEditText.addTextChangedListener(
            object : SimpleTextWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    val text = editable.toString()
                    viewModel.seed.value = text
                    if (text.isEmpty()) {
                        createWalletButton.setText(R.string.create_wallet)
                    } else {
                        createWalletButton.setText(R.string.restore_wallet)
                    }
                }
            },
        )

        walletSeedOffsetPassphraseEditText.addTextChangedListener(
            object : SimpleTextWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    viewModel.seedOffsetPassphrase.value = editable.toString()
                }
            },
        )

        seedTypeButton.setOnClickListener { toggleSeedType() }
        networkTypeButton.setOnClickListener { toggleNetworkType() }
    }

    private fun bindObservers() {
        viewModel.password.observe(this) { text ->
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

        viewModel.seedType.observe(this) { seedType ->
            seedTypeButton.text = seedType.toString()
            seedTypeDescTextView.text = getText(seedType.getDescriptionResourceId())
            if (seedType == SeedType.MONERO) {
                walletHeightLabelTextView.visibility = View.VISIBLE
                walletRestoreHeightEditText.visibility = View.VISIBLE
                walletSeedEditText.hint = getString(R.string.recovery_phrase_optional_legacy)
            } else {
                walletHeightLabelTextView.visibility = View.GONE
                walletRestoreHeightEditText.visibility = View.GONE
                walletSeedEditText.hint = getString(R.string.recovery_phrase_optional_polyseed)
            }
        }

        viewModel.seedAndType.observe(this) { (seedType, seed) ->
            if (seed.isNotBlank() || seedType == SeedType.POLYSEED) {
                walletSeedOffsetLabelTextView.visibility = View.VISIBLE
                walletSeedOffsetPassphraseEditText.visibility = View.VISIBLE
            } else {
                walletSeedOffsetLabelTextView.visibility = View.GONE
                walletSeedOffsetPassphraseEditText.visibility = View.GONE
            }
        }

        viewModel.networkType.observe(this) { networkType ->
            networkTypeButton.text = getString(networkType.resourceId)
        }

        viewModel.developerModeEnabled.observe(this) { isDeveloperModeEnabled ->
            if (isDeveloperModeEnabled) {
                networkTypeButton.visibility = View.VISIBLE
                networkTypeLabelTextView.visibility = View.VISIBLE
            } else {
                networkTypeButton.visibility = View.GONE
                networkTypeLabelTextView.visibility = View.GONE
            }
        }
    }

    private fun toggleSeedType() {
        val seedType = viewModel.seedType.value ?: return
        val newSeedType =
            if (seedType == SeedType.POLYSEED) {
                SeedType.MONERO
            } else {
                SeedType.POLYSEED
            }
        viewModel.seedType.value = newSeedType
    }

    fun toggleNetworkType() {
        viewModel.networkType.value = viewModel.networkType.value!!.next()
    }

    private fun createOrImportWallet(
        walletSeed: String,
        restoreHeightText: String,
    ) {
        val walletName = walletNameEditText.text.toString()
        if (!Helper.isValidWalletName(walletName)) {
            Toast.makeText(this, getString(R.string.toast_invalid_wallet_name), Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.createOrImportWallet(
                this@ActivityWalletCreate,
                walletName,
                walletSeed,
                restoreHeightText,
            )
        }
    }

    private fun getWalletNameSuggestion(): String {
        val wallets = Monfluo.listWallets(Helper.getWalletRoot(this).absolutePath).map { Path(it).name }.toSet()
        if (!wallets.contains(Constants.DEFAULT_WALLET_NAME)) {
            return Constants.DEFAULT_WALLET_NAME
        }
        for (i in 2 until Int.MAX_VALUE) {
            val name = "${Constants.DEFAULT_WALLET_NAME} ($i)"
            if (!wallets.contains(name)) {
                return name
            }
        }
        Timber.e("User managed to create 2^31-3 wallets, giving up on finding a good wallet name")
        return Constants.DEFAULT_WALLET_NAME
    }

    private fun colorSeed(
        editable: Editable,
        validWords: Set<String>,
    ) {
        editable.getSpans(0, editable.length + 1, ForegroundColorSpan::class.java).forEach {
            editable.removeSpan(it)
        }
        TextHelper
            .groupWordsBy(editable) { word ->
                validWords.contains(word)
            }.forEach { (isValidWord, start, end) ->
                val colorResourceId = if (isValidWord) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                val color = resources.getColor(colorResourceId)
                editable.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
    }
}

internal class CreateWalletViewModel : ViewModel() {
    val seedType = MutableLiveData(SeedType.MONERO)
    val showMoreOptions = MutableLiveData(false)
    val developerModeEnabled = MutableLiveData(false)
    val networkType = MutableLiveData(MoneroNetworkType.NetworkType_Mainnet)
    val password: MutableLiveData<String> = MutableLiveData("")
    private val confirmedPassphrase = MutableLiveData("")
    val seed = MutableLiveData("")
    val seedOffsetPassphrase = MutableLiveData("")
    private val creatingWallet = MutableLiveData(false)

    val enableButton =
        combineLiveDatas(
            seedType,
            password,
            confirmedPassphrase,
            creatingWallet,
        ) { seedType, passphrase, confirmedPassphrase, creatingWallet ->
            if (seedType == null || passphrase == null || confirmedPassphrase == null || creatingWallet == null) return@combineLiveDatas false
            if ((passphrase.isNotEmpty() || confirmedPassphrase.isNotEmpty()) && passphrase != confirmedPassphrase) return@combineLiveDatas false
            if (creatingWallet) return@combineLiveDatas false

            return@combineLiveDatas true
        }

    val seedAndType =
        combineLiveDatas(
            seedType,
            seed,
        ) { seedType, seed ->
            Pair(seedType!!, seed!!)
        }

    fun createOrImportWallet(
        activity: ActivityWalletCreate,
        walletName: String,
        walletSeed: String,
        restoreHeightText: String,
    ) {
        val password = password.value ?: return
        val confirmedPassword = confirmedPassphrase.value ?: return

        val application = activity.application as MoneroApplication
        creatingWallet.postValue(true)

        val offset = seedOffsetPassphrase.value!!
        if (password.isNotEmpty()) {
            if (password != confirmedPassword) {
                creatingWallet.postValue(false)
                activity.runOnUiThread {
                    Toast
                        .makeText(
                            activity,
                            application.getString(R.string.invalid_confirmed_password),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                return
            }
        }
        val walletFile = File(Helper.getWalletRoot(activity), walletName)
        val seedTypeValue = seedType.value ?: return

        if (seedTypeValue == SeedType.POLYSEED && WalletManager.isEncryptedPolyseed(walletSeed)) {
            creatingWallet.postValue(false)
            activity.runOnUiThread {
                Toast
                    .makeText(
                        activity,
                        application.getString(R.string.activity_wallet_create_wallet_encrypted_polyseed_toast),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
            return
        }

        if (walletSeed.isNotBlank()) {
            val seed: Seed<*>? = Polyseed.fromMnemonic(walletSeed) ?: MoneroSeed.fromMnemonic(walletSeed)
            if (seed == null) {
                activity.runOnUiThread {
                    Toast
                        .makeText(
                            activity,
                            "Bad seed!",
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                return
            }
            Monfluo.restoreWallet(Helper.getWalletRoot(activity).absolutePath, walletName, password, seed, null, restoreHeightText.toIntOrNull())

            activity.runOnUiThread {
                Toast
                    .makeText(
                        activity,
                        "Wallet restored!",
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        } else {
            Monfluo.createWallet(Helper.getWalletRoot(activity).absolutePath, walletName, password, MoneroSeedLanguage.English, "")

            activity.runOnUiThread {
                Toast
                    .makeText(
                        activity,
                        "Wallet created!",
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }

        activity.finish()
    }

    private fun getMnemonicType(seed: String): SeedType? {
        val words = seed.split("\\s".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val seedTypeValue = seedType.value ?: return SeedType.MONERO
        return if (words.size == 16 && seedTypeValue == SeedType.POLYSEED) {
            SeedType.POLYSEED
        } else if (words.size == 25 && seedTypeValue == SeedType.MONERO) {
            SeedType.MONERO
        } else {
            null
        }
    }

    fun setPassphrase(passphrase: String) {
        this.password.value = passphrase
    }

    fun setConfirmedPassphrase(confirmedPassphrase: String) {
        this.confirmedPassphrase.value = confirmedPassphrase
    }
}
