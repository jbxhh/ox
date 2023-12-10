package net.mynero.wallet.fragment.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.mynero.wallet.R
import net.mynero.wallet.adapter.NodeSelectionAdapter
import net.mynero.wallet.adapter.NodeSelectionAdapter.NodeSelectionAdapterListener
import net.mynero.wallet.data.DefaultNodes
import net.mynero.wallet.data.Node
import net.mynero.wallet.data.Node.Companion.fromJson
import net.mynero.wallet.data.Node.Companion.fromString
import net.mynero.wallet.service.DaemonService
import net.mynero.wallet.service.PrefService
import net.mynero.wallet.util.Constants
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class NodeSelectionBottomSheetDialog : BottomSheetDialogFragment(), NodeSelectionAdapterListener {
    var listener: NodeSelectionDialogListener? = null
    private var adapter: NodeSelectionAdapter? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.node_selection_bottom_sheet_dialog, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val nodes = ArrayList<Node>()
        adapter = NodeSelectionAdapter(this)
        val recyclerView = view.findViewById<RecyclerView>(R.id.node_selection_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter
        val addNodeButton = view.findViewById<Button>(R.id.add_node_button)
        addNodeButton.setOnClickListener {
            if (listener != null) {
                listener?.onClickedAddNode()
            }
            dismiss()
        }
        val nodesArray = PrefService.instance?.getString(Constants.PREF_CUSTOM_NODES, "[]")
        val jsonArray: JSONArray = try {
            JSONArray(nodesArray)
        } catch (e: JSONException) {
            return
        }
        for (i in 0 until jsonArray.length()) {
            var nodeJsonObject: JSONObject?
            try {
                nodeJsonObject = jsonArray.getJSONObject(i)
                if (nodeJsonObject != null) {
                    val node = fromJson(nodeJsonObject)
                    if (node != null) {
                        nodes.add(node)
                    }
                }
            } catch (e: JSONException) {
                // if stored node is old string format, try parse and upgrade, if fail then remove
                try {
                    val nodeString = jsonArray.getString(i)
                    val node = fromString(nodeString)
                    if (node != null) {
                        nodes.add(node)
                        jsonArray.put(i, node.toJson())
                    } else {
                        jsonArray.remove(i)
                    }
                } catch (ex: JSONException) {
                    throw RuntimeException(ex)
                }
            }
        }
        PrefService.instance?.edit()?.putString(Constants.PREF_CUSTOM_NODES, jsonArray.toString())
            ?.apply()
        for (defaultNode in DefaultNodes.values()) {
            fromJson(defaultNode.json)?.let { nodes.add(it) }
        }
        adapter?.submitList(nodes)
    }

    override fun onSelectNode(node: Node?) {
        PrefService.instance?.edit()?.putString(Constants.PREF_NODE_2, node?.toJson().toString())
            ?.apply()
        node?.let { DaemonService.instance?.setDaemon(it) }
        activity?.runOnUiThread {
            adapter?.updateSelectedNode()
        }
        listener?.onNodeSelected()
    }

    override fun onSelectEditNode(node: Node?): Boolean {
        if (listener != null) {
            listener?.onClickedEditNode(node)
        }
        dismiss()
        return true
    }

    interface NodeSelectionDialogListener {
        fun onNodeSelected()
        fun onClickedEditNode(node: Node?)
        fun onClickedAddNode()
    }
}