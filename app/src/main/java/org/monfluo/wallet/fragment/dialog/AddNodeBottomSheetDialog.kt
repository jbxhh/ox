package org.monfluo.wallet.fragment.dialog

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.monfluo.wallet.R
import org.monfluo.wallet.data.Node
import org.monfluo.wallet.model.NetworkType
import org.monfluo.wallet.util.Helper.getClipBoardText

class AddNodeBottomSheetDialog(val listener: AddNodeListener) : BottomSheetDialogFragment() {

    private lateinit var addNodeButton: Button
    private lateinit var addressEditText: EditText
    private lateinit var nodeNameEditText: EditText
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var trustedDaemonCheckbox: CheckBox
    private lateinit var pastePasswordImageButton: ImageButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_dialog_add_node, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addNodeButton = view.findViewById(R.id.add_node_button)
        addressEditText = view.findViewById(R.id.address_edittext)
        nodeNameEditText = view.findViewById(R.id.node_name_edittext)
        usernameEditText = view.findViewById(R.id.username_edittext)
        passwordEditText = view.findViewById(R.id.password_edittext)
        trustedDaemonCheckbox = view.findViewById(R.id.trusted_node_checkbox)
        pastePasswordImageButton = view.findViewById(R.id.paste_password_imagebutton)

        bindListeners(view)
    }

    private fun bindListeners(view: View) {
        usernameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                if (editable.toString().isEmpty()) {
                    passwordEditText.text = null
                    passwordEditText.visibility = View.GONE
                    pastePasswordImageButton.visibility = View.GONE
                } else {
                    passwordEditText.visibility = View.VISIBLE
                    pastePasswordImageButton.visibility = View.VISIBLE
                }
            }
        })

        addPasteListener(view, addressEditText, R.id.paste_address_imagebutton)
        addPasteListener(view, usernameEditText, R.id.paste_username_imagebutton)
        addPasteListener(view, passwordEditText, R.id.paste_password_imagebutton)

        addNodeButton.setOnClickListener {
            val name = nodeNameEditText.text.toString()
            val address = addressEditText.text.toString()
            val username = usernameEditText.text.toString().ifBlank { null }
            val password = passwordEditText.text.toString().ifBlank { null }
            val trusted = trustedDaemonCheckbox.isChecked

            if (name.isBlank()) {
                Toast.makeText(context, "Enter node name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (address.isBlank()) {
                Toast.makeText(context, "Enter node address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (username != null && password == null) {
                Toast.makeText(context, "Enter password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val node = Node(name, address, username, password, NetworkType.NetworkType_Mainnet, trusted)
            listener.onAddNode(this, node)
        }
    }

    private fun addPasteListener(root: View, editText: EditText, layoutId: Int) {
        val pasteImageButton = root.findViewById<ImageButton>(layoutId)
        pasteImageButton.setOnClickListener {
            val ctx = context
            if (ctx != null) {
                editText.setText(getClipBoardText(ctx))
            }
        }
    }

    interface AddNodeListener {
        fun onAddNode(self: AddNodeBottomSheetDialog, node: Node)
    }
}