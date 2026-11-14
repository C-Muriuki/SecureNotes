package com.example.securenotes.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.securenotes.R
import com.example.securenotes.databinding.ItemConnectedThoughtBinding
import com.example.securenotes.models.ConnectedThought
import java.text.DateFormat
import java.util.Date

class ConnectedThoughtsAdapter(
    private val onOpen: (ConnectedThought) -> Unit,
    private val onRemove: (ConnectedThought, View) -> Unit
) : ListAdapter<ConnectedThought, ConnectedThoughtsAdapter.ThoughtViewHolder>(DIFF) {

    class ThoughtViewHolder(val binding: ItemConnectedThoughtBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThoughtViewHolder =
        ThoughtViewHolder(ItemConnectedThoughtBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ThoughtViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.tvConnectedThoughtTitle.text = item.note.title
        holder.binding.tvConnectedThoughtPreview.text = item.note.content
            .replace(Regex("\\s+"), " ").take(120)
        holder.binding.tvConnectedThoughtDate.text = holder.itemView.context.getString(
            R.string.thought_connection_created,
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.connectedAt))
        )
        holder.binding.root.setOnClickListener {
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                holder.binding.root.animate().cancel()
                holder.binding.root.animate().alpha(0.72f).setDuration(70L).withEndAction {
                    holder.binding.root.animate().alpha(1f).setDuration(100L).start()
                }.start()
            }
            onOpen(item)
        }
        holder.binding.btnRemoveThoughtConnection.setOnClickListener { onRemove(item, it) }
        holder.binding.btnRemoveThoughtConnection.contentDescription =
            holder.itemView.context.getString(R.string.thought_connection_remove_accessibility, item.note.title)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ConnectedThought>() {
            override fun areItemsTheSame(oldItem: ConnectedThought, newItem: ConnectedThought): Boolean =
                oldItem.note.id == newItem.note.id

            override fun areContentsTheSame(oldItem: ConnectedThought, newItem: ConnectedThought): Boolean =
                oldItem == newItem
        }
    }
}
