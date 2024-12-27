package net.mynero.wallet.fragment.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.mynero.wallet.R
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.util.Helper.clipBoardCopy

class WalletKeysBottomSheetDialog(
    private val wallet: Wallet,
    private val privateViewKey: String,
    private val restoreHeight: Long,
) : BottomSheetDialogFragment() {

    private val viewModel: WalletKeysBottomSheetDialogViewModel by viewModels()

    private lateinit var encryptSeedSwitch: SwitchCompat
    private lateinit var copyViewKeyImageButton: ImageButton
    private lateinit var seedTextView: TextView
    private lateinit var viewKeyTextView: TextView
    private lateinit var restoreHeightTextView: TextView
    private lateinit var walletSeedOffset: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.wallet_keys_bottom_sheet_dialog, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        encryptSeedSwitch = view.findViewById(R.id.encrypt_seed_switch)
        copyViewKeyImageButton = view.findViewById(R.id.copy_viewkey_imagebutton)
        seedTextView = view.findViewById(R.id.seed_textview)
        viewKeyTextView = view.findViewById(R.id.viewkey_textview)
        restoreHeightTextView = view.findViewById(R.id.restore_height_textview)
        walletSeedOffset = view.findViewById(R.id.wallet_seed_offset_textview)

        viewKeyTextView.text = privateViewKey
        restoreHeightTextView.text = "$restoreHeight"

        bindListeners()
        bindObservers()
    }

    private fun bindListeners() {
        encryptSeedSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.setUseOffset(checked)
        }
        copyViewKeyImageButton.setOnClickListener {
            clipBoardCopy(
                context, "private view-key", privateViewKey
            )
        }
    }

    private fun bindObservers() {
        viewModel.useOffset.observe(this) { useOffset ->
            if (useOffset) {
                seedTextView.text = wallet.getSeed(wallet.info.password)
                walletSeedOffset.visibility = View.VISIBLE
            } else {
                seedTextView.text = wallet.getSeed("")
                walletSeedOffset.visibility = View.INVISIBLE
            }
        }
    }
}

class WalletKeysBottomSheetDialogViewModel : ViewModel() {
    private val _useOffset = MutableLiveData(false)
    val useOffset: LiveData<Boolean> = _useOffset

    fun setUseOffset(useOffset: Boolean) {
        _useOffset.value = useOffset
    }
}