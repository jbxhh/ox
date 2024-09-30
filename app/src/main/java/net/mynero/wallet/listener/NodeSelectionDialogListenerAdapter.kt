package net.mynero.wallet.listener

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import net.mynero.wallet.R
import net.mynero.wallet.data.Node
import net.mynero.wallet.data.Node.Companion.fromJson
import net.mynero.wallet.fragment.dialog.AddNodeBottomSheetDialog
import net.mynero.wallet.fragment.dialog.EditNodeBottomSheetDialog
import net.mynero.wallet.fragment.dialog.NodeSelectionBottomSheetDialog
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.util.Constants
import net.mynero.wallet.util.Utils
import org.json.JSONArray

class NodeSelectionDialogListenerAdapter(
    val activity: FragmentActivity,
    val setSelectNodeButtonText: (String) -> Unit,
    val getProxyAndPortValues: () -> Pair<String, String>,
) : NodeSelectionBottomSheetDialog.NodeSelectionDialogListener {

    override fun onNodeSelected() {
        val node = PrefService.instance.node
        setSelectNodeButtonText(activity.getString(R.string.node_button_text, node?.address))

        val (proxyAddress, proxyPort) = getProxyAndPortValues()
        Utils.refreshProxy(proxyAddress, proxyPort)

        activity.runOnUiThread {
            Toast.makeText(
                activity,
                activity.getString(R.string.node_selected, node?.name ?: node?.host),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onClickedEditNode(node: Node) {
        val listener = object : EditNodeBottomSheetDialog.EditNodeListener {
            override fun onNodeDeleted(node: Node) {
                try {
                    val nodesArray = PrefService.instance.getString(Constants.PREF_CUSTOM_NODES, "[]")
                    val jsonArray = JSONArray(nodesArray)
                    for (i in 0 until jsonArray.length()) {
                        val nodeJsonObject = jsonArray.getJSONObject(i)
                        val savedNode = fromJson(nodeJsonObject)
                        if (savedNode?.toNodeString() == node.toNodeString()) jsonArray.remove(i)
                    }
                    saveNodes(jsonArray)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onNodeEdited(oldNode: Node?, newNode: Node?) {
                try {
                    val nodesArray = PrefService.instance.getString(Constants.PREF_CUSTOM_NODES, "[]")
                    val jsonArray = JSONArray(nodesArray)
                    for (i in 0 until jsonArray.length()) {
                        val nodeJsonObject = jsonArray.getJSONObject(i)
                        val savedNode = fromJson(nodeJsonObject)
                        if (savedNode?.toNodeString() == oldNode?.toNodeString()) jsonArray.put(
                            i,
                            newNode?.toJson()
                        )
                    }
                    saveNodes(jsonArray)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val editNodeDialog = EditNodeBottomSheetDialog(listener, node)

        editNodeDialog.show(activity.supportFragmentManager, "edit_node_dialog")
    }

    override fun onClickedAddNode() {
        val addNodeDialog = AddNodeBottomSheetDialog()
        addNodeDialog.listener = object : AddNodeBottomSheetDialog.AddNodeListener {
            override fun onNodeAdded() {}
        }
        addNodeDialog.show(activity.supportFragmentManager, "add_node_dialog")
    }

    private fun saveNodes(jsonArray: JSONArray) {
        PrefService.instance.edit().putString(Constants.PREF_CUSTOM_NODES, jsonArray.toString()).apply()
    }
}