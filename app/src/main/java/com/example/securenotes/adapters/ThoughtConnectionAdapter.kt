package com.example.securenotes.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.securenotes.R
import com.example.securenotes.databinding.ItemThoughtConnectionBinding
import com.example.securenotes.models.ThoughtConnection

class ThoughtConnectionAdapter(
    private val onOpenFirst: (ThoughtConnection) -> Unit,
    private val onOpenSecond: (ThoughtConnection) -> Unit
) : ListAdapter<ThoughtConnection, ThoughtConnectionAdapter.ConnectionViewHolder>(DIFF) {

    class ConnectionViewHolder(val binding: ItemThoughtConnectionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConnectionViewHolder =
        ConnectionViewHolder(ItemThoughtConnectionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ConnectionViewHolder, position: Int) {
        val connection = getItem(position)
        holder.binding.tvConnectionFirstTitle.text = connection.first.title
        holder.binding.tvConnectionFirstPreview.text = connection.first.content.replace(Regex("\\s+"), " ").take(90)
        holder.binding.tvConnectionSecondTitle.text = connection.second.title
        holder.binding.tvConnectionSecondPreview.text = connection.second.content.replace(Regex("\\s+"), " ").take(90)
        holder.binding.btnOpenFirstThought.text = holder.itemView.context.getString(
            R.string.thought_connection_open_named, connection.first.title
        )
        holder.binding.btnOpenSecondThought.text = holder.itemView.context.getString(
            R.string.thought_connection_open_named, connection.second.title
        )
        holder.binding.btnOpenFirstThought.setOnClickListener { onOpenFirst(connection) }
        holder.binding.btnOpenSecondThought.setOnClickListener { onOpenSecond(connection) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ThoughtConnection>() {
            override fun areItemsTheSame(oldItem: ThoughtConnection, newItem: ThoughtConnection): Boolean =
                oldItem.first.id == newItem.first.id && oldItem.second.id == newItem.second.id

            override fun areContentsTheSame(oldItem: ThoughtConnection, newItem: ThoughtConnection): Boolean =
                oldItem == newItem
        }
    }
}
