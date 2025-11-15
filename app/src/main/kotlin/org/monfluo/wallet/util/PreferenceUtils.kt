package org.monfluo.wallet.util

import android.content.Context
import android.content.SharedPreferences
import org.monfluo.wallet.data.Node
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import androidx.core.content.edit

object PreferenceUtils {

    fun getSharedPreferences(context: Context): SharedPreferences = context.getSharedPreferences(
        Constants.PREFERENCES_KEY,
        Context.MODE_PRIVATE
    )

    fun getBoolean(context: Context, key: String, default: Boolean): Boolean = getSharedPreferences(context).getBoolean(key, default)

    fun setBoolean(context: Context, key: String, value: Boolean) = getSharedPreferences(context).edit {
        putBoolean(
            key,
            value
        )
    }

    fun getString(context: Context, key: String, default: String? = null): String? = getSharedPreferences(context).getString(key, default)

    fun setString(context: Context, key: String, value: String) = getSharedPreferences(context).edit {
        putString(
            key,
            value
        )
    }

    fun isPostNotificationsPermissionRequested(context: Context) = getBoolean(context, Constants.PREF_POST_NOTIFICATIONS_PERMISSION_REQUESTED, false)

    fun setPostNotificationsPermissionRequested(context: Context, value: Boolean = true) = setBoolean(context, Constants.PREF_POST_NOTIFICATIONS_PERMISSION_REQUESTED, value)

    fun isUseProxy(context: Context): Boolean = getBoolean(context, Constants.PREF_USE_PROXY, false)

    fun setUseProxy(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_USE_PROXY, value)

    fun isMultiWalletMode(context: Context): Boolean = getBoolean(context, Constants.PREF_ENABLE_MULTIPLE_WALLETS, false)

    fun setMultiWalletMode(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_ENABLE_MULTIPLE_WALLETS, value)

    fun isMultiAccountMode(context: Context): Boolean = getBoolean(context, Constants.PREF_ENABLE_MULTIPLE_ACCOUNTS, false)

    fun setMultiAccountMode(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_ENABLE_MULTIPLE_ACCOUNTS, value)

    fun isStreetMode(context: Context): Boolean = getBoolean(context, Constants.PREF_STREET_MODE, false)

    fun setStreetMode(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_STREET_MODE, value)

    fun isAllowFeeOverride(context: Context): Boolean = getBoolean(context, Constants.PREF_ALLOW_FEE_OVERRIDE, false)

    fun setAllowFeeOverride(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_ALLOW_FEE_OVERRIDE, value)

    fun isAllowCopyingSecrets(context: Context): Boolean = getBoolean(context, Constants.PREF_ALLOW_COPYING_SECRETS, false)

    fun setAllowCopyingSecrets(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_ALLOW_COPYING_SECRETS, value)

    fun isSortNodesAscending(context: Context): Boolean = getBoolean(context, Constants.PREF_SORT_NODES_ASCENDING, true)

    fun setSortNodesAscending(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_SORT_NODES_ASCENDING, value)

    fun isShowTransferNotifications(context: Context): Boolean = getBoolean(context, Constants.PREF_SHOW_TRANSFER_NOTIFICATIONS, false)

    fun setShowTransferNotifications(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_SHOW_TRANSFER_NOTIFICATIONS, value)

    fun isShowIconOnQrCodes(context: Context): Boolean = getBoolean(context, Constants.PREF_SHOW_ICON_ON_QR_CODES, false)

    fun setShowIconOnQrCodes(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_SHOW_ICON_ON_QR_CODES, value)

    fun isAutoRotateAddresses(context: Context): Boolean = getBoolean(context, Constants.PREF_AUTO_ROTATE_ADDRESSES, true)

    fun setAutoRotateAddresses(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_AUTO_ROTATE_ADDRESSES, value)

    fun isEnableEnoteManagement(context: Context): Boolean = getBoolean(context, Constants.PREF_ENABLE_ENOTE_MANAGEMENT, false)

    fun setEnableEnoteManagement(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_ENABLE_ENOTE_MANAGEMENT, value)

    fun isBalanceTapTogglesStreetMode(context: Context): Boolean = getBoolean(context, Constants.PREF_BALANCE_TAP_TOGGLES_STREET_MODE, false)

    fun setBalanceTapTogglesStreetMode(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_BALANCE_TAP_TOGGLES_STREET_MODE, value)

