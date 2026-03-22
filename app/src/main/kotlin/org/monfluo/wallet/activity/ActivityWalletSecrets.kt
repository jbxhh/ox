package org.monfluo.wallet.activity

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.R
import org.monfluo.wallet.data.SeedType
import org.monfluo.wallet.livedata.combineLiveDatas
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.service.wallet.WalletService
import org.monfluo.wallet.util.Helper.clipBoardCopy
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.util.TextHelper
import org.monfluo.wallet.util.component.ActivityMonero
import java.text.NumberFormat
import kotlin.math.max

class ActivityWalletSecrets : ActivityMonero() {
    private val viewModel: ActivityWalletSecretsViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var copySecretsWarningLabelTextView: TextView
    private lateinit var seedTypeButton: TextView
    private lateinit var legacySeedApplySeedOffsetPassphraseTextView: TextView
    private lateinit var legacySeedApplySeedOffsetPassphraseSwitch: SwitchCompat
    private lateinit var legacySeedOffsetPassphraseDescriptionLabelTextView: TextView
    private lateinit var legacySeedOffsetPassphraseEditText: EditText
    private lateinit var seedTextView: TextView
    private lateinit var copySeedImageButton: ImageButton
    private lateinit var polyseedOffsetPassphraseDescriptionLabelTextView: TextView
    private lateinit var polyseedOffsetPassphraseLayout: ConstraintLayout
    private lateinit var polyseedOffsetPassphraseTextView: TextView
    private lateinit var copyPolyseedOffsetPassphraseImageButton: ImageButton
    private lateinit var restoreHeightTextView: TextView
    private lateinit var copyRestoreHeightImageButton: ImageButton
    private lateinit var adjustedRestoreHeightTextView: TextView
    private lateinit var copyAdjustedRestoreHeightImageButton: ImageButton
    private lateinit var walletAddressTextView: TextView
    private lateinit var copyWalletAddressImageButton: ImageButton
    private lateinit var walletSecretViewKeyTextView: TextView
    private lateinit var copyWalletSecretViewKeyImageButton: ImageButton
    private lateinit var walletPublicViewKeyTextView: TextView
    private lateinit var copyWalletPublicViewKeyImageButton: ImageButton
    private lateinit var walletSecretSpendKeyTextView: TextView
    private lateinit var copyWalletSecretSpendKeyImageButton: ImageButton
    private lateinit var walletPublicSpendKeyTextView: TextView
    private lateinit var copyWalletPublicSpendKeyImageButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_secrets)

        layoutView = findViewById(R.id.activity_wallet_secrets_layout)
        backImageView = findViewById(R.id.back_imageview)
        copySecretsWarningLabelTextView = findViewById(R.id.copy_secrets_warning_label_textview)
        seedTypeButton = findViewById(R.id.seed_type_button)
        legacySeedApplySeedOffsetPassphraseTextView = findViewById(R.id.legacy_seed_apply_seed_offset_passphrase_textview)
        legacySeedApplySeedOffsetPassphraseSwitch = findViewById(R.id.legacy_seed_apply_seed_offset_passphrase_switch)
        legacySeedOffsetPassphraseDescriptionLabelTextView = findViewById(R.id.legacy_seed_offset_passphrase_description_label_textview)
        legacySeedOffsetPassphraseEditText = findViewById(R.id.legacy_seed_offset_passphrase_edittext)
        seedTextView = findViewById(R.id.seed_textview)
        copySeedImageButton = findViewById(R.id.copy_seed_imagebutton)
        polyseedOffsetPassphraseDescriptionLabelTextView = findViewById(R.id.polyseed_offset_passphrase_description_label_textview)
        polyseedOffsetPassphraseLayout = findViewById(R.id.polyseed_offset_passphrase_layout)
        polyseedOffsetPassphraseTextView = findViewById(R.id.polyseed_offset_passphrase_textview)
        copyPolyseedOffsetPassphraseImageButton = findViewById(R.id.copy_polyseed_offset_passphrase_imagebutton)
        restoreHeightTextView = findViewById(R.id.restore_height_textview)
        copyRestoreHeightImageButton = findViewById(R.id.copy_restore_height_imagebutton)
        adjustedRestoreHeightTextView = findViewById(R.id.adjusted_restore_height_textview)
        copyAdjustedRestoreHeightImageButton = findViewById(R.id.copy_adjusted_restore_height_imagebutton)
        walletAddressTextView = findViewById(R.id.wallet_address_textview)
        copyWalletAddressImageButton = findViewById(R.id.copy_wallet_address_imagebutton)
        walletSecretViewKeyTextView = findViewById(R.id.wallet_secret_view_key_textview)
        copyWalletSecretViewKeyImageButton = findViewById(R.id.copy_wallet_secret_view_key_imagebutton)
        walletPublicViewKeyTextView = findViewById(R.id.wallet_public_view_key_textview)
        copyWalletPublicViewKeyImageButton = findViewById(R.id.copy_wallet_public_view_key_imagebutton)
        walletSecretSpendKeyTextView = findViewById(R.id.wallet_secret_spend_key_textview)
        copyWalletSecretSpendKeyImageButton = findViewById(R.id.copy_wallet_secret_spend_key_imagebutton)
        walletPublicSpendKeyTextView = findViewById(R.id.wallet_public_spend_key_textview)
        copyWalletPublicSpendKeyImageButton = findViewById(R.id.copy_wallet_public_spend_key_imagebutton)

        bindWindowInsetsListener()
        bindListeners()
        bindObservers()
    }

    override fun onResume() {
        super.onResume()

        val isAllowCopyingSecrets = PreferenceUtils.isAllowCopyingSecrets(this)

        viewModel.allowCopyingSecrets.value = isAllowCopyingSecrets
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
        seedTypeButton.setOnClickListener {
            walletService?.getWalletOrLog()?.let { wallet ->
                val newSeedType =
                    when (viewModel.seedType.value!!) {
                        SeedType.MONERO -> SeedType.POLYSEED
                        SeedType.POLYSEED -> SeedType.MONERO
                    }
                viewModel.setSeedType(newSeedType, wallet)
            }
        }
        legacySeedApplySeedOffsetPassphraseSwitch.setOnCheckedChangeListener { _, checked ->
            walletService?.getWalletOrLog()?.let { wallet ->
                viewModel.setUseLegacySeedOffsetPassphrase(checked, wallet)
            }
        }
        legacySeedOffsetPassphraseEditText.doAfterTextChanged { legacySeedOffsetPassphrase ->
            walletService?.getWalletOrLog()?.let { wallet ->
                legacySeedOffsetPassphrase?.let {
                    viewModel.setLegacySeedOffsetPassphrase(it.toString(), wallet)
                }
            }
        }
        copySeedImageButton.setOnClickListener {
            clipBoardCopy(this, "monero seed", viewModel.seed.value!!, sensitive = true)
        }
        copyPolyseedOffsetPassphraseImageButton.setOnClickListener {
            clipBoardCopy(this, "monero polyseed offset passphrase", viewModel.polyseedOffsetPassphrase.value!!, sensitive = true)
        }
        copyRestoreHeightImageButton.setOnClickListener {
            clipBoardCopy(this, "monero restore height", viewModel.restoreHeight.value!!.toString())
        }
        copyWalletAddressImageButton.setOnClickListener {
            clipBoardCopy(this, "monero wallet address", viewModel.walletAddress.value!!)
        }
        copyWalletSecretViewKeyImageButton.setOnClickListener {
            clipBoardCopy(this, "secret view-key", viewModel.secretViewKey.value!!, sensitive = true)
        }
        copyWalletPublicViewKeyImageButton.setOnClickListener {
            clipBoardCopy(this, "public view-key", viewModel.publicViewKey.value!!)
        }
        copyWalletSecretSpendKeyImageButton.setOnClickListener {
            clipBoardCopy(this, "secret spend-key", viewModel.secretSpendKey.value!!, sensitive = true)
        }
        copyWalletPublicSpendKeyImageButton.setOnClickListener {
            clipBoardCopy(this, "public spend-key", viewModel.publicSpendKey.value!!)
        }
    }

    private fun bindObservers() {
        viewModel.allowCopyingSecrets.observe(this) { allowCopyingSecrets ->
            if (allowCopyingSecrets) {
                copySecretsWarningLabelTextView.visibility = View.VISIBLE
                copySeedImageButton.visibility = View.VISIBLE
                copyPolyseedOffsetPassphraseImageButton.visibility = View.VISIBLE
                copyWalletSecretViewKeyImageButton.visibility = View.VISIBLE
                copyWalletSecretSpendKeyImageButton.visibility = View.VISIBLE
            } else {
                copySecretsWarningLabelTextView.visibility = View.GONE
                copySeedImageButton.visibility = View.INVISIBLE
                copyPolyseedOffsetPassphraseImageButton.visibility = View.INVISIBLE
                copyWalletSecretViewKeyImageButton.visibility = View.INVISIBLE
                copyWalletSecretSpendKeyImageButton.visibility = View.INVISIBLE
            }
        }
        viewModel.isWalletPolyseed.observe(this) { isPolyseed ->
            // Polyseed can be converted to legacy seed, but not the other way around
            seedTypeButton.isEnabled = isPolyseed
        }
        viewModel.seedTypeAndUseLegacySeedOffsetPassphrase.observe(this) { (seedType, useLegacySeedOffsetPassphrase) ->
            if (seedType == SeedType.MONERO && useLegacySeedOffsetPassphrase) {
                legacySeedOffsetPassphraseDescriptionLabelTextView.visibility = View.VISIBLE
                legacySeedOffsetPassphraseEditText.visibility = View.VISIBLE
            } else {
                legacySeedOffsetPassphraseDescriptionLabelTextView.visibility = View.GONE
                legacySeedOffsetPassphraseEditText.visibility = View.GONE
            }
        }
        viewModel.seedTypeAndPolyseedSeedOffsetPassphrase.observe(this) { (seedType, polyseedOffsetPassphrase) ->
            seedTypeButton.text = seedType.name
            when (seedType) {
                SeedType.MONERO -> {
                    legacySeedApplySeedOffsetPassphraseTextView.visibility = View.VISIBLE
                    legacySeedApplySeedOffsetPassphraseSwitch.visibility = View.VISIBLE

                    polyseedOffsetPassphraseDescriptionLabelTextView.visibility = View.GONE
                    polyseedOffsetPassphraseLayout.visibility = View.GONE
                }
                SeedType.POLYSEED -> {
                    legacySeedApplySeedOffsetPassphraseTextView.visibility = View.GONE
                    legacySeedApplySeedOffsetPassphraseSwitch.visibility = View.GONE

                    if (polyseedOffsetPassphrase.isNotEmpty()) {
                        polyseedOffsetPassphraseDescriptionLabelTextView.visibility = View.VISIBLE
                        polyseedOffsetPassphraseLayout.visibility = View.VISIBLE
                    } else {
                        polyseedOffsetPassphraseDescriptionLabelTextView.visibility = View.GONE
                        polyseedOffsetPassphraseLayout.visibility = View.GONE
                    }
                }
            }
        }
        viewModel.polyseedOffsetPassphrase.observe(this) { polyseedOffsetPassphrase ->
            polyseedOffsetPassphraseTextView.text = polyseedOffsetPassphrase
        }
        viewModel.seed.observe(this) { seed ->
            seedTextView.text = seed
        }
        viewModel.restoreHeight.observe(this) { restoreHeight ->
            restoreHeightTextView.text = NumberFormat.getInstance().format(restoreHeight)
        }
        viewModel.adjustedRestoreHeight.observe(this) { adjustedRestoreHeight ->
            adjustedRestoreHeightTextView.text = NumberFormat.getInstance().format(adjustedRestoreHeight)
        }
        viewModel.walletAddress.observe(this) { walletAddress ->
            walletAddressTextView.text = TextHelper.formatAddress(walletAddress)
        }
        viewModel.secretViewKey.observe(this) { secretViewKey ->
            walletSecretViewKeyTextView.text = secretViewKey
        }
        viewModel.publicViewKey.observe(this) { publicViewKey ->
            walletPublicViewKeyTextView.text = publicViewKey
        }
        viewModel.secretSpendKey.observe(this) { secretSpendKey ->
            walletSecretSpendKeyTextView.text = secretSpendKey
        }
        viewModel.publicSpendKey.observe(this) { publicSpendKey ->
            walletPublicSpendKeyTextView.text = publicSpendKey
        }
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService.getWalletOrLog()?.let { wallet -> viewModel.init(wallet) }
    }

    override fun onWalletUpdated(wallet: Wallet) {
        runOnUiThread {
            viewModel.init(wallet)
        }
    }
}

