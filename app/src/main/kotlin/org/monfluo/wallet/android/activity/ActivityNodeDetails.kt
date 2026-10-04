package org.monfluo.wallet.android.activity

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.data.Credentials
import org.monfluo.wallet.android.data.Node
import org.monfluo.wallet.android.data.NodeStore
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.component.ActivityBase
import org.monfluo.wallet.lib.data.Network
import timber.log.Timber
import kotlin.math.max

class ActivityNodeDetails : ActivityBase() {
    private val viewModel: ActivityNodeDetailsViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var nodeNameEditText: EditText
    private lateinit var nodeAddressEditText: EditText
    private lateinit var nodeUsernameEditText: EditText
    private lateinit var nodePasswordEditText: EditText
    private lateinit var nodeDeleteButton: Button
    private lateinit var nodeSaveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_node_details)

        layoutView = findViewById(R.id.activity_node_details_layout)
        backImageView = findViewById(R.id.back_imageview)
        nodeNameEditText = findViewById(R.id.node_name_edittext)
        nodeAddressEditText = findViewById(R.id.node_address_edittext)
        nodeUsernameEditText = findViewById(R.id.node_username_edittext)
        nodePasswordEditText = findViewById(R.id.node_password_edittext)
        nodeDeleteButton = findViewById(R.id.delete_node_button)
        nodeSaveButton = findViewById(R.id.save_node_button)

        bindWindowInsetsListener()
        bindListeners()
        init()
    }

    override fun onResume() {
        super.onResume()

        val nodeId = viewModel.nodeId!!
        val node = NodeStore.nodes(this).find { it.id == nodeId }

        if (node == null) {
            Timber.e("Node $nodeId is not found")
            finish()
            return
        }

        if (nodeNameEditText.text.isEmpty()) {
            nodeNameEditText.setText(node.name)
        }
        if (nodeAddressEditText.text.isEmpty()) {
            nodeAddressEditText.setText(node.address)
        }
        viewModel.networkType = node.network
        if (nodeUsernameEditText.text.isEmpty()) {
            nodeUsernameEditText.setText(node.credentials?.username ?: "")
        }
        if (nodePasswordEditText.text.isEmpty()) {
            nodePasswordEditText.setText(node.credentials?.password ?: "")
        }
    }

    private fun bindWindowInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(layoutView) { _, insetsCompat ->
            val insets = insetsCompat.getInsets(systemBars() or displayCutout())
            layoutView.updatePadding(
                left = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.left),
                top = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.top),
                right = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.right),
                bottom = max(resources.getDimensionPixelOffset(R.dimen.padding_big), insets.bottom),
            )
            return@setOnApplyWindowInsetsListener insetsCompat
        }
    }

    private fun bindListeners() {
        backImageView.setOnClickListener {
            finish()
        }
        nodeDeleteButton.setOnClickListener {
            val nodeId = viewModel.nodeId!!

            AlertDialog
                .Builder(this)
                .setTitle(getString(R.string.dialog_delete_node_title))
                .setMessage(getString(R.string.dialog_delete_node_message))
                .setPositiveButton(getString(R.string.dialog_delete_node_action_yes)) { _, _ ->
                    NodeStore.delete(this, nodeId)
                    Toast.makeText(this, getText(R.string.toast_node_deleted), Toast.LENGTH_SHORT).show()
                    finish()
                }.setNegativeButton(getString(R.string.dialog_delete_node_action_no), null)
                .create()
                .show()
        }
        nodeSaveButton.setOnClickListener {
            val nodeId = viewModel.nodeId!!

            val name = nodeNameEditText.text.toString().trim()
            val address = nodeAddressEditText.text.toString().trim()
            val username = nodeUsernameEditText.text.toString().trim()
            val password = nodePasswordEditText.text.toString()
            val networkType = viewModel.networkType!!

            if (name.isBlank()) {
                Toast.makeText(this, getText(R.string.toast_enter_node_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (address.isBlank()) {
                Toast.makeText(this, getText(R.string.toast_enter_node_address), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (username.isNotBlank() != password.isNotBlank()) {
                Toast.makeText(this, getText(R.string.toast_enter_node_password), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val credentials = if (username.isNotBlank()) Credentials(username, password) else null

            NodeStore.update(this, Node(nodeId, name, address, credentials, networkType))
            Toast.makeText(this, getText(R.string.toast_node_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun init() {
        if (viewModel.nodeId == null) {
            val extras = intent.extras
            if (extras != null && extras.containsKey(Constants.EXTRA_NODE_ID)) {
                viewModel.nodeId = extras.getString(Constants.EXTRA_NODE_ID)
            } else {
                Timber.e("Node id is null in the view model and it was not present in extras")
                finish()
            }
        }
    }
}

internal class ActivityNodeDetailsViewModel : ViewModel() {
    var nodeId: String? = null
    var networkType: Network? = null
}
