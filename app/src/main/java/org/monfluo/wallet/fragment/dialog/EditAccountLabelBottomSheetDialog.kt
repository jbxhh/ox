package org.monfluo.wallet.fragment.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.monfluo.wallet.R
import org.monfluo.wallet.util.Helper.getClipBoardText

class EditAccountLabelBottomSheetDialog(
    private val currentLabel: String,
    private val onSave: (String) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var pasteButton: ImageButton
    private lateinit var labelEditText: EditText
    private lateinit var saveLabelButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_dialog_edit_account_label, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pasteButton = view.findViewById(R.id.set_account_label_imagebutton)
        labelEditText = view.findViewById(R.id.set_account_label_edittext)
        saveLabelButton = view.findViewById(R.id.set_account_label_button)

        labelEditText.setText(currentLabel)
        pasteButton.setOnClickListener {
            labelEditText.setText(getClipBoardText(view.context))
        }
        saveLabelButton.setOnClickListener {
            val label = labelEditText.text.toString()
            onSave(label)
            dismiss()
        }
    }
}
