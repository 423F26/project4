package com.example.liftingapp.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.example.liftingapp.LeaderboardState
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.R
import com.example.liftingapp.data.EXERCISES
import com.example.liftingapp.data.RankBy
import com.example.liftingapp.databinding.ItemLeaderboardBinding
import com.example.liftingapp.databinding.TabLeaderboardBinding
import com.google.android.material.R as MaterialR

/**
 * Ranks by *estimated* max (so any rep range counts), with a toggle for pound-for-pound.
 */
class LeaderboardTab(owner: LifecycleOwner, binding: TabLeaderboardBinding, viewModel: LiftViewModel) {

    private val adapter = LeaderboardAdapter()

    init {
        val context = binding.root.context

        binding.exerciseSpinner.adapter = spinnerAdapter(context, EXERCISES)
        binding.exerciseSpinner.setSelection(EXERCISES.indexOf(viewModel.currentLeaderboardExercise).coerceAtLeast(0), false)
        binding.exerciseSpinner.onItemSelected { position -> viewModel.setLeaderboardExercise(EXERCISES[position]) }

        binding.rankToggle.check(
            if (viewModel.currentRankBy == RankBy.POUND_FOR_POUND) R.id.rankPoundForPound else R.id.rankEstimatedMax
        )
        binding.rankToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                viewModel.setRankBy(if (checkedId == R.id.rankPoundForPound) RankBy.POUND_FOR_POUND else RankBy.ESTIMATED_MAX)
            }
        }

        binding.leaderboardList.adapter = adapter
        viewModel.leaderboard.observe(owner) { state ->
            adapter.submit(state)
            binding.emptyText.text = "No ${state.exercise} sets logged yet."
            binding.emptyText.visibility = if (state.rows.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}

private class LeaderboardAdapter : RecyclerView.Adapter<LeaderboardAdapter.Holder>() {

    class Holder(val binding: ItemLeaderboardBinding) : RecyclerView.ViewHolder(binding.root)

    /** Rank-badge fill and text colors: gold for 1st, tinted for 2nd-3rd, subtle for the rest. */
    private class BadgeColors(val first: Pair<Int, Int>, val podium: Pair<Int, Int>, val rest: Pair<Int, Int>)
    private var badges: BadgeColors? = null

    private var state = LeaderboardState(EXERCISES.first(), RankBy.ESTIMATED_MAX, emptyList())

    @SuppressLint("NotifyDataSetChanged")   // ranking mode can change every row's value, so redraw all
    fun submit(newState: LeaderboardState) {
        state = newState
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        if (badges == null) {
            badges = BadgeColors(
                first = parent.themeColor(MaterialR.attr.colorOnPrimary) to parent.themeColor(MaterialR.attr.colorOnPrimary),
                podium = parent.themeColor(MaterialR.attr.colorSecondaryContainer) to
                    parent.themeColor(MaterialR.attr.colorOnSecondaryContainer),
                rest = parent.themeColor(R.attr.cardStroke) to parent.themeColor(MaterialR.attr.colorOnSurfaceVariant)
            )
        }
        return Holder(ItemLeaderboardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = state.rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = state.rows[position]
        holder.binding.rank.text = "${position + 1}"
        badges?.let { colors ->
            val (fill, ink) = when (position) {
                0 -> colors.first
                1, 2 -> colors.podium
                else -> colors.rest
            }
            holder.binding.rank.backgroundTintList = ColorStateList.valueOf(fill)
            holder.binding.rank.setTextColor(ink)
        }
        holder.binding.name.text = row.athlete.label()
        holder.binding.detail.text =
            "Best set ${row.bestSet.weight.lb()} × ${row.bestSet.reps} · BW ${row.athlete.bodyWeightLb.lb()} lb"
        holder.binding.value.text = when (state.rankBy) {
            RankBy.ESTIMATED_MAX -> "${row.estimatedMax.wholeLb()} lb"
            RankBy.POUND_FOR_POUND -> row.poundForPound.timesBodyWeight()
        }
    }
}