internal class ActivityWalletSecretsViewModel : ViewModel() {
    fun init(wallet: Wallet) {
        val polyseedAndOffset = wallet.getPolyseedSeedAndPassphrase()
        if (polyseedAndOffset != null) {
            isWalletPolyseed.value = true
            _seedType.value = SeedType.POLYSEED
            polyseedOffsetPassphrase.value = polyseedAndOffset.second
        } else {
            isWalletPolyseed.value = false
            _seedType.value = SeedType.MONERO
        }
        restoreHeight.value = wallet.getRestoreHeight()
        adjustedRestoreHeight.value = wallet.getAdjustedRestoreHeight()
        walletAddress.value = wallet.getAddress(0, 0)
        secretViewKey.value = wallet.getSecretViewKey()
        publicViewKey.value = wallet.getPublicViewKey()
        secretSpendKey.value = wallet.getSecretSpendKey()
        publicSpendKey.value = wallet.getPublicSpendKey()
        updateSeed(wallet)
    }

    val isWalletPolyseed = MutableLiveData(false)
    val allowCopyingSecrets = MutableLiveData(false)
    private val _seedType = MutableLiveData(SeedType.MONERO)
    val seedType: LiveData<SeedType> = _seedType
    private val useLegacySeedOffsetPassphrase = MutableLiveData(false)
    private val legacySeedOffsetPassphrase = MutableLiveData("")
    val seed = MutableLiveData("")
    val polyseedOffsetPassphrase = MutableLiveData("")
    val restoreHeight = MutableLiveData(0L)
    val adjustedRestoreHeight = MutableLiveData(0L)
    val walletAddress = MutableLiveData("")
    val secretViewKey = MutableLiveData("")
    val publicViewKey = MutableLiveData("")
    val secretSpendKey = MutableLiveData("")
    val publicSpendKey = MutableLiveData("")

