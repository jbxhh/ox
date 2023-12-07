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
import net.mynero.wallet.data.Node.Companion.fromJson
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.Helper.getClipBoardText
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class AddNodeBottomSheetDialog : BottomSheetDialogFragment() {
    var listener: AddNodeListener? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.add_node_bottom_sheet_dialog, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val addNodeButton = view.findViewById<Button>(R.id.add_node_button)
        val addressEditText = view.findViewById<EditText>(R.id.address_edittext)
        val portEditText = view.findViewById<EditText>(R.id.node_port_edittext)
        val nodeNameEditText = view.findViewById<EditText>(R.id.node_name_edittext)
        val usernameEditText = view.findViewById<EditText>(R.id.username_edittext)
        val passwordEditText = view.findViewById<EditText>(R.id.password_edittext)
        val trustedDaemonCheckbox = view.findViewById<CheckBox>(R.id.trusted_node_checkbox)

        val pastePasswordImageButton =
            view.findViewById<ImageButton>(R.id.paste_password_imagebutton)
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
            var nodeAddr = addressEditText.text.toString()
            val portString = portEditText.text.toString()
            val name = nodeNameEditText.text.toString()
            val user = usernameEditText.text.toString()
            val pass = passwordEditText.text.toString()
            val trusted = trustedDaemonCheckbox.isChecked

            if (name.isEmpty()) {
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
                if (nodeAddr.contains(":") && !nodeAddr.startsWith("[") && !nodeAddr.endsWith("]")) nodeAddr =
                    "[$nodeAddr]"
                jsonObject.put("host", nodeAddr)
                jsonObject.put("rpcPort", portString.toInt())
                jsonObject.put("network", "mainnet")
                jsonObject.put("name", name)
                jsonObject.put("trusted", trusted)
                addNodeToSaved(jsonObject)
                if (listener != null) {
                    listener?.onNodeAdded()
                    dismiss()
                }
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
        }
    }

    @Throws(JSONException::class)
    private fun addNodeToSaved(newNodeJsonObject: JSONObject) {
        val newNode = fromJson(newNodeJsonObject)
        val nodesArray = PrefService.instance?.getString(Constants.PREF_CUSTOM_NODES, "[]")
        val jsonArray = JSONArray(nodesArray)
        var exists = false
        for (i in 0 until jsonArray.length()) {
            val nodeJsonObject = jsonArray.getJSONObject(i)
            val node = fromJson(nodeJsonObject)
            if (node?.toNodeString() == newNode?.toNodeString()) exists = true
        }
        if (!exists) {
            jsonArray.put(newNodeJsonObject)
        } else {
            Toast.makeText(context, "Node already exists", Toast.LENGTH_SHORT).show()
            return
        }
        PrefService.instance?.edit()?.putString(Constants.PREF_CUSTOM_NODES, jsonArray.toString())
            ?.apply()
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
        fun onNodeAdded()
    }
}