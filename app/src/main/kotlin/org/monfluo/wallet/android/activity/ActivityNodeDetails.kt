package org.monfluo.wallet.android.activity

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.data.DefaultNode
import org.monfluo.wallet.android.data.Node
import org.monfluo.wallet.android.model.MoneroNetworkType
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.PreferenceUtils
import org.monfluo.wallet.android.util.component.ActivityBase
import timber.log.Timber
import kotlin.math.max

class ActivityNodeDetails : ActivityBase() {
    private val viewModel: ActivityNodeDetailsViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var nodeNameEditText: EditText
    private lateinit var nodeAddressEditText: EditText
    private lateinit var nodeDaemonTrustedCheckBox: CheckBox
    private lateinit var nodeNetworkTypeButton: Button
    private lateinit var nodeNetworkTypeLabelTextView: TextView
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
        nodeDaemonTrustedCheckBox = findViewById(R.id.trusted_node_checkbox)
        nodeNetworkTypeButton = findViewById(R.id.node_network_type_button)
        nodeNetworkTypeLabelTextView = findViewById(R.id.node_network_type_label_textview)
        nodeUsernameEditText = findViewById(R.id.node_username_edittext)
        nodePasswordEditText = findViewById(R.id.node_password_edittext)
        nodeDeleteButton = findViewById(R.id.delete_node_button)
        nodeSaveButton = findViewById(R.id.save_node_button)

        bindWindowInsetsListener()
        bindListeners()
        bindObservers()
        init()
    }

    override fun onResume() {
        super.onResume()

        val nodeName = viewModel.nodeName!!
        val node = PreferenceUtils.getNodeByName(this, nodeName).getOrNull()

        if (node == null) {
            Timber.e("Node $nodeName is not found")
            finish()
            return
        }

        if (nodeNameEditText.text.isEmpty()) {
            nodeNameEditText.setText(node.name)
        }
        if (nodeAddressEditText.text.isEmpty()) {
            nodeAddressEditText.setText(node.address)
        }
        if (!viewModel.nodeDaemonTrusedClicked) {
            nodeDaemonTrustedCheckBox.isChecked = node.trusted
        }
        if (!viewModel.nodeNetworkTypeClicked) {
            viewModel.networkType.value = node.networkType
        }
        if (nodeUsernameEditText.text.isEmpty()) {
            nodeUsernameEditText.setText(node.username ?: "")
        }
        if (nodePasswordEditText.text.isEmpty()) {
            nodePasswordEditText.setText(node.password ?: "")
        }

        val isDeveloperModeEnabled = PreferenceUtils.isDeveloperModeEnabled(applicationContext)
        viewModel.developerModeEnabled.value = isDeveloperModeEnabled
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
        nodeNetworkTypeButton.setOnClickListener {
            viewModel.nodeNetworkTypeClicked = true
            toggleNetworkType()
        }
        nodeDaemonTrustedCheckBox.setOnClickListener {
            viewModel.nodeDaemonTrusedClicked = true
        }
        nodeDeleteButton.setOnClickListener {
            val nodeName = viewModel.nodeName!!

            AlertDialog
                .Builder(this)
                .setTitle(getString(R.string.dialog_delete_node_title))
                .setMessage(getString(R.string.dialog_delete_node_message))
                .setPositiveButton(getString(R.string.dialog_delete_node_action_yes)) { _, _ ->
                    val newNodes = PreferenceUtils.getOrSetDefaultNodes(this, DefaultNode.defaultNodes()).toMutableList()
                    newNodes.removeIf { node ->
                        node.name == nodeName
                    }
                    PreferenceUtils.setNodes(this, newNodes).fold(
                        {
                            Toast.makeText(this, getText(R.string.toast_node_deleted), Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        {
                            Timber.e(it, "Failed to delete node")
                            Toast.makeText(this, getText(R.string.toast_failed_to_delete_node), Toast.LENGTH_SHORT).show()
                        },
                    )
                }.setNegativeButton(getString(R.string.dialog_delete_node_action_no), null)
                .create()
                .show()
        }
        nodeSaveButton.setOnClickListener {
            val nodeName = viewModel.nodeName!!

            val name = nodeNameEditText.text.toString()
            val address = nodeAddressEditText.text.toString()
            val username = nodeUsernameEditText.text.toString().ifBlank { null }
            val password = nodePasswordEditText.text.toString().ifBlank { null }
            val trusted = nodeDaemonTrustedCheckBox.isChecked
            val networkType = viewModel.networkType.value!!

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

            val newNode = Node(name, address, username, password, networkType, trusted)

            val newNodes = PreferenceUtils.getOrSetDefaultNodes(this, DefaultNode.defaultNodes()).toMutableList()
            val nodeIndex = newNodes.indexOfFirst { node -> node.name == nodeName }
            if (nodeIndex == -1) {
                Timber.e("Failed to save node because node with name $nodeName is not found")
                Toast.makeText(this, getText(R.string.toast_failed_to_save_node), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            newNodes[nodeIndex] = newNode

            PreferenceUtils.setNodes(this, newNodes).fold(
                {
                    Toast.makeText(this, getText(R.string.toast_node_saved), Toast.LENGTH_SHORT).show()
                    finish()
                },
                {
                    Timber.e(it, "Failed to save node")
                    Toast.makeText(this, getText(R.string.toast_failed_to_save_node), Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    private fun bindObservers() {
        viewModel.networkType.observe(this) { networkType ->
            nodeNetworkTypeButton.text = getString(networkType.resourceId)
        }

        viewModel.developerModeEnabled.observe(this) { isDeveloperModeEnabled ->
            if (isDeveloperModeEnabled) {
                nodeNetworkTypeButton.visibility = View.VISIBLE
                nodeNetworkTypeLabelTextView.visibility = View.VISIBLE
            } else {
                nodeNetworkTypeButton.visibility = View.GONE
                nodeNetworkTypeLabelTextView.visibility = View.GONE
            }
        }
    }

    private fun init() {
        if (viewModel.nodeName == null) {
            val extras = intent.extras
            if (extras != null && extras.containsKey(Constants.EXTRA_NODE_NAME)) {
                viewModel.nodeName = extras.getString(Constants.EXTRA_NODE_NAME)
            } else {
                Timber.e("Node name is null in the view model and it was not present in extras")
                finish()
            }
        }
    }

    fun toggleNetworkType() {
        viewModel.networkType.value = viewModel.networkType.value!!.next()
    }
}

internal class ActivityNodeDetailsViewModel : ViewModel() {
    var nodeName: String? = null
    var nodeDaemonTrusedClicked: Boolean = false
    var nodeNetworkTypeClicked: Boolean = false
    val developerModeEnabled: MutableLiveData<Boolean> = MutableLiveData(false)
    val networkType: MutableLiveData<MoneroNetworkType> = MutableLiveData(MoneroNetworkType.NetworkType_Mainnet)
}
