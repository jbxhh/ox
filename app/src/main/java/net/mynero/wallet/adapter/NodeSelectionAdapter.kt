/*
 * Copyright (c) 2017 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.mynero.wallet.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import net.mynero.wallet.R
import net.mynero.wallet.data.DefaultNodes
import net.mynero.wallet.data.Node
import net.mynero.wallet.service.PrefService

class NodeSelectionAdapter(val listener: NodeSelectionAdapterListener?) :
    RecyclerView.Adapter<NodeSelectionAdapter.ViewHolder>() {
    private var localDataSet: List<Node>

    /**
     * Initialize the dataset of the Adapter.
     */
    init {
        localDataSet = ArrayList()
    }

    fun submitList(dataSet: List<Node>) {
        localDataSet = dataSet
        notifyDataSetChanged()
    }

    fun updateSelectedNode() {
        notifyDataSetChanged()
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.node_selection_item, viewGroup, false)
        return ViewHolder(listener, view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val node = localDataSet[position]
        viewHolder.bind(node)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return localDataSet.size
    }

    interface NodeSelectionAdapterListener {
        fun onSelectNode(node: Node?)
        fun onSelectEditNode(node: Node?): Boolean
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(private val listener: NodeSelectionAdapterListener?, view: View) :
        RecyclerView.ViewHolder(
            view
        ) {
        fun bind(node: Node) {
            val currentNode = PrefService.getInstance().node
            val match = node == currentNode
            if (match) {
                itemView.setBackgroundColor(itemView.resources.getColor(R.color.oled_colorSecondary))
            } else {
                itemView.setBackgroundColor(itemView.resources.getColor(android.R.color.transparent))
            }
            val nodeNameTextView = itemView.findViewById<TextView>(R.id.node_name_textview)
            val nodeAddressTextView = itemView.findViewById<TextView>(R.id.node_uri_textview)
            val authTextView = itemView.findViewById<TextView>(R.id.authenticated_textview)
            nodeNameTextView.text = node.name
            nodeAddressTextView.text = node.address
            if (node.password.isNotEmpty()) {
                authTextView.visibility = View.VISIBLE
            }
            val nodeAnonymityNetworkImageView =
                itemView.findViewById<ImageView>(R.id.anonymity_network_imageview)
            if (node.isOnion) {
                nodeAnonymityNetworkImageView.visibility = View.VISIBLE
                nodeAnonymityNetworkImageView.setImageResource(R.drawable.tor)
            } else if (node.isI2P) {
                nodeAnonymityNetworkImageView.visibility = View.VISIBLE
                nodeAnonymityNetworkImageView.setImageResource(R.drawable.i2p)
            } else {
                nodeAnonymityNetworkImageView.visibility = View.GONE
            }
            itemView.setOnLongClickListener {
                if (match) {
                    Toast.makeText(
                        itemView.context,
                        itemView.resources.getString(R.string.cant_edit_current_node),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnLongClickListener true
                } else if (isDefaultNode(node)) {
                    Toast.makeText(
                        itemView.context,
                        itemView.resources.getString(R.string.cant_edit_default_nodes),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnLongClickListener true
                } else {
                    return@setOnLongClickListener listener?.onSelectEditNode(node) == true
                }
            }
            itemView.setOnClickListener { listener?.onSelectNode(node) }
        }

        private fun isDefaultNode(currentNode: Node): Boolean {
            var isDefault = false
            for (defaultNode in DefaultNodes.values()) {
                if (currentNode.toNodeString() == defaultNode.nodeString) isDefault = true
            }
            return isDefault
        }
    }
}