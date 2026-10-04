package org.monfluo.wallet.android.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
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
import org.monfluo.wallet.android.adapter.NodeListItem
import org.monfluo.wallet.android.data.Node
import org.monfluo.wallet.android.data.NodeStore
import org.monfluo.wallet.android.data.SortOrder
import org.monfluo.wallet.android.service.wallet.WalletService
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.PreferenceUtils
import org.monfluo.wallet.android.util.component.ActivityMonero
import org.monfluo.wallet.lib.data.Network
import timber.log.Timber
import kotlin.math.max

class ActivityNodeList : ActivityMonero() {
    private val nodeAdapterListener =
        object : AdapterNode.Listener {
            override fun onSelectNode(node: Node) {
                NodeStore.select(this@ActivityNodeList, node)
                Timber.i("Node ${node.name} selected")
                if (node.network == walletNetwork()) {
                    walletService?.reconnectDaemon()
                }
                refresh()
            }

            override fun onEditNode(node: Node) {
                startActivity(
                    Intent(this@ActivityNodeList, ActivityNodeDetails::class.java).apply {
                        putExtra(Constants.EXTRA_NODE_ID, node.id)
                    },
                )
            }

            override fun onSelectOffline(network: Network) {
                NodeStore.clearSelection(this@ActivityNodeList, network)
                refresh()
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
            val sortOrder = viewModel.sortOrder.value!!.reverse()
            PreferenceUtils.setSortNodesAscending(this, sortOrder == SortOrder.ASCENDING)
            viewModel.sortOrder.value = sortOrder
            refresh()
        }
        addNodeButton.setOnClickListener {
            startActivity(
                Intent(this, ActivityNodeAdd::class.java).apply {
                    putExtra(Constants.EXTRA_NETWORK, (walletNetwork() ?: Network.Mainnet).name)
                },
            )
        }
    }

    private fun bindObservers() {
        viewModel.sortOrder.observe(this) { sortOrder ->
            when (sortOrder!!) {
                SortOrder.ASCENDING -> sortNodesImageButton.setImageResource(R.drawable.sort_down)
                SortOrder.DESCENDING -> sortNodesImageButton.setImageResource(R.drawable.sort_up)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        viewModel.sortOrder.value = SortOrder.fromBoolean(PreferenceUtils.isSortNodesAscending(this))
        refresh()
    }

    private fun refresh() {
        val selections = NodeStore.selections(this)
        val nodes = viewModel.sortOrder.value!!.sort(NodeStore.nodes(this).sortedBy { it.name.lowercase() })

        val items =
            if (PreferenceUtils.isDeveloperModeEnabled(this)) {
                Network.entries.flatMap { network -> sectionItems(network, selections[network], nodes, withHeader = true) }
            } else {
                val network = walletNetwork() ?: Network.Mainnet
                sectionItems(network, selections[network], nodes, withHeader = false)
            }

        adapter.setItems(items)
    }

    private fun walletNetwork(): Network? = walletService?.getWalletOrLog()?.wallet?.network()

    private fun sectionItems(
        network: Network,
        selectedNode: Node?,
        nodes: List<Node>,
        withHeader: Boolean,
    ): List<NodeListItem> =
        buildList {
            if (withHeader) {
                add(NodeListItem.Header(network))
            }
            add(NodeListItem.Offline(network, selectedNode == null))
            nodes.filter { it.network == network }.forEach { node ->
                add(NodeListItem.Entry(node, node.id == selectedNode?.id))
            }
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
        refresh()
    }
}

class ActivityNodeListViewModel : ViewModel() {
    val sortOrder: MutableLiveData<SortOrder> = MutableLiveData(SortOrder.ASCENDING)
}
