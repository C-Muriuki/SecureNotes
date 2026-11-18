package com.example.securenotes.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.securenotes.R
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.databinding.ItemThinkingSessionBinding
import com.example.securenotes.models.ThinkingSession
import com.example.securenotes.repositories.ThinkingTemplateRepository
import java.text.DateFormat
import java.util.Date

class ThinkingSessionAdapter(
    private val onOpenSession: (ThinkingSession) -> Unit
) : ListAdapter<ThinkingSession, ThinkingSessionAdapter.SessionViewHolder>(DiffCallback) {

    class SessionViewHolder(val binding: ItemThinkingSessionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemThinkingSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = getItem(position)
        val context = holder.itemView.context
        val framework = ThinkingTemplateRepository.getFramework(session.frameworkId)

        holder.binding.tvSessionFrameworkTitle.text = framework?.title ?: session.frameworkId
        holder.binding.tvSessionDate.text = DateFormat.getDateTimeInstance().format(Date(session.updatedAt))

        val isCompleted = session.status == DatabaseContract.ThinkingSessionTable.STATUS_COMPLETED
        holder.binding.tvSessionStatusBadge.text = context.getString(
            if (isCompleted) R.string.guided_thinking_status_completed else R.string.guided_thinking_status_active
        )

        val totalSteps = framework?.steps?.size ?: 0
        val answeredCount = session.responses.count { it.response.isNotBlank() }
        holder.binding.tvSessionResponsesCount.text = "$answeredCount of $totalSteps responses recorded"

        holder.itemView.setOnClickListener { onOpenSession(session) }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ThinkingSession>() {
        override fun areItemsTheSame(
            oldItem: ThinkingSession,
            newItem: ThinkingSession
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: ThinkingSession,
            newItem: ThinkingSession
        ): Boolean = oldItem == newItem
    }
}
