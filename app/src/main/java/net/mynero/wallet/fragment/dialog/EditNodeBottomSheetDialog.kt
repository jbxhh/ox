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
import net.mynero.wallet.data.Node.Companion.fromJson
import net.mynero.wallet.util.Helper.getClipBoardText
import org.json.JSONException
import org.json.JSONObject

class EditNodeBottomSheetDialog : BottomSheetDialogFragment() {
    var listener: EditNodeListener? = null
    var node: Node? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.edit_node_bottom_sheet_dialog, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val deleteNodeButton = view.findViewById<Button>(R.id.delete_node_button)
        val doneEditingButton = view.findViewById<Button>(R.id.done_editing_button)
        val addressEditText = view.findViewById<EditText>(R.id.address_edittext)
        val portEditText = view.findViewById<EditText>(R.id.node_port_edittext)
        val nodeNameEditText = view.findViewById<EditText>(R.id.node_name_edittext)
        val usernameEditText = view.findViewById<EditText>(R.id.username_edittext)
        val passwordEditText = view.findViewById<EditText>(R.id.password_edittext)
        val trustedDaemonCheckBox = view.findViewById<CheckBox>(R.id.trusted_node_checkbox)
        val pastePasswordImageButton =
            view.findViewById<ImageButton>(R.id.paste_password_imagebutton)
        if (node == null) return
        addressEditText.setText(node?.host)
        portEditText.setText("${node?.rpcPort}")
        nodeNameEditText.setText(node?.name)
        usernameEditText.setText(node?.username)
        trustedDaemonCheckBox.isChecked = node?.trusted ?: false
        if (node?.password?.isNotEmpty() == true) {
            passwordEditText.setText(node?.password)
            passwordEditText.visibility = View.VISIBLE
            pastePasswordImageButton.visibility = View.VISIBLE
        }
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
        deleteNodeButton.setOnClickListener {
            listener?.onNodeDeleted(node)
            dismiss()
        }
        doneEditingButton.setOnClickListener {
            var nodeAddr = addressEditText.text.toString()
            val portString = portEditText.text.toString()
            val nodeName = nodeNameEditText.text.toString()
            val user = usernameEditText.text.toString()
            val pass = passwordEditText.text.toString()
            val trusted = trustedDaemonCheckBox.isChecked
            if (nodeName.isEmpty()) {
                Toast.makeText(context, "Enter node name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (nodeAddr.isEmpty() || portString.isEmpty()) {
                Toast.makeText(context, "Enter node address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (user.isNotEmpty() && pass.isEmpty()) {
                Toast.makeText(context, "Enter password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val jsonObject = JSONObject()
            try {
                if (user.isNotEmpty()) {
                    jsonObject.put("username", user)
                    jsonObject.put("password", pass)
                }
                if (nodeAddr.contains(":") && !nodeAddr.startsWith("[") && !nodeAddr.endsWith("]"))
                    nodeAddr = "[$nodeAddr]"

                jsonObject.put("host", nodeAddr)
                jsonObject.put("rpcPort", portString.toInt())
                jsonObject.put("network", "mainnet")
                jsonObject.put("name", nodeName)
                jsonObject.put("trusted", trusted)
                listener?.onNodeEdited(node, fromJson(jsonObject))
                dismiss()
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
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
        fun onNodeDeleted(node: Node?)
        fun onNodeEdited(oldNode: Node?, newNode: Node?)
    }
}