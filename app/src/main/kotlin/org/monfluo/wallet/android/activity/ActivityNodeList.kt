package org.monfluo.wallet.android.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.adapter.AdapterNode
import org.monfluo.wallet.android.data.DefaultNode
import org.monfluo.wallet.android.data.Node
import org.monfluo.wallet.android.data.SortOrder
import org.monfluo.wallet.android.livedata.combineLiveDatas
import org.monfluo.wallet.android.model.MoneroNetworkType
import org.monfluo.wallet.android.model.Wallet
import org.monfluo.wallet.android.service.wallet.WalletService
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.PreferenceUtils
import org.monfluo.wallet.android.util.component.ActivityMonero
import timber.log.Timber
import kotlin.math.max

class ActivityNodeList : ActivityMonero() {
    private val nodeAdapterListener =
        object : AdapterNode.Listener {
            override fun onSelectNode(
                self: AdapterNode,
                node: Node,
            ) {
                PreferenceUtils.setNode(this@ActivityNodeList, node).fold(
                    {
                        Timber.i("Node ${node.name} selected")
                        val proxy = PreferenceUtils.getProxyIfEnabled(this@ActivityNodeList) ?: ""
                        walletService?.setDaemonAddress(node.address, node.username ?: "", node.password ?: "", node.trusted, proxy)
                        self.setSelectedNode(node)
                    },
                    {
                        Timber.e(it, "Failed to select node ${node.name}")
                        Toast.makeText(this@ActivityNodeList, getText(R.string.toast_failed_to_select_node), Toast.LENGTH_SHORT).show()
                    },
                )
            }

            override fun onEditNode(
                self: AdapterNode,
                node: Node,
            ) {
                startActivity(
                    Intent(this@ActivityNodeList, ActivityNodeDetails::class.java).apply {
                        putExtra(Constants.EXTRA_NODE_NAME, node.name)
                    },
                )
            }
        }

    private val viewModel: ActivityNodeListViewModel by viewModels()

    private lateinit var layoutView: View
    private lateinit var backImageView: ImageView

    private lateinit var addNodeButton: Button
    private lateinit var sortNodesImageButton: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AdapterNode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_node_list)

        layoutView = findViewById(R.id.activity_node_list_layout)
        backImageView = findViewById(R.id.back_imageview)
        addNodeButton = findViewById(R.id.node_add_button)
        sortNodesImageButton = findViewById(R.id.sort_nodes_imagebutton)
        recyclerView = findViewById(R.id.node_selection_recyclerview)
        adapter = AdapterNode(nodeAdapterListener)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        bindWindowInsetsListener()
        bindListeners()
        bindObservers()
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
        sortNodesImageButton.setOnClickListener {
            viewModel.sortOrder.value = viewModel.sortOrder.value!!.reverse()
        }
        addNodeButton.setOnClickListener {
            startActivity(Intent(this, ActivityNodeAdd::class.java))
        }
    }

    private fun bindObservers() {
        viewModel.sortOrder.observe(this) { sortOrder ->
            when (sortOrder!!) {
                SortOrder.ASCENDING -> sortNodesImageButton.setImageResource(R.drawable.sort_down)
                SortOrder.DESCENDING -> sortNodesImageButton.setImageResource(R.drawable.sort_up)
            }
        }
        viewModel.sortedNodes.observe(this) { sortedNodes ->
            adapter.setNodes(sortedNodes)
        }
        viewModel.walletNetworkType.observe(this) { networkType ->
            val node = PreferenceUtils.getDefaultNode(this, networkType)
            viewModel.selectedNode.value = node
        }
        viewModel.selectedNode.observe(this) { selectedNode ->
            adapter.setSelectedNode(selectedNode)
        }
    }

    override fun onResume() {
        super.onResume()

        val nodes = PreferenceUtils.getOrSetDefaultNodes(this, DefaultNode.defaultNodes())
        val sortOrder = SortOrder.fromBoolean(PreferenceUtils.isSortNodesAscending(this))

        viewModel.nodes.value = nodes
        viewModel.sortOrder.value = sortOrder
    }

    // overridden to not finish the activity if wallet service is not active
    override fun onWalletServiceNotFound() {
        Timber.d("Wallet Service not found")
    }

    // overridden to not finish the activity if wallet service gets disconnected
    override fun onWalletServiceDisconnected() {
        Timber.d("Wallet Service disconnected")
    }

    override fun onWalletServiceConnected(walletService: WalletService) {
        walletService.getWallet()?.let { wallet ->
            viewModel.walletNetworkType.value = wallet.networkType()
        }
    }

    override fun onWalletUpdated(wallet: Wallet) {
        viewModel.walletNetworkType.postValue(wallet.networkType())
    }
}

class ActivityNodeListViewModel : ViewModel() {
    val nodes: MutableLiveData<List<Node>> = MutableLiveData(emptyList())
    val selectedNode: MutableLiveData<Node?> = MutableLiveData(null)
    val sortOrder: MutableLiveData<SortOrder> = MutableLiveData(SortOrder.ASCENDING)
    val walletNetworkType: MutableLiveData<MoneroNetworkType> = MutableLiveData(MoneroNetworkType.NetworkType_Mainnet)

    val sortedNodes =
        combineLiveDatas(nodes, sortOrder) { nodes, sortOrder ->
            return@combineLiveDatas when (sortOrder!!) {
                SortOrder.ASCENDING -> nodes!!
                SortOrder.DESCENDING -> nodes!!.reversed()
            }
        }
}
