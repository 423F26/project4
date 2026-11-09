package com.example.liftingapp.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.example.liftingapp.LeaderboardState
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.R
import com.example.liftingapp.data.EXERCISES
import com.example.liftingapp.data.GeneralBoard
import com.example.liftingapp.data.RankBy
import com.example.liftingapp.data.Scope
import com.example.liftingapp.databinding.ItemLeaderboardBinding
import com.example.liftingapp.databinding.TabLeaderboardBinding
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * Ranks by *estimated* max (so any rep range counts), with a toggle for pound-for-pound.
 *  - On a team: the team's roster.
 *  - General use: everyone using the app on their own who chose to appear (Account & settings → Privacy),
 *    the top GeneralBoard.LIMIT for the exercise.
 */
class LeaderboardTab(private val owner: LifecycleOwner, binding: TabLeaderboardBinding, private val viewModel: LiftViewModel) {

    private val adapter = LeaderboardAdapter(owner)

    init {
        val context = binding.root.context
        val scope = viewModel.scope

        binding.boardTitle.text = when (scope) {
            is Scope.Team -> "${scope.name} leaderboard"
            Scope.General -> "General leaderboard"
        }
        binding.boardSubtitle.text = when (scope) {
            is Scope.Team -> "Everyone on ${scope.name}"
            Scope.General -> "Everyone using the app on their own"
        }

        binding.boardExerciseField.text = viewModel.currentLeaderboardExercise
        binding.boardExerciseField.setOnClickListener {
            showExercisePicker(
                context = context,
                catalog = viewModel.currentCatalog,
                custom = viewModel.customExercises,
                current = viewModel.currentLeaderboardExercise,
                onAdd = null,   // you can only rank exercises someone has logged
                onPick = { exercise ->
                    viewModel.setLeaderboardExercise(exercise)
                    binding.boardExerciseField.text = exercise
                }
            )
        }

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
            binding.emptyText.text = when (scope) {
                is Scope.Team -> "No ${state.exercise} sets logged on ${scope.name} yet."
                Scope.General -> "No one on the General leaderboard has logged ${state.exercise} yet."
            }
            binding.emptyText.visibility = if (state.rows.isEmpty()) View.VISIBLE else View.GONE
            binding.boardSubtitle.text = when (scope) {
                is Scope.Team -> "Everyone on ${scope.name}"
                Scope.General ->
                    if (state.rows.size >= GeneralBoard.LIMIT) "Top ${GeneralBoard.LIMIT} of everyone using the app on their own"
                    else "Everyone using the app on their own"
            }
        }
        viewModel.profile.observe(owner) { profile ->
            binding.hiddenNotice.isVisible = scope == Scope.General && !profile.showOnGeneral
        }
    }
}

private class LeaderboardAdapter(private val owner: LifecycleOwner) : RecyclerView.Adapter<LeaderboardAdapter.Holder>() {

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
                first = parent.themeColor(AppCompatR.attr.colorPrimary) to parent.themeColor(MaterialR.attr.colorOnPrimary),
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
        val b = holder.binding
        b.rank.text = "${position + 1}"
        badges?.let { colors ->
            val (fill, ink) = when (position) {
                0 -> colors.first
                1, 2 -> colors.podium
                else -> colors.rest
            }
            b.rank.backgroundTintList = ColorStateList.valueOf(fill)
            b.rank.setTextColor(ink)
        }
        b.avatar.showAvatar(owner, row.photoUid, row.name)
        b.name.text = if (row.positionGroup.isBlank()) row.name else "${row.name} (${row.positionGroup})"
        b.youBadge.isVisible = row.isMine
        val hasBodyWeight = row.bodyWeightLb > 0f
        b.detail.text = "Best set ${row.best.weight.lb()} × ${row.best.reps}" +
            if (hasBodyWeight) " · BW ${row.bodyWeightLb.lb()} lb" else " · No body weight yet"
        b.value.text = when (state.rankBy) {
            RankBy.ESTIMATED_MAX -> "${row.estimatedMax.wholeLb()} lb"
            RankBy.POUND_FOR_POUND -> if (hasBodyWeight) row.poundForPound.timesBodyWeight() else "—"
        }
    }
}
