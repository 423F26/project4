package com.example.liftingapp.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.data.LiftWithAthlete
import com.example.liftingapp.data.Strength
import com.example.liftingapp.databinding.ItemHistoryBinding
import com.example.liftingapp.databinding.TabHistoryBinding

/** Every logged set, newest first, with its e1RM. */
class HistoryTab(owner: LifecycleOwner, binding: TabHistoryBinding, viewModel: LiftViewModel) {

    private val adapter = HistoryAdapter()

    init {
        binding.historyList.adapter = adapter
        viewModel.history.observe(owner) { items ->
            adapter.submit(items)
            binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}

private class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    class Holder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    private var items: List<LiftWithAthlete> = emptyList()

    @SuppressLint("NotifyDataSetChanged")   // fine for a short list; switch to ListAdapter + DiffUtil if it grows
    fun submit(newItems: List<LiftWithAthlete>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val set = item.lift
        val e1rm = Strength.estimatedOneRepMax(set.weight, set.reps)
        holder.binding.title.text = item.athlete.name
        holder.binding.detail.text = "${set.exercise}, ${set.weight.lb()} lb × ${set.reps}"
        holder.binding.date.text = set.date.shortDateTime()
        holder.binding.value.text = "${e1rm.wholeLb()} lb"
    }
}
