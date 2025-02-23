package org.monfluo.wallet.fragment.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.monfluo.wallet.R
import org.monfluo.wallet.data.SeedType
import org.monfluo.wallet.livedata.combineLiveDatas
import org.monfluo.wallet.model.Wallet
import org.monfluo.wallet.util.Helper.clipBoardCopy
import org.monfluo.wallet.util.PreferenceUtils
import java.text.NumberFormat

class WalletSecretsBottomSheetDialog(
    private val wallet: Wallet,
) : BottomSheetDialogFragment() {

    private val viewModel: WalletSecretsBottomSheetDialogViewModel by viewModels()

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_dialog_wallet_secrets, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.init(wallet)

        copySecretsWarningLabelTextView = view.findViewById(R.id.copy_secrets_warning_label_textview)
        seedTypeButton = view.findViewById(R.id.seed_type_button)
        legacySeedApplySeedOffsetPassphraseTextView = view.findViewById(R.id.legacy_seed_apply_seed_offset_passphrase_textview)
        legacySeedApplySeedOffsetPassphraseSwitch = view.findViewById(R.id.legacy_seed_apply_seed_offset_passphrase_switch)
        legacySeedOffsetPassphraseDescriptionLabelTextView = view.findViewById(R.id.legacy_seed_offset_passphrase_description_label_textview)
        legacySeedOffsetPassphraseEditText = view.findViewById(R.id.legacy_seed_offset_passphrase_edittext)
        seedTextView = view.findViewById(R.id.seed_textview)
        copySeedImageButton = view.findViewById(R.id.copy_seed_imagebutton)
        polyseedOffsetPassphraseDescriptionLabelTextView = view.findViewById(R.id.polyseed_offset_passphrase_description_label_textview)
        polyseedOffsetPassphraseLayout = view.findViewById(R.id.polyseed_offset_passphrase_layout)
        polyseedOffsetPassphraseTextView = view.findViewById(R.id.polyseed_offset_passphrase_textview)
        copyPolyseedOffsetPassphraseImageButton = view.findViewById(R.id.copy_polyseed_offset_passphrase_imagebutton)
        restoreHeightTextView = view.findViewById(R.id.restore_height_textview)
        copyRestoreHeightImageButton = view.findViewById(R.id.copy_restore_height_imagebutton)
        walletAddressTextView = view.findViewById(R.id.wallet_address_textview)
        copyWalletAddressImageButton = view.findViewById(R.id.copy_wallet_address_imagebutton)
        walletSecretViewKeyTextView = view.findViewById(R.id.wallet_secret_view_key_textview)
        copyWalletSecretViewKeyImageButton = view.findViewById(R.id.copy_wallet_secret_view_key_imagebutton)
        walletPublicViewKeyTextView = view.findViewById(R.id.wallet_public_view_key_textview)
        copyWalletPublicViewKeyImageButton = view.findViewById(R.id.copy_wallet_public_view_key_imagebutton)
        walletSecretSpendKeyTextView = view.findViewById(R.id.wallet_secret_spend_key_textview)
        copyWalletSecretSpendKeyImageButton = view.findViewById(R.id.copy_wallet_secret_spend_key_imagebutton)
        walletPublicSpendKeyTextView = view.findViewById(R.id.wallet_public_spend_key_textview)
        copyWalletPublicSpendKeyImageButton = view.findViewById(R.id.copy_wallet_public_spend_key_imagebutton)

        bindListeners()
        bindObservers()
    }

    override fun onResume() {
        super.onResume()

        val isAllowCopyingSecrets = PreferenceUtils.isAllowCopyingSecrets(requireContext())
        viewModel.allowCopyingSecrets.value = isAllowCopyingSecrets
    }

    private fun bindListeners() {
        seedTypeButton.setOnClickListener {
            val newSeedType = when (viewModel.seedType.value!!) {
                SeedType.MONERO -> SeedType.POLYSEED
                SeedType.POLYSEED -> SeedType.MONERO
            }
            viewModel.setSeedType(newSeedType)
        }
        legacySeedApplySeedOffsetPassphraseSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.setUseLegacySeedOffsetPassphrase(checked)
        }
        legacySeedOffsetPassphraseEditText.doAfterTextChanged { legacySeedOffsetPassphrase ->
            legacySeedOffsetPassphrase?.let {
                viewModel.setLegacySeedOffsetPassphrase(it.toString())
            }
        }
        copySeedImageButton.setOnClickListener {
            clipBoardCopy(context, "monero seed", viewModel.seed.value!!)
        }
        copyPolyseedOffsetPassphraseImageButton.setOnClickListener {
            clipBoardCopy(context, "monero polyseed offset passphrase", viewModel.polyseedOffsetPassphrase.value!!)
        }
        copyRestoreHeightImageButton.setOnClickListener {
            clipBoardCopy(context, "monero restore height", viewModel.restoreHeight.value!!.toString())
        }
        copyWalletAddressImageButton.setOnClickListener {
            clipBoardCopy(context, "monero wallet address", viewModel.walletAddress.value!!.toString())
        }
        copyWalletSecretViewKeyImageButton.setOnClickListener {
            clipBoardCopy(context, "secret view-key", viewModel.secretViewKey.value!!)
        }
        copyWalletPublicViewKeyImageButton.setOnClickListener {
            clipBoardCopy(context, "public view-key", viewModel.publicViewKey.value!!)
        }
        copyWalletSecretSpendKeyImageButton.setOnClickListener {
            clipBoardCopy(context, "secret spend-key", viewModel.secretSpendKey.value!!)
        }
        copyWalletPublicSpendKeyImageButton.setOnClickListener {
            clipBoardCopy(context, "public spend-key", viewModel.publicSpendKey.value!!)
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
        viewModel.walletAddress.observe(this) { walletAddress ->
            walletAddressTextView.text = walletAddress
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
}

class WalletSecretsBottomSheetDialogViewModel : ViewModel() {

    private lateinit var wallet: Wallet

    fun init(wallet: Wallet) {
        this.wallet = wallet
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
        walletAddress.value = wallet.getAddress(0)
        secretViewKey.value = wallet.getSecretViewKey()
        publicViewKey.value = wallet.getPublicViewKey()
        secretSpendKey.value = wallet.getSecretSpendKey()
        publicSpendKey.value = wallet.getPublicSpendKey()
        updateSeed()
    }

    val isWalletPolyseed = MutableLiveData(false)
    val allowCopyingSecrets = MutableLiveData(false)
    private val _seedType = MutableLiveData(SeedType.MONERO)
    val seedType: LiveData<SeedType> = _seedType
    private val _useLegacySeedOffsetPassphrase = MutableLiveData(false)
    private val _legacySeedOffsetPassphrase = MutableLiveData("")
    val seed = MutableLiveData("")
    val polyseedOffsetPassphrase = MutableLiveData("")
    val restoreHeight = MutableLiveData(0L)
    val walletAddress = MutableLiveData("")
    val secretViewKey = MutableLiveData("")
    val publicViewKey = MutableLiveData("")
    val secretSpendKey = MutableLiveData("")
    val publicSpendKey = MutableLiveData("")

    val seedTypeAndUseLegacySeedOffsetPassphrase: LiveData<Pair<SeedType, Boolean>> = combineLiveDatas(
        _seedType,
        _useLegacySeedOffsetPassphrase,
    ) { seedType, useLegacySeedOffsetPassphrase ->
        return@combineLiveDatas Pair(seedType!!, useLegacySeedOffsetPassphrase!!)
    }

    val seedTypeAndPolyseedSeedOffsetPassphrase: LiveData<Pair<SeedType, String>> = combineLiveDatas(
        _seedType,
        polyseedOffsetPassphrase,
    ) { seedType, polyseedOffsetPassphrase ->
        return@combineLiveDatas Pair(seedType!!, polyseedOffsetPassphrase!!)
    }

    fun setSeedType(seedType: SeedType) {
        _seedType.value = seedType
        updateSeed()
    }

    fun setUseLegacySeedOffsetPassphrase(useLegacySeedOffsetPassphrase: Boolean) {
        _useLegacySeedOffsetPassphrase.value = useLegacySeedOffsetPassphrase
        updateSeed()
    }

    fun setLegacySeedOffsetPassphrase(legacySeedOffsetPassphrase: String) {
        _legacySeedOffsetPassphrase.value = legacySeedOffsetPassphrase
        updateSeed()
    }

    private fun updateSeed() {
        when (_seedType.value!!) {
            SeedType.MONERO -> {
                if (_useLegacySeedOffsetPassphrase.value!!) {
                    seed.value = wallet.getLegacySeed(_legacySeedOffsetPassphrase.value!!)
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