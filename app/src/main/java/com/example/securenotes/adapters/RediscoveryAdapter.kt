package com.example.securenotes.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.securenotes.R
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.databinding.ItemRediscoveryCandidateBinding
import com.example.securenotes.models.RediscoveryCandidate

class RediscoveryAdapter(
    private val onOpenItem: (RediscoveryCandidate) -> Unit,
    private val onRevisitItem: (RediscoveryCandidate, View) -> Unit,
    private val onSnoozeItem: (RediscoveryCandidate, View) -> Unit,
    private val onDismissItem: (RediscoveryCandidate, View) -> Unit,
    private val onRestoreItem: (RediscoveryCandidate, View) -> Unit
) : ListAdapter<RediscoveryCandidate, RediscoveryAdapter.CandidateViewHolder>(DiffCallback) {

    class CandidateViewHolder(val binding: ItemRediscoveryCandidateBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder {
        val binding = ItemRediscoveryCandidateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CandidateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
        val candidate = getItem(position)
        val context = holder.itemView.context

        holder.binding.tvCandidateTitle.text = candidate.note.title
        holder.binding.tvCandidateScore.text = context.getString(
            R.string.rediscover_score_label, candidate.score
        )
        holder.binding.tvCandidateReason.text = candidate.reason
        holder.binding.tvCandidateContentPreview.text = candidate.note.content

        holder.itemView.setOnClickListener { onOpenItem(candidate) }

        val isDismissed = candidate.reviewStatus == DatabaseContract.NoteReviewQueue.STATUS_DISMISSED
        if (isDismissed) {
            holder.binding.btnCandidateDismiss.visibility = View.GONE
            holder.binding.btnCandidateSnooze.visibility = View.GONE
            holder.binding.btnCandidateRevisit.text = context.getString(R.string.rediscover_action_restore)
            holder.binding.btnCandidateRevisit.setOnClickListener { view ->
                onRestoreItem(candidate, view)
            }
        } else {
            holder.binding.btnCandidateDismiss.visibility = View.VISIBLE
            holder.binding.btnCandidateSnooze.visibility = View.VISIBLE
            holder.binding.btnCandidateRevisit.text = context.getString(R.string.rediscover_action_revisit)
            holder.binding.btnCandidateRevisit.setOnClickListener { view ->
                onRevisitItem(candidate, view)
            }
            holder.binding.btnCandidateSnooze.setOnClickListener { view ->
                onSnoozeItem(candidate, view)
            }
            holder.binding.btnCandidateDismiss.setOnClickListener { view ->
                onDismissItem(candidate, view)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RediscoveryCandidate>() {
        override fun areItemsTheSame(
            oldItem: RediscoveryCandidate,
            newItem: RediscoveryCandidate
        ): Boolean = oldItem.note.id == newItem.note.id

        override fun areContentsTheSame(
            oldItem: RediscoveryCandidate,
            newItem: RediscoveryCandidate
        ): Boolean = oldItem == newItem
    }
}
