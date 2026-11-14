package com.example.securenotes.adapters

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.securenotes.R
import com.example.securenotes.databinding.ItemNoteBinding
import com.example.securenotes.models.Note
import java.text.DateFormat
import java.util.Date

class NotesAdapter(
    private val onItemClick: (Note) -> Unit,
    private val onDelete: ((Note) -> Unit)? = null,
    private val onPinToggle: ((Note, View) -> Unit)? = null
) : ListAdapter<Note, NotesAdapter.NoteViewHolder>(DIFF) {

    class NoteViewHolder(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root) {
        var entranceAnimationApplied = false
    }

    fun noteAt(position: Int): Note = getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder =
        NoteViewHolder(ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = getItem(position)
        animateFirstAppearance(holder)
        holder.binding.tvNoteTitle.text = note.title
        holder.binding.tvNotePreview.text = note.content
        holder.binding.tvNoteDate.text =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.updatedAt))
        holder.binding.ivPinIndicator.apply {
            visibility = View.VISIBLE
            isSelected = note.isPinned
            alpha = if (note.isPinned) 1f else 0.58f
            contentDescription = context.getString(
                if (note.isPinned) R.string.action_unpin_note else R.string.action_pin_note
            )
            isEnabled = onPinToggle != null
            setOnClickListener {
                onPinToggle?.invoke(note, this)
            }
        }
        holder.binding.tvEncryptionStatus.visibility = View.GONE
        holder.itemView.setOnClickListener { onItemClick(note) }
        holder.itemView.setOnLongClickListener {
            onDelete?.invoke(note)
            onDelete != null
        }
    }

    private fun animateFirstAppearance(holder: NoteViewHolder) {
        if (holder.entranceAnimationApplied) return
        holder.entranceAnimationApplied = true
        val item = holder.itemView
        item.animate().cancel()
        item.alpha = 1f
        item.translationY = 0f
        if (!ValueAnimator.areAnimatorsEnabled()) return

        item.alpha = 0f
        item.translationY = 8f * item.resources.displayMetrics.density
        item.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180L)
            .start()
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Note>() {
            override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean = oldItem == newItem
        }
    }
}
