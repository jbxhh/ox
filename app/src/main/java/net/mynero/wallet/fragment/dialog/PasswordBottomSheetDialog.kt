package net.mynero.wallet.fragment.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.mynero.wallet.R
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.Helper.getClipBoardText
import java.io.File

class PasswordBottomSheetDialog : BottomSheetDialogFragment() {
    @JvmField
    var listener: PasswordListener? = null
    @JvmField
    var cancelable = false
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.password_bottom_sheet_dialog, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = cancelable
        val walletFile = File(activity?.applicationInfo?.dataDir, Constants.WALLET_NAME)
        val pastePasswordImageButton =
            view.findViewById<ImageButton>(R.id.paste_password_imagebutton)
        val passwordEditText = view.findViewById<EditText>(R.id.wallet_password_edittext)
        val unlockWalletButton = view.findViewById<Button>(R.id.unlock_wallet_button)
        pastePasswordImageButton.setOnClickListener {
            passwordEditText.setText(
                getClipBoardText(view.context)
            )
        }
        unlockWalletButton.setOnClickListener {
            val password = passwordEditText.text.toString()
            val success = checkPassword(walletFile, password)
            if (success) {
                listener?.onPasswordSuccess(password)
                dismiss()
            } else {
                listener?.onPasswordFail()
            }
        }
    }

    private fun checkPassword(walletFile: File, password: String): Boolean {
        return WalletManager.instance?.verifyWalletPasswordOnly(
            walletFile.absolutePath + ".keys",
            password
        ) == true
    }

    interface PasswordListener {
        fun onPasswordSuccess(password: String)
        fun onPasswordFail()
    }
}