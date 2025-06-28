package org.monfluo.wallet.listener

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import org.monfluo.wallet.R
import org.monfluo.wallet.data.DefaultNode
import org.monfluo.wallet.data.Node
import org.monfluo.wallet.data.SortOrder
import org.monfluo.wallet.fragment.dialog.AddNodeBottomSheetDialog
import org.monfluo.wallet.fragment.dialog.EditNodeBottomSheetDialog
import org.monfluo.wallet.fragment.dialog.NodeSelectionBottomSheetDialog
import org.monfluo.wallet.util.PreferenceUtils
import timber.log.Timber

abstract class NodeSelectionDialogListenerAdapter(
    private val activity: FragmentActivity,
) : NodeSelectionBottomSheetDialog.NodeSelectionDialogListener {

    abstract fun trySelectNode(self: NodeSelectionBottomSheetDialog, node: Node): Boolean

    final override fun onSelectNode(self: NodeSelectionBottomSheetDialog, node: Node) {
        if (trySelectNode(self, node)) {
            self.setSelectedNode(node)
        }
    }

    final override fun onEditNode(self: NodeSelectionBottomSheetDialog, node: Node) {
        val nodeSelectionBottomSheetDialog = self
        val listener = object : EditNodeBottomSheetDialog.EditNodeListener {
            override fun onDeleteNode(self: EditNodeBottomSheetDialog, node: Node) {
                val newNodes = PreferenceUtils.getOrSetDefaultNodes(activity, DefaultNode.defaultNodes()).toMutableList()
                newNodes.remove(node)
                PreferenceUtils.setNodes(activity, newNodes).fold(
                    {
                        Toast.makeText(activity, activity.getText(R.string.toast_node_deleted), Toast.LENGTH_SHORT).show()
                        nodeSelectionBottomSheetDialog.setNodes(newNodes)
                        self.dismiss()
                    },
                    {
                        Timber.e(it, "Failed to delete node")
                        Toast.makeText(activity, activity.getText(R.string.toast_failed_to_delete_node), Toast.LENGTH_SHORT).show()
                    }
                )
            }

            override fun onEditNode(
                self: EditNodeBottomSheetDialog,
                oldNode: Node,
                newNode: Node,
            ) {
                val newNodes = PreferenceUtils.getOrSetDefaultNodes(activity, DefaultNode.defaultNodes())
                    .map { node -> if (node != oldNode) node else newNode }
                PreferenceUtils.setNodes(activity, newNodes).fold(
                    {
                        Toast.makeText(activity, activity.getText(R.string.toast_node_saved), Toast.LENGTH_SHORT).show()
                        nodeSelectionBottomSheetDialog.setNodes(newNodes)
                        self.dismiss()
                    },
                    {
                        Timber.e(it, "Failed to save node")
                        Toast.makeText(activity, activity.getText(R.string.toast_failed_to_save_node), Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        val editNodeDialog = EditNodeBottomSheetDialog(listener, node)

        editNodeDialog.show(activity.supportFragmentManager, "edit_node_dialog")
    }

    final override fun onAddNode(self: NodeSelectionBottomSheetDialog) {
        val nodeSelectionBottomSheetDialog = self
        val addNodeDialog = AddNodeBottomSheetDialog(object : AddNodeBottomSheetDialog.AddNodeListener {
            override fun onAddNode(self: AddNodeBottomSheetDialog, node: Node) {
                val nodes = PreferenceUtils.getOrSetDefaultNodes(activity, DefaultNode.defaultNodes())
                if (nodes.any { it.name == node.name }) {
                    Timber.i("Failed to add nodes due to node with the same name already existing")
                    Toast.makeText(activity, activity.getText(R.string.toast_node_with_name_already_exists), Toast.LENGTH_SHORT).show()
                } else {
                    val newNodes = nodes.toMutableList()
                    newNodes.add(node)
                    PreferenceUtils.setNodes(activity, newNodes).fold(
                        {
                            Timber.i("New node ${node.name} added successfully")
                            nodeSelectionBottomSheetDialog.setNodes(newNodes)
                            Toast.makeText(activity, activity.getText(R.string.toast_node_added), Toast.LENGTH_SHORT).show()
                            self.dismiss()
                        },
                        {
                            Timber.e(it, "Failed to add new node")
                            Toast.makeText(activity, activity.getText(R.string.toast_failed_to_add_node), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        })
        addNodeDialog.show(activity.supportFragmentManager, "add_node_dialog")
    }

    override fun onSortClicked(self: NodeSelectionBottomSheetDialog, sortOrder: SortOrder) {
        val newSortOrder = sortOrder.reverse()
        PreferenceUtils.setSortNodesAscending(activity, newSortOrder.toBoolean())
        self.setSortOrder(newSortOrder)
    }
}