package org.monfluo.wallet.android.data

import android.content.Context
import org.json.JSONObject
import org.monfluo.wallet.android.util.Constants
import org.monfluo.wallet.android.util.PreferenceUtils
import org.monfluo.wallet.lib.data.Network
import timber.log.Timber
import java.util.UUID

private const val NODES = "nodes"
private const val SELECTED = "selected"
private const val NAME = "name"
private const val ADDRESS = "address"
private const val CREDENTIALS = "credentials"
private const val USERNAME = "username"
private const val PASSWORD = "password"
private const val NETWORK = "network"

object NodeStore {
    private data class Document(
        val nodes: Map<String, Node>,
        val selected: Map<Network, String>,
    )

    fun nodes(context: Context): List<Node> = load(context).nodes.values.toList()

    fun selected(
        context: Context,
        network: Network,
    ): Node? {
        val document = load(context)
        return document.selected[network]?.let { document.nodes[it] }
    }

    fun selections(context: Context): Map<Network, Node> {
        val document = load(context)
        return document.selected.mapNotNull { (network, id) -> document.nodes[id]?.let { network to it } }.toMap()
    }

    fun clearSelection(
        context: Context,
        network: Network,
    ) {
        val document = load(context)
        store(context, document.copy(selected = document.selected - network))
    }

    fun add(
        context: Context,
        name: String,
        address: String,
        credentials: Credentials?,
        network: Network,
    ) {
        val node = Node(UUID.randomUUID().toString(), name, address, credentials, network)
        val document = load(context)
        store(context, document.copy(nodes = document.nodes + (node.id to node)))
    }

    fun update(
        context: Context,
        node: Node,
    ) {
        val document = load(context)
        if (!document.nodes.containsKey(node.id)) {
            Timber.e("Failed to update node ${node.id} because it is not found")
            return
        }
        store(context, document.copy(nodes = document.nodes + (node.id to node)))
    }

    fun delete(
        context: Context,
        id: String,
    ) {
        val document = load(context)
        store(context, document.copy(nodes = document.nodes - id))
    }

    fun select(
        context: Context,
        node: Node,
    ) {
        val document = load(context)
        store(context, document.copy(selected = document.selected + (node.network to node.id)))
    }

    private fun load(context: Context): Document {
        val jsonString = PreferenceUtils.getString(context, Constants.PREF_NODES)
        val document = jsonString?.let { decode(it) }
        if (document != null) {
            return document
        }
        val seeded = Document(DefaultNode.defaultNodes().associateBy { it.id }, emptyMap())
        store(context, seeded)
        return seeded
    }

    private fun store(
        context: Context,
        document: Document,
    ) {
        val nodesJson = JSONObject()
        for (node in document.nodes.values) {
            nodesJson.put(node.id, encodeNode(node))
        }
        val selectedJson = JSONObject()
        for ((network, id) in document.selected) {
            selectedJson.put(network.name, id)
        }
        val json = JSONObject().put(NODES, nodesJson).put(SELECTED, selectedJson)
        PreferenceUtils.setString(context, Constants.PREF_NODES, json.toString())
    }

    private fun decode(raw: String): Document? {
        val json =
            runCatching { JSONObject(raw) }.getOrElse {
                Timber.e(it, "Failed to parse nodes")
                return null
            }
        val nodes = mutableMapOf<String, Node>()
        val nodesJson = json.optJSONObject(NODES) ?: JSONObject()
        for (id in nodesJson.keys()) {
            val node = nodesJson.optJSONObject(id)?.let { decodeNode(id, it) }
            if (node != null) {
                nodes[id] = node
            }
        }
        val selected = mutableMapOf<Network, String>()
        val selectedJson = json.optJSONObject(SELECTED) ?: JSONObject()
        for (network in Network.entries) {
            val id = selectedJson.optString(network.name)
            if (id.isNotEmpty()) {
                selected[network] = id
            }
        }
        return Document(nodes, selected)
    }

    private fun encodeNode(node: Node): JSONObject {
        val json =
            JSONObject()
                .put(NAME, node.name)
                .put(ADDRESS, node.address)
                .put(NETWORK, node.network.name)
        node.credentials?.let {
            json.put(CREDENTIALS, JSONObject().put(USERNAME, it.username).put(PASSWORD, it.password))
        }
        return json
    }

    private fun decodeNode(
        id: String,
        json: JSONObject,
    ): Node? {
        val name = json.optString(NAME)
        val address = json.optString(ADDRESS)
        val network = Network.entries.find { it.name == json.optString(NETWORK) }
        if (name.isEmpty() || address.isEmpty() || network == null) {
            Timber.e("Failed to parse node $id")
            return null
        }
        val credentials =
            json.optJSONObject(CREDENTIALS)?.let {
                Credentials(it.optString(USERNAME), it.optString(PASSWORD))
            }
        return Node(id, name, address, credentials, network)
    }
}
