package com.example.liftingapp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.liftingapp.LeaderboardState
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.R
import com.example.liftingapp.data.EXERCISES
import com.example.liftingapp.data.GeneralBoard
import com.example.liftingapp.data.LeaderboardRow
import com.example.liftingapp.data.RankBy
import com.example.liftingapp.data.Scope
import com.example.liftingapp.databinding.ItemLeaderboardBinding
import com.example.liftingapp.databinding.TabLeaderboardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * Ranks by *estimated* max (so any rep range counts), with a toggle for pound-for-pound.
 *  - On a team: the team's roster.
 *  - General use: everyone using the app on their own who chose to appear (Account & settings → Privacy),
 *    the top GeneralBoard.LIMIT for the exercise.
 * The search field filters the list by name (ranks stay as on the full board). Tapping a row, or your
 * own follower count, shows who follows that person; followers are public to signed-in users.
 */
class LeaderboardTab(private val owner: LifecycleOwner, private val binding: TabLeaderboardBinding, private val viewModel: LiftViewModel) {

    private val adapter = LeaderboardAdapter(
        owner,
        onFollow = { targetUid, name, follow -> viewModel.setFollowing(targetUid, name, follow) },
        onShowFollowers = { targetUid, name -> showFollowers(targetUid, "$name's followers") }
    )

    private var latest = LeaderboardState(EXERCISES.first(), RankBy.ESTIMATED_MAX, emptyList())

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
        binding.boardSearchInput.doAfterTextChanged { showRows() }
        viewModel.leaderboard.observe(owner) { state ->
            latest = state
            showRows()
            binding.boardSubtitle.text = when (scope) {
                is Scope.Team -> "Everyone on ${scope.name}"
                Scope.General ->
                    if (state.rows.size >= GeneralBoard.LIMIT) "Top ${GeneralBoard.LIMIT} of everyone using the app on their own"
                    else "Everyone using the app on their own"
            }
        }
        viewModel.following.observe(owner) { adapter.submitFollowing(it) }
        viewModel.followerCount.observe(owner) { count ->
            binding.followerCountText.text = when (count) {
                0 -> "No followers yet"
                1 -> "1 follower"
                else -> "$count followers"
            }
        }
        binding.followerCountText.setOnClickListener { showFollowers(viewModel.uid, "Your followers") }
        viewModel.profile.observe(owner) { profile ->
            binding.hiddenNotice.isVisible = scope == Scope.General && !profile.showOnGeneral
        }
    }

    /** The board filtered by the search field; each row keeps its rank on the full board. */
    private fun showRows() {
        val query = binding.boardSearchInput.text?.toString()?.trim().orEmpty()
        val ranked = latest.rows.mapIndexed { index, row -> index + 1 to row }
        val shown = if (query.isEmpty()) ranked else ranked.filter { (_, row) -> row.name.contains(query, ignoreCase = true) }
        adapter.submit(latest.rankBy, shown)
        val scope = viewModel.scope
        binding.emptyText.text = when {
            latest.rows.isNotEmpty() -> when (scope) {
                is Scope.Team -> "No one on ${scope.name} matches \"$query\"."
                Scope.General -> "No one matching \"$query\" is on the General leaderboard for ${latest.exercise}."
            }
            scope is Scope.Team -> "No ${latest.exercise} sets logged on ${scope.name} yet."
            else -> "No one on the General leaderboard has logged ${latest.exercise} yet."
        }
        binding.emptyText.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showFollowers(targetUid: String, title: String) {
        val context = binding.root.context
        owner.lifecycleScope.launch {
            val followers = viewModel.followersOf(targetUid) ?: return@launch
            showFollowersDialog(context, title, followers.map { it.name })
        }
    }
}

private fun showFollowersDialog(context: Context, title: String, names: List<String>) {
    val builder = MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setPositiveButton("Close", null)
    if (names.isEmpty()) builder.setMessage("No followers yet.")
    else builder.setItems(names.toTypedArray(), null)
    builder.show()
}

private class LeaderboardAdapter(
    private val owner: LifecycleOwner,
    private val onFollow: (targetUid: String, name: String, follow: Boolean) -> Unit,
    private val onShowFollowers: (targetUid: String, name: String) -> Unit
) : RecyclerView.Adapter<LeaderboardAdapter.Holder>() {

    /** Accounts the signed-in person follows. */
    private var following: Set<String> = emptySet()

    @SuppressLint("NotifyDataSetChanged")
    fun submitFollowing(set: Set<String>) {
        following = set
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemLeaderboardBinding) : RecyclerView.ViewHolder(binding.root)

    /** Rank-badge fill and text colors: gold for 1st, tinted for 2nd-3rd, subtle for the rest. */
    private class BadgeColors(val first: Pair<Int, Int>, val podium: Pair<Int, Int>, val rest: Pair<Int, Int>)
    private var badges: BadgeColors? = null

    private var rankBy = RankBy.ESTIMATED_MAX
    /** (rank on the full board, row), already filtered by the search field. */
    private var rows: List<Pair<Int, LeaderboardRow>> = emptyList()

    @SuppressLint("NotifyDataSetChanged")   // ranking mode or search can change every row, so redraw all
    fun submit(newRankBy: RankBy, newRows: List<Pair<Int, LeaderboardRow>>) {
        rankBy = newRankBy
        rows = newRows
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

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val (rank, row) = rows[position]
        val b = holder.binding
        b.rank.text = "$rank"
        badges?.let { colors ->
            val (fill, ink) = when (rank) {
                1 -> colors.first
                2, 3 -> colors.podium
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
        b.value.text = when (rankBy) {
            RankBy.ESTIMATED_MAX -> "${row.estimatedMax.wholeLb()} lb"
            RankBy.POUND_FOR_POUND -> if (hasBodyWeight) row.poundForPound.timesBodyWeight() else "—"
        }

        // Tap anyone with an account to see who follows them (followers are public).
        val account = row.accountUid
        if (account != null) {
            b.root.setOnClickListener { onShowFollowers(account, row.name) }
        } else {
            b.root.setOnClickListener(null)
            b.root.isClickable = false
        }

        // Follow anyone with an account except yourself.
        val target = account?.takeIf { !row.isMine }
        b.followButton.isVisible = target != null
        if (target != null) {
            val isFollowing = target in following
            b.followButton.text = if (isFollowing) "Following" else "Follow"
            b.followButton.contentDescription = if (isFollowing) "Unfollow ${row.name}" else "Follow ${row.name}"
            b.followButton.setOnClickListener { onFollow(target, row.name, !isFollowing) }
        } else {
            b.followButton.setOnClickListener(null)
        }
    }
}