    fun isMaxBrightnessQrCodes(context: Context): Boolean = getBoolean(context, Constants.PREF_MAX_BRIGHTNESS_QR_CODES, true)

    fun setMaxBrightnessQrCodes(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_MAX_BRIGHTNESS_QR_CODES, value)

    fun isForceUriFormatQrCodes(context: Context): Boolean = getBoolean(context, Constants.PREF_FORCE_URI_FORMAT_QR_CODES, true)

    fun setForceUriFormatQrCodes(context: Context, value: Boolean) = setBoolean(context, Constants.PREF_FORCE_URI_FORMAT_QR_CODES, value)

    fun getProxy(context: Context): String? = getString(context, Constants.PREF_PROXY)

    fun setProxy(context: Context, value: String) = setString(context, Constants.PREF_PROXY, value)

    fun getProxyIfEnabled(context: Context): String? = if (isUseProxy(context)) getProxy(context) else null

    fun getNodeByName(context: Context, nodeName: String): Result<Node?> = getNodes(context).map { nodes ->
        nodes.find { node ->
            node.name == nodeName
        }
    }

    private fun getNodes(context: Context): Result<List<Node>> = runCatching {
        val jsonArrayString = getString(context, Constants.PREF_NODES, "[]")
        val jsonArray = JSONArray(jsonArrayString)
        val nodes = mutableListOf<Node>()
        for (i in 0 until jsonArray.length()) {
            val nodeJson = jsonArray.getJSONObject(i)
            Node.fromJson(nodeJson).fold(
                {
                    nodes.add(it)
                },
                {
                    Timber.e(it, "Failed to parse node from string $nodeJson")
                }
            )
        }
        return Result.success(nodes)
    }

    fun setNodes(context: Context, nodes: List<Node>): Result<Unit> = kotlin.runCatching {
        val jsonNodes = nodes.mapNotNull { node ->
            node.toJson().fold(
                {
                    it
                },
                {
                    Timber.e(it, "Failed to convert node $node to json")
                    null
                }
            )
        }
        val jsonArray = JSONArray()
        jsonNodes.forEach {
            jsonArray.put(it)
        }
        val jsonArrayString = jsonArray.toString()
        setString(context, Constants.PREF_NODES, jsonArrayString)
    }

    private fun setDefaultNodesAndLogError(context: Context, defaultNodes: List<Node>) {
        setNodes(context, defaultNodes).exceptionOrNull()?.let { setNodesException ->
            Timber.e(setNodesException, "Failed to set default nodes")
        }
    }

    fun getOrSetDefaultNodes(context: Context, defaultNodes: List<Node>): List<Node> {
        return getNodes(context).fold(
            { nodes ->
                if (nodes.isEmpty()) {
                    Timber.i("No nodes found, saving default nodes")
                    setDefaultNodesAndLogError(context, defaultNodes)
                    return@fold defaultNodes
                } else {
                    return@fold nodes
                }
            },
            { getNodesException ->
                Timber.e(getNodesException, "Failed to get nodes, saving default nodes")
                setDefaultNodesAndLogError(context, defaultNodes)
                return@fold defaultNodes
            }
        )
    }

    fun setNode(context: Context, node: Node): Result<Unit> = node.toJson().map { nodeJson ->
        setString(context, Constants.PREF_NODE, nodeJson.toString())
    }

    private fun getNode(context: Context): Result<Node?> {
        val nodeJsonString = getString(context, Constants.PREF_NODE) ?: return Result.success(null)
        val nodeJson = kotlin.runCatching { JSONObject(nodeJsonString) }
        return nodeJson.fold(
            {
                Node.fromJson(it)
            },
            {
                Result.failure(it)
            }
        )
    }

    private fun setDefaultNodeAndLogError(context: Context, defaultNode: Node) {
        setNode(context, defaultNode).exceptionOrNull()?.let { setNodesException ->
            Timber.e(setNodesException, "Failed to set default node")
        }
    }

    fun getOrSetDefaultNode(context: Context, defaultNode: Node): Node {
        return getNode(context).fold(
            { node ->
                if (node == null) {
                    Timber.i("No node found, saving default node")
                    setDefaultNodeAndLogError(context, defaultNode)
                    return@fold defaultNode
                } else {
                    return@fold node
                }
            },
            { getNodeException ->
                Timber.e(getNodeException, "Failed to get node, saving default node")
                setDefaultNodeAndLogError(context, defaultNode)
                return@fold defaultNode
            }
        )
    }
}
