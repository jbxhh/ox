package org.monfluo.wallet.fragment.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.monfluo.wallet.R
import org.monfluo.wallet.model.WalletManager
import org.monfluo.wallet.util.Helper.getClipBoardText

class PasswordBottomSheetDialog(
    private val walletPath: String,
    private val preventGoingBack: Boolean,
    private val listener: Listener,
) : BottomSheetDialogFragment() {

    private lateinit var passwordEditText: EditText
    private lateinit var unlockButton: Button
    private lateinit var pastePasswordImageButton: ImageButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_dialog_password, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        passwordEditText = view.findViewById(R.id.wallet_password_edittext)
        unlockButton = view.findViewById(R.id.unlock_wallet_button)
        pastePasswordImageButton = view.findViewById(R.id.paste_password_imagebutton)

        bindListeners()

        if (preventGoingBack) {
            dialog?.setOnDismissListener {
                requireActivity().finish()
            }
        }
    }

    private fun bindListeners() {
        unlockButton.setOnClickListener {
            onUnlockClicked(passwordEditText.text.toString())
        }
        pastePasswordImageButton.setOnClickListener {
            passwordEditText.setText(getClipBoardText(requireView().context))
        }
    }

    private fun onUnlockClicked(walletPassword: String) {
        if (checkPassword(walletPassword)) {
            listener.onCorrectPasswordSubmitted(this, walletPassword)
        } else {
            Toast.makeText(context, R.string.bad_password, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPassword(walletPassword: String): Boolean {
        return WalletManager.instance.verifyWalletPasswordOnly(
            "$walletPath.keys",
            walletPassword
        )
    }

    interface Listener {
        fun onCorrectPasswordSubmitted(self: PasswordBottomSheetDialog, password: String)
    }
}