package org.monfluo.wallet.android.activity

import android.os.Bundle
import android.view.View
import android.widget.Button
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
import org.monfluo.wallet.android.data.Credentials
import org.monfluo.wallet.android.data.NodeStore
import org.monfluo.wallet.android.data.next
import org.monfluo.wallet.android.data.resourceId
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.PreferenceUtils
import org.monfluo.wallet.android.util.component.ActivityBase
import org.monfluo.wallet.lib.data.Network
import timber.log.Timber
import kotlin.math.max

class ActivityNodeAdd : ActivityBase() {
    private val viewModel: ActivityNodeAddViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var nodeNameEditText: EditText
    private lateinit var nodeAddressEditText: EditText
    private lateinit var nodeNetworkTypeButton: Button
    private lateinit var nodeNetworkTypeLabelTextView: TextView
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
        nodeNetworkTypeButton = findViewById(R.id.node_network_type_button)
        nodeNetworkTypeLabelTextView = findViewById(R.id.node_network_type_label_textview)
        nodeUsernameEditText = findViewById(R.id.node_username_edittext)
        nodePasswordEditText = findViewById(R.id.node_password_edittext)
        nodeAddButton = findViewById(R.id.node_add_button)

        bindWindowInsetsListener()
        bindListeners()
        bindObservers()
        init()
    }

    override fun onResume() {
        super.onResume()

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
        nodeNetworkTypeButton.setOnClickListener { toggleNetworkType() }
        nodeAddButton.setOnClickListener {
            val name = nodeNameEditText.text.toString().trim()
            val address = nodeAddressEditText.text.toString().trim()
            val username = nodeUsernameEditText.text.toString().trim()
            val password = nodePasswordEditText.text.toString()
            val networkType = viewModel.networkType.value!!

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

            NodeStore.add(this, name, address, credentials, networkType)
            Timber.i("New node $name added successfully")
            Toast.makeText(this, getText(R.string.toast_node_added), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun bindObservers() {
        viewModel.networkType.observe(this) { networkType ->
            nodeNetworkTypeButton.text = getString(networkType.resourceId())
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
        if (viewModel.networkTypeInitialized) {
            return
        }
        viewModel.networkTypeInitialized = true

        val networkName = intent.getStringExtra(Constants.EXTRA_NETWORK) ?: return
        Network.entries.find { it.name == networkName }?.let { viewModel.networkType.value = it }
    }

    fun toggleNetworkType() {
        viewModel.networkType.value = viewModel.networkType.value!!.next()
    }
}

internal class ActivityNodeAddViewModel : ViewModel() {
    var networkTypeInitialized: Boolean = false
    val developerModeEnabled: MutableLiveData<Boolean> = MutableLiveData(false)
    val networkType: MutableLiveData<Network> = MutableLiveData(Network.Mainnet)
}
