package net.mynero.wallet.listener

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import net.mynero.wallet.data.Node
import net.mynero.wallet.fragment.dialog.AddNodeBottomSheetDialog
import net.mynero.wallet.fragment.dialog.EditNodeBottomSheetDialog
import net.mynero.wallet.fragment.dialog.NodeSelectionBottomSheetDialog
import net.mynero.wallet.util.PreferenceUtils
import timber.log.Timber

abstract class NodeSelectionDialogListenerAdapter(
    private val activity: FragmentActivity,
    private var nodes: List<Node>,
) : NodeSelectionBottomSheetDialog.NodeSelectionDialogListener {

    abstract fun trySelectNode(self: NodeSelectionBottomSheetDialog, node: Node): Boolean

    final override fun onSelectNode(self: NodeSelectionBottomSheetDialog, node: Node) {
        if (trySelectNode(self, node)) {
            self.setSelectedNode(node)
        }
    }

    final override fun onEditNode(self: NodeSelectionBottomSheetDialog, node: Node) {
        val listener = object : EditNodeBottomSheetDialog.EditNodeListener {
            override fun onDeleteNode(self: EditNodeBottomSheetDialog, node: Node) {
                val newNodes = nodes.toMutableList()
                newNodes.remove(node)
                PreferenceUtils.setNodes(activity, newNodes).fold(
                    {
                        Toast.makeText(activity, "Node delete", Toast.LENGTH_SHORT).show()
                        self.dismiss()
                    },
                    {
                        Timber.e(it, "Failed to delete node")
                        Toast.makeText(activity, "Failed to delete node", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            override fun onEditNode(
                self: EditNodeBottomSheetDialog,
                oldNode: Node,
                newNode: Node,
            ) {
                val newNodes = nodes.map { node -> if (node != oldNode) node else newNode }
                PreferenceUtils.setNodes(activity, newNodes).fold(
                    {
                        Toast.makeText(activity, "Node saved", Toast.LENGTH_SHORT).show()
                        self.dismiss()
                    },
                    {
                        Timber.e(it, "Failed to save node")
                        Toast.makeText(activity, "Failed to save node", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        val editNodeDialog = EditNodeBottomSheetDialog(listener, node)

        editNodeDialog.show(activity.supportFragmentManager, "edit_node_dialog")
    }

    final override fun onAddNode(self: NodeSelectionBottomSheetDialog) {
        val addNodeDialog = AddNodeBottomSheetDialog(object : AddNodeBottomSheetDialog.AddNodeListener {
            override fun onAddNode(self: AddNodeBottomSheetDialog, node: Node) {
                if (nodes.any { it.name == node.name }) {
                    Timber.i("Failed to add nodes due to node with the same name already existing")
                    Toast.makeText(activity, "Node with this name already exists", Toast.LENGTH_SHORT).show()
                } else {
                    val newNodes = nodes.toMutableList()
                    newNodes.add(node)
                    PreferenceUtils.setNodes(activity, newNodes).fold(
                        {
                            Timber.i("New node ${node.name} added successfully")
                            Toast.makeText(activity, "Node added", Toast.LENGTH_SHORT).show()
                            self.dismiss()
                        },
                        {
                            Timber.e(it, "Failed to add new node")
                            Toast.makeText(activity, "Failed to add node: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        })
        addNodeDialog.show(activity.supportFragmentManager, "add_node_dialog")
    }
}