    val seedTypeAndUseLegacySeedOffsetPassphrase: LiveData<Pair<SeedType, Boolean>> =
        combineLiveDatas(
            _seedType,
            useLegacySeedOffsetPassphrase,
        ) { seedType, useLegacySeedOffsetPassphrase ->
            return@combineLiveDatas Pair(seedType!!, useLegacySeedOffsetPassphrase!!)
        }

    val seedTypeAndPolyseedSeedOffsetPassphrase: LiveData<Pair<SeedType, String>> =
        combineLiveDatas(
            _seedType,
            polyseedOffsetPassphrase,
        ) { seedType, polyseedOffsetPassphrase ->
            return@combineLiveDatas Pair(seedType!!, polyseedOffsetPassphrase!!)
        }

    fun setSeedType(
        seedType: SeedType,
        wallet: Wallet,
    ) {
        _seedType.value = seedType
        updateSeed(wallet)
    }

    fun setUseLegacySeedOffsetPassphrase(
        useLegacySeedOffsetPassphrase: Boolean,
        wallet: Wallet,
    ) {
        this.useLegacySeedOffsetPassphrase.value = useLegacySeedOffsetPassphrase
        updateSeed(wallet)
    }

    fun setLegacySeedOffsetPassphrase(
        legacySeedOffsetPassphrase: String,
        wallet: Wallet,
    ) {
        this.legacySeedOffsetPassphrase.value = legacySeedOffsetPassphrase
        updateSeed(wallet)
    }

    private fun updateSeed(wallet: Wallet) {
        when (_seedType.value!!) {
            SeedType.MONERO -> {
                if (useLegacySeedOffsetPassphrase.value!!) {
                    seed.value = wallet.getLegacySeed(legacySeedOffsetPassphrase.value!!)
                } else {
                    seed.value = wallet.getLegacySeed("")
                }
            }
            SeedType.POLYSEED -> {
                seed.value = wallet.getSeed("")
            }
        }
    }
}
