package com.example.securenotes.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.securenotes.databinding.ItemThinkingFrameworkBinding
import com.example.securenotes.models.ThinkingFramework

class ThinkingFrameworkAdapter(
    private val frameworks: List<ThinkingFramework>,
    private val onSelectFramework: (ThinkingFramework) -> Unit
) : RecyclerView.Adapter<ThinkingFrameworkAdapter.FrameworkViewHolder>() {

    class FrameworkViewHolder(val binding: ItemThinkingFrameworkBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FrameworkViewHolder {
        val binding = ItemThinkingFrameworkBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FrameworkViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FrameworkViewHolder, position: Int) {
        val framework = frameworks[position]
        holder.binding.tvFrameworkTitle.text = framework.title
        holder.binding.tvFrameworkDescription.text = framework.description
        holder.binding.tvFrameworkStepCount.text = "${framework.steps.size} steps"
        holder.itemView.setOnClickListener { onSelectFramework(framework) }
    }

    override fun getItemCount(): Int = frameworks.size
}
