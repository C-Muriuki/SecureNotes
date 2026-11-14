package com.example.securenotes.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.securenotes.databinding.ItemConnectionCandidateBinding
import com.example.securenotes.models.Note

class ConnectionCandidateAdapter(
    private val onConnect: (Note) -> Unit
) : ListAdapter<Note, ConnectionCandidateAdapter.CandidateViewHolder>(DIFF) {

    private var busyNoteId: Long? = null

    class CandidateViewHolder(val binding: ItemConnectionCandidateBinding) : RecyclerView.ViewHolder(binding.root)

    fun setBusy(noteId: Long?) {
        busyNoteId = noteId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder =
        CandidateViewHolder(ItemConnectionCandidateBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
        val note = getItem(position)
        holder.binding.tvCandidateTitle.text = note.title
        holder.binding.tvCandidatePreview.text = note.content.replace(Regex("\\s+"), " ").take(120)
        holder.binding.btnConnectCandidate.isEnabled = busyNoteId == null
        holder.binding.btnConnectCandidate.setOnClickListener {
            if (busyNoteId == null) onConnect(note)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Note>() {
            override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean = oldItem == newItem
        }
    }
}
