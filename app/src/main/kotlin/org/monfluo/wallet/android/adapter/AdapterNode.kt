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
package org.monfluo.wallet.android.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.data.Node
import org.monfluo.wallet.android.data.resourceId
import org.monfluo.wallet.android.model.NodeNetworkType
import org.monfluo.wallet.lib.data.Network

sealed interface NodeListItem {
    data class Header(
        val network: Network,
    ) : NodeListItem

    data class Offline(
        val network: Network,
        val isSelected: Boolean,
    ) : NodeListItem

    data class Entry(
        val node: Node,
        val isSelected: Boolean,
    ) : NodeListItem
}

private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_OFFLINE = 1
private const val VIEW_TYPE_ENTRY = 2

class AdapterNode(
    private val listener: Listener,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var items: List<NodeListItem> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<NodeListItem>) {
        if (this.items != items) {
            this.items = items
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is NodeListItem.Header -> VIEW_TYPE_HEADER
            is NodeListItem.Offline -> VIEW_TYPE_OFFLINE
            is NodeListItem.Entry -> VIEW_TYPE_ENTRY
        }

    override fun onCreateViewHolder(
        viewGroup: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(viewGroup.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.recycler_view_item_node_header, viewGroup, false))
            VIEW_TYPE_OFFLINE -> OfflineViewHolder(inflater.inflate(R.layout.recycler_view_item_node_offline, viewGroup, false))
            else -> EntryViewHolder(inflater.inflate(R.layout.recycler_view_item_node, viewGroup, false))
        }
    }

    override fun onBindViewHolder(
        viewHolder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val item = items[position]) {
            is NodeListItem.Header -> (viewHolder as HeaderViewHolder).bind(item)
            is NodeListItem.Offline -> (viewHolder as OfflineViewHolder).bind(item)
            is NodeListItem.Entry -> (viewHolder as EntryViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    interface Listener {
        fun onSelectNode(node: Node)

        fun onEditNode(node: Node)

        fun onSelectOffline(network: Network)
    }

    private class HeaderViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        private val nodeHeaderTextView: TextView = itemView.findViewById(R.id.node_header_textview)

        fun bind(item: NodeListItem.Header) {
            nodeHeaderTextView.setText(item.network.resourceId())
        }
    }

    private inner class OfflineViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        private val nodeOfflineWrapperLayout: View = itemView.findViewById(R.id.node_offline_wrapper_layout)
        private val nodeOfflineRadioButton: RadioButton = itemView.findViewById(R.id.node_offline_radiobutton)

        fun bind(item: NodeListItem.Offline) {
            nodeOfflineRadioButton.isChecked = item.isSelected
            nodeOfflineRadioButton.isClickable = false

            nodeOfflineWrapperLayout.setOnClickListener { _: View -> listener.onSelectOffline(item.network) }
        }
    }

    private inner class EntryViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        private val nodeWrapperLayout: View = itemView.findViewById(R.id.node_wrapper_layout)
        private val nodeRadioButton: RadioButton = itemView.findViewById(R.id.node_radiobutton)
        private val nodeIconImageView: ImageView = itemView.findViewById(R.id.node_icon_imagebutton)
        private val nodeNameTextView: TextView = itemView.findViewById(R.id.node_name_textview)
        private val nodeAddressTextView: TextView = itemView.findViewById(R.id.node_address_textview)
        private val nodeEditImageButton: ImageButton = itemView.findViewById(R.id.node_edit_imagebutton)

        fun bind(item: NodeListItem.Entry) {
            val node = item.node

            nodeRadioButton.isChecked = item.isSelected
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
                    nodeIconImageView.setImageDrawable(ResourcesCompat.getDrawable(itemView.resources, R.drawable.i2p, null))
                    nodeIconImageView.scaleType = ImageView.ScaleType.FIT_CENTER
                    nodeIconImageView.adjustViewBounds = true
                }
            }

            val onSelectNode = { _: View -> listener.onSelectNode(node) }
            nodeRadioButton.isClickable = false
            nodeWrapperLayout.setOnClickListener(onSelectNode)
            nodeIconImageView.setOnClickListener(onSelectNode)

            nodeEditImageButton.setOnClickListener {
                listener.onEditNode(node)
            }
        }
    }
}
