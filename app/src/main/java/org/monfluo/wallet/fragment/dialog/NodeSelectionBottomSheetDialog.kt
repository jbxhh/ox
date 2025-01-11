package org.monfluo.wallet.fragment.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.monfluo.wallet.R
import org.monfluo.wallet.adapter.NodeSelectionAdapter
import org.monfluo.wallet.adapter.NodeSelectionAdapter.NodeSelectionAdapterListener
import org.monfluo.wallet.data.Node

class NodeSelectionBottomSheetDialog(
    private var nodes: List<Node>,
    private var selectedNode: Node,
    private val listener: NodeSelectionDialogListener,
) : BottomSheetDialogFragment(), NodeSelectionAdapterListener {

    private lateinit var addNodeButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NodeSelectionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_dialog_node_selection, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.node_selection_recyclerview)
        addNodeButton = view.findViewById(R.id.add_node_button)
        adapter = NodeSelectionAdapter(nodes, selectedNode, this)

        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter

        bindListeners()

        adapter.setNodes(nodes)
    }

    private fun bindListeners() {
        addNodeButton.setOnClickListener {
            listener.onAddNode(this)
        }
    }

    fun getNodes(): List<Node> = nodes

    fun setNodes(nodes: List<Node>) {
        this.nodes = nodes
        adapter.setNodes(nodes)
    }

    fun setSelectedNode(selectedNode: Node) {
        this.selectedNode = selectedNode
        adapter.setSelectedNode(selectedNode)
    }

    override fun onSelectNode(node: Node) {
        listener.onSelectNode(this, node)
    }

    override fun onEditNode(node: Node) {
        listener.onEditNode(this, node)
    }

    interface NodeSelectionDialogListener {
        fun onSelectNode(self: NodeSelectionBottomSheetDialog, node: Node)
        fun onEditNode(self: NodeSelectionBottomSheetDialog, node: Node)
        fun onAddNode(self: NodeSelectionBottomSheetDialog)
    }
}