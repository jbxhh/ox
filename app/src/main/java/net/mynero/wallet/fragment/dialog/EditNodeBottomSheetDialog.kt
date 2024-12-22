package net.mynero.wallet.fragment.dialog

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
import net.mynero.wallet.R
import net.mynero.wallet.data.Node
import net.mynero.wallet.model.NetworkType
import net.mynero.wallet.util.Helper.getClipBoardText

class EditNodeBottomSheetDialog(val listener: EditNodeListener, val node: Node) : BottomSheetDialogFragment() {

    private lateinit var deleteNodeButton: Button
    private lateinit var doneEditingButton: Button
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
        return inflater.inflate(R.layout.edit_node_bottom_sheet_dialog, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deleteNodeButton = view.findViewById(R.id.delete_node_button)
        doneEditingButton = view.findViewById(R.id.done_editing_button)
        addressEditText = view.findViewById(R.id.address_edittext)
        nodeNameEditText = view.findViewById(R.id.node_name_edittext)
        usernameEditText = view.findViewById(R.id.username_edittext)
        passwordEditText = view.findViewById(R.id.password_edittext)
        trustedDaemonCheckbox = view.findViewById(R.id.trusted_node_checkbox)
        pastePasswordImageButton = view.findViewById(R.id.paste_password_imagebutton)

        nodeNameEditText.setText(node.name)
        addressEditText.setText(node.address)
        node.username?.let {
            usernameEditText.setText(it)
            passwordEditText.visibility = View.VISIBLE
            pastePasswordImageButton.visibility = View.VISIBLE
        }
        node.password.let {
            passwordEditText.setText(it)
        }
        trustedDaemonCheckbox.isChecked = node.trusted

        usernameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                if (editable.toString().isEmpty()) {
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

        deleteNodeButton.setOnClickListener {
            listener.onDeleteNode(this, node)
        }

        doneEditingButton.setOnClickListener {
            val name = nodeNameEditText.text.toString()
            var address = addressEditText.text.toString()
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

            val newNode = Node(name, address, username, password, NetworkType.NetworkType_Mainnet, trusted)
            listener.onEditNode(this, node, newNode)
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

    interface EditNodeListener {
        fun onDeleteNode(self: EditNodeBottomSheetDialog, node: Node)
        fun onEditNode(self: EditNodeBottomSheetDialog, oldNode: Node, newNode: Node)
    }
}