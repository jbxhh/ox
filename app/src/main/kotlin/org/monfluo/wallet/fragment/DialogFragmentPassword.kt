package org.monfluo.wallet.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.monfluo.wallet.R
import org.monfluo.wallet.model.WalletManager
import org.monfluo.wallet.util.Helper.getClipBoardText

class DialogFragmentPassword(
    private val walletPath: String,
    private val preventGoingBack: Boolean,
    private val listener: Listener,
) : BottomSheetDialogFragment() {

    private lateinit var passwordEditText: EditText
    private lateinit var unlockButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_fragment_password, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        passwordEditText = view.findViewById(R.id.wallet_password_edittext)
        unlockButton = view.findViewById(R.id.unlock_wallet_button)

        bindListeners()
        showKeyboard()

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
    }

    private fun showKeyboard() {
        passwordEditText.requestFocus()
        getDialog()?.getWindow()?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private fun onUnlockClicked(walletPassword: String) {
        if (checkPassword(walletPassword)) {
            listener.onCorrectPasswordSubmitted(this, walletPassword)
        } else {
            Toast.makeText(context, R.string.toast_bad_password, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPassword(walletPassword: String, noSpendKey: Boolean = false): Boolean {
        return WalletManager.instance.verifyWalletPassword(
            "$walletPath.keys",
            walletPassword,
            noSpendKey
        )
    }

    interface Listener {
        fun onCorrectPasswordSubmitted(self: DialogFragmentPassword, password: String)
    }
}
