package org.monfluo.wallet.activity

import android.view.View
import android.widget.ImageView
import android.widget.EditText
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import org.monfluo.wallet.util.component.ActivityLogging
import org.monfluo.wallet.util.Helper
import org.monfluo.wallet.util.PreferenceUtils
import org.monfluo.wallet.R
import org.monfluo.wallet.data.Node
import org.monfluo.wallet.data.DefaultNode
import org.monfluo.wallet.model.MoneroNetworkType
import kotlin.math.max
import timber.log.Timber

class ActivityNodeAdd() : ActivityLogging() {

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var nodeNameEditText: EditText
    private lateinit var nodeAddressEditText: EditText
    private lateinit var nodeDaemonTrustedCheckBox: CheckBox
    private lateinit var nodeUsernameEditText: EditText
    private lateinit var nodePasswordEditText: EditText
    private lateinit var nodeAddButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_node_add)

        layoutView = findViewById(R.id.activity_node_add_layout)
        backImageView = findViewById(R.id.back_imageview)
        nodeNameEditText = findViewById(R.id.node_name_edittext)
        nodeAddressEditText = findViewById(R.id.node_address_edittext)
        nodeDaemonTrustedCheckBox = findViewById(R.id.trusted_node_checkbox)
        nodeUsernameEditText = findViewById(R.id.node_username_edittext)
        nodePasswordEditText = findViewById(R.id.node_password_edittext)
        nodeAddButton = findViewById(R.id.node_add_button)

        bindWindowInsetsListener()
        bindListeners()
    }

    private fun bindWindowInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(layoutView) { _, insetsCompat ->
            val insets = insetsCompat.getInsets(systemBars() or displayCutout())
            layoutView.updatePadding(
                left = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.left),
                top = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.top),
                right = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.right),
                bottom = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.bottom)
            )
            return@setOnApplyWindowInsetsListener insetsCompat
        }
    }

    private fun bindListeners() {
        backImageView.setOnClickListener {
            finish()
        }
        nodeAddButton.setOnClickListener {
            val name = nodeNameEditText.text.toString()
            val address = nodeAddressEditText.text.toString()
            val username = nodeUsernameEditText.text.toString().ifBlank { null }
            val password = nodePasswordEditText.text.toString().ifBlank { null }
            val trusted = nodeDaemonTrustedCheckBox.isChecked

            if (name.isBlank()) {
                Toast.makeText(this, getText(R.string.toast_enter_node_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (address.isBlank()) {
                Toast.makeText(this, getText(R.string.toast_enter_node_address), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (username != null && password == null) {
                Toast.makeText(this, getText(R.string.toast_enter_node_password), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newNode = Node(name, address, username, password, MoneroNetworkType.NetworkType_Mainnet, trusted)

            val nodes = PreferenceUtils.getOrSetDefaultNodes(this, DefaultNode.defaultNodes())
            if (nodes.any { it.name == newNode.name }) {
                Timber.i("Failed to add nodes due to node with the same name already existing")
                Toast.makeText(this, getText(R.string.toast_node_with_name_already_exists), Toast.LENGTH_SHORT).show()
            } else {
                val newNodes = nodes.toMutableList()
                newNodes.add(newNode)
                PreferenceUtils.setNodes(this, newNodes).fold(
                    {
                        Timber.i("New node ${newNode.name} added successfully")
                        Toast.makeText(this, getText(R.string.toast_node_added), Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    {
                        Timber.e(it, "Failed to add new node")
                        Toast.makeText(this, getText(R.string.toast_failed_to_add_node), Toast.LENGTH_SHORT).show()
                    }
                )
            }

        }
    }
}
