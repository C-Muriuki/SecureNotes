package com.example.securenotes.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.securenotes.R
import com.example.securenotes.databinding.ItemIdeaStageHistoryBinding
import com.example.securenotes.models.IdeaStage
import com.example.securenotes.models.IdeaStageHistory
import java.text.DateFormat
import java.util.Date

class IdeaStageHistoryAdapter : ListAdapter<IdeaStageHistory, IdeaStageHistoryAdapter.HistoryViewHolder>(DiffCallback) {

    class HistoryViewHolder(val binding: ItemIdeaStageHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemIdeaStageHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        val titleText = if (item.fromStage == item.toStage) {
            context.getString(R.string.idea_journey_history_initial, getStageName(context, item.toStage))
        } else {
            context.getString(
                R.string.idea_journey_history_moved,
                getStageName(context, item.fromStage),
                getStageName(context, item.toStage)
            )
        }
        holder.binding.tvHistoryTitle.text = titleText
        holder.binding.tvHistoryDate.text = DateFormat.getDateTimeInstance().format(Date(item.changedAt))
    }

    private fun getStageName(context: android.content.Context, stage: IdeaStage): String {
        return when (stage) {
            IdeaStage.CAPTURED -> context.getString(R.string.idea_stage_captured)
            IdeaStage.EXPLORING -> context.getString(R.string.idea_stage_exploring)
            IdeaStage.DEVELOPING -> context.getString(R.string.idea_stage_developing)
            IdeaStage.COMPLETED -> context.getString(R.string.idea_stage_completed)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<IdeaStageHistory>() {
        override fun areItemsTheSame(oldItem: IdeaStageHistory, newItem: IdeaStageHistory): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: IdeaStageHistory, newItem: IdeaStageHistory): Boolean = oldItem == newItem
    }
}
