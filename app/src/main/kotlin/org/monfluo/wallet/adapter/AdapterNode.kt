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
package org.monfluo.wallet.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.RadioButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.R
import org.monfluo.wallet.data.Node
import org.monfluo.wallet.model.NodeNetworkType
import org.monfluo.wallet.util.Helper

class AdapterNode(
    private val listener: Listener
) : RecyclerView.Adapter<AdapterNode.ViewHolder>() {

    private var nodes: List<Node> = emptyList()
    private var selectedNode: Node? = null

    @SuppressLint("NotifyDataSetChanged")
    fun setNodes(nodes: List<Node>) {
        this.nodes = nodes
        notifyDataSetChanged()
    }

    fun setSelectedNode(selectedNode: Node?) {
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

    interface Listener {
        fun onSelectNode(self: AdapterNode, node: Node)
        fun onEditNode(self: AdapterNode, node: Node)
    }

    inner class ViewHolder(private val listener: Listener, view: View) : RecyclerView.ViewHolder(view) {

        private val nodeWrapperLayout: View = itemView.findViewById(R.id.node_wrapper_layout)
        private val nodeRadioButton: RadioButton = itemView.findViewById(R.id.node_radiobutton)
        private val nodeIconImageView: ImageView = itemView.findViewById(R.id.node_icon_imagebutton)
        private val nodeNameTextView: TextView = itemView.findViewById(R.id.node_name_textview)
        private val nodeAddressTextView: TextView = itemView.findViewById(R.id.node_address_textview)
        private val nodeEditImageButton: ImageButton = itemView.findViewById(R.id.node_edit_imagebutton)

        fun bind(node: Node, isSelected: Boolean) {
            nodeRadioButton.setChecked(isSelected)
            nodeNameTextView.text = node.name
            nodeAddressTextView.text = node.address

            when (NodeNetworkType.fromAddress(node.address)) {
                NodeNetworkType.CLEARNET, null -> {
                    nodeIconImageView.background = ResourcesCompat.getDrawable(itemView.resources, R.drawable.highlighted_on_card_background, null)
                    nodeIconImageView.setImageDrawable(ResourcesCompat.getDrawable(itemView.resources, R.drawable.logo_monero_alternative, null))
                    nodeIconImageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    nodeIconImageView.adjustViewBounds = false
                }
                NodeNetworkType.TOR -> {
                    nodeIconImageView.setBackgroundColor(itemView.resources.getColor(R.color.transparent, null))
                    nodeIconImageView.setImageDrawable(ResourcesCompat.getDrawable(itemView.resources, R.drawable.tor, null))
                    nodeIconImageView.scaleType = ImageView.ScaleType.FIT_CENTER
                    nodeIconImageView.adjustViewBounds = true
                }
                NodeNetworkType.I2P -> {
                    nodeIconImageView.setBackgroundColor(itemView.resources.getColor(R.color.transparent, null))
                    nodeIconImageView.setImageDrawable(ResourcesCompat.getDrawable(itemView.resources, R.drawable.tor, null))
                    nodeIconImageView.scaleType = ImageView.ScaleType.FIT_CENTER
                    nodeIconImageView.adjustViewBounds = true
                }
            }

            val onSelectNode = { _: View -> listener.onSelectNode(this@AdapterNode, node) }
            nodeWrapperLayout.setOnClickListener(onSelectNode)
            nodeRadioButton.setOnClickListener(onSelectNode)
            nodeIconImageView.setOnClickListener(onSelectNode)

            nodeEditImageButton.setOnClickListener {
                listener.onEditNode(this@AdapterNode, node)
            }
        }
    }
}
