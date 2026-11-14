package com.example.securenotes.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.securenotes.databinding.ItemPinnedNoteBinding
import com.example.securenotes.models.Note

class PinnedNotesAdapter(
    private val notes: List<Note>,
    private val onItemClick: (Note) -> Unit
) : RecyclerView.Adapter<PinnedNotesAdapter.PinnedViewHolder>() {
    class PinnedViewHolder(val binding: ItemPinnedNoteBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        PinnedViewHolder(ItemPinnedNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: PinnedViewHolder, position: Int) {
        val note = notes[position]
        holder.binding.tvPinnedTitle.text = note.title
        holder.binding.tvPinnedSnippet.text = note.content
        holder.itemView.setOnClickListener { onItemClick(note) }
    }
    override fun getItemCount(): Int = notes.size
}
