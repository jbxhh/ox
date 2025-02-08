package org.monfluo.wallet.fragment.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.monfluo.wallet.R

class EditWalletBottomSheetDialog(
    private val walletName: String,
    private val listener: EditWalletListener,
) : BottomSheetDialogFragment() {

    private lateinit var walletNameEditText: EditText
    private lateinit var deleteButton: Button
    private lateinit var doneButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_dialog_edit_wallet, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        walletNameEditText = view.findViewById(R.id.wallet_name_edittext)
        doneButton = view.findViewById(R.id.done_editing_button)
        deleteButton = view.findViewById(R.id.delete_node_button)

        walletNameEditText.setText(walletName)

        deleteButton.setOnClickListener {
            listener.onDeleteWallet(this)
        }

        doneButton.setOnClickListener {
            val newWalletName = walletNameEditText.text.toString()

            listener.onDone(this, newWalletName)
        }
    }

    interface EditWalletListener {
        fun onDeleteWallet(self: EditWalletBottomSheetDialog)
        fun onDone(self: EditWalletBottomSheetDialog, newWalletName: String)
    }
}