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

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import net.mynero.wallet.R
import net.mynero.wallet.data.Node

class NodeSelectionAdapter(
    private var nodes: List<Node>,
    private var selectedNode: Node,
    private val listener: NodeSelectionAdapterListener
) : RecyclerView.Adapter<NodeSelectionAdapter.ViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun setNodes(nodes: List<Node>) {
        this.nodes = nodes
        notifyDataSetChanged()
    }

    fun setSelectedNode(selectedNode: Node) {
        val oldSelectedNodePosition = nodes.indexOf(this.selectedNode)
        val newSelectedNodePosition = nodes.indexOf(selectedNode)
        this.selectedNode = selectedNode
        if (oldSelectedNodePosition >= 0) {
            notifyItemChanged(oldSelectedNodePosition)
        }
        if (newSelectedNodePosition >= 0) {
            notifyItemChanged(newSelectedNodePosition)
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.recycler_view_item_node, viewGroup, false)
        return ViewHolder(listener, view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val node = nodes[position]
        viewHolder.bind(node, node == selectedNode)
    }

    override fun getItemCount(): Int {
        return nodes.size
    }

    interface NodeSelectionAdapterListener {
        fun onSelectNode(node: Node)
        fun onEditNode(node: Node)
    }

    class ViewHolder(private val listener: NodeSelectionAdapterListener, view: View) :
        RecyclerView.ViewHolder(
            view
        ) {
        fun bind(node: Node, isSelected: Boolean) {
            if (isSelected) {
                itemView.setBackgroundColor(
                    ContextCompat.getColor(
                        itemView.context,
                        R.color.oled_colorSecondary
                    )
                )
            } else {
                itemView.setBackgroundColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.transparent
                    )
                )
            }
            val nodeNameTextView = itemView.findViewById<TextView>(R.id.node_name_textview)
            val nodeAddressTextView = itemView.findViewById<TextView>(R.id.node_uri_textview)
            val authTextView = itemView.findViewById<TextView>(R.id.authenticated_textview)
            val trustedTextView = itemView.findViewById<TextView>(R.id.trusted_node_textview)
            nodeNameTextView.text = node.name
            nodeAddressTextView.text = node.address
            if (!node.password.isNullOrBlank()) {
                authTextView.visibility = View.VISIBLE
            }
            if (node.trusted) {
                trustedTextView.visibility = View.VISIBLE
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
                listener.onEditNode(node)
                return@setOnLongClickListener true
            }
            itemView.setOnClickListener { listener.onSelectNode(node) }
        }
    }
}