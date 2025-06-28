package org.monfluo.wallet.fragment.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.monfluo.wallet.R
import org.monfluo.wallet.adapter.NodeSelectionAdapter
import org.monfluo.wallet.adapter.NodeSelectionAdapter.NodeSelectionAdapterListener
import org.monfluo.wallet.data.Node
import org.monfluo.wallet.data.SortOrder

class NodeSelectionBottomSheetDialog(
    private var sortOrder: SortOrder,
    private var nodes: List<Node>,
    private var selectedNode: Node,
    private val listener: NodeSelectionDialogListener,
) : BottomSheetDialogFragment(), NodeSelectionAdapterListener {

    private lateinit var addNodeButton: Button
    private lateinit var sortNodesImageButton: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NodeSelectionAdapter

    private var sortedNodes = sortOrder.sort(nodes)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_dialog_node_selection, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addNodeButton = view.findViewById(R.id.add_node_button)
        sortNodesImageButton = view.findViewById(R.id.sort_nodes_imagebutton)
        recyclerView = view.findViewById(R.id.node_selection_recyclerview)
        adapter = NodeSelectionAdapter(sortedNodes, selectedNode, this)

        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter

        bindListeners()

        updateSortImage()
    }

    private fun bindListeners() {
        sortNodesImageButton.setOnClickListener {
            listener.onSortClicked(this, sortOrder)
        }
        addNodeButton.setOnClickListener {
            listener.onAddNode(this)
        }
    }

    private fun updateSortImage() {
        when (sortOrder) {
            SortOrder.ASCENDING -> sortNodesImageButton.setImageResource(R.drawable.sort_down)
            SortOrder.DESCENDING -> sortNodesImageButton.setImageResource(R.drawable.sort_up)
        }
    }

    fun setNodes(newNodes: List<Node>) {
        nodes = newNodes
        sortedNodes = sortOrder.sort(newNodes)
        adapter.setNodes(sortedNodes)
    }

    fun setSelectedNode(newSelectedNode: Node) {
        selectedNode = newSelectedNode
        adapter.setSelectedNode(newSelectedNode)
    }

    fun setSortOrder(newSortOrder: SortOrder) {
        sortOrder = newSortOrder
        sortedNodes = sortOrder.sort(nodes)
        adapter.setNodes(sortedNodes)
        updateSortImage()
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
        fun onSortClicked(self: NodeSelectionBottomSheetDialog, sortOrder: SortOrder)
    }
}