package com.example.liftingapp.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.liftingapp.LiftViewModel
import com.example.liftingapp.R
import com.example.liftingapp.data.LeaderboardRow
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.launch
import com.google.android.material.R as MaterialR

/**
 * Tapping someone on the leaderboard opens their profile: a large picture, name, position and body
 * weight, followers (tap to see who) with a small Follow button, then their max lifts: a summary
 * (lifts logged, top estimated max, strongest lift for their weight) and a card per lift, strongest
 * first, with the best set, its date, estimated max and × body weight. It shows only what the
 * leaderboard already shares, so privacy settings carry over.
 *
 * Coaches (on their team) and staff (on General) also get Remove on each lift, for fake entries
 * like a 4,000 lb bench: it deletes the set that made that best (data/Moderation.kt).
 */
fun showAthleteProfile(context: Context, owner: LifecycleOwner, row: LeaderboardRow, viewModel: LiftViewModel, onMessage: (String) -> Unit) {
    fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()
    val probe = View(context)
    val muted = probe.themeColor(MaterialR.attr.colorOnSurfaceVariant)
    val ink = probe.themeColor(MaterialR.attr.colorOnSurface)
    val accent = probe.themeColor(androidx.appcompat.R.attr.colorPrimary)
    val cardFill = probe.themeColor(R.attr.cardFill)
    val cardStroke = probe.themeColor(R.attr.cardStroke)
    fun card(radius: Int = 16) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(cardFill)
        setStroke(dp(1), cardStroke)
    }
    fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value
        setTextAppearance(if (bold) MaterialR.style.TextAppearance_Material3_TitleMedium else MaterialR.style.TextAppearance_Material3_BodyMedium)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
    }
    val fullWidth = { LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }

    val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(20))
    }

    // --- Header: big picture, name, position · weight, followers, small Follow ---
    column.addView(ShapeableImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(120), dp(120))
        shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, dp(60).toFloat()).build()
        strokeColor = android.content.res.ColorStateList.valueOf(accent)
        strokeWidth = dp(3).toFloat()
        setPadding(dp(3))
        showAvatar(owner, row.photoUid, row.name)
    })
    column.addView(text(row.name, 24f, ink, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, 0)
    })
    val details = listOfNotNull(
        row.positionGroup.takeIf { it.isNotBlank() },
        if (row.bodyWeightLb > 0f) "${row.bodyWeightLb.lb()} lb" else "No body weight yet"
    ).joinToString(" · ")
    column.addView(text(details, 17f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0) })

    val account = row.accountUid
    if (account != null) {
        val followers = text("Followers…", 17f, ink).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            setOnClickListener {
                owner.lifecycleScope.launch {
                    val list = viewModel.followersOf(account) ?: return@launch
                    MaterialAlertDialogBuilder(context)
                        .setTitle(if (row.isMine) "Your followers" else "${row.name}'s followers")
                        .apply { if (list.isEmpty()) setMessage("No followers yet.") else setItems(list.map { it.name }.toTypedArray(), null) }
                        .setPositiveButton("Close", null)
                        .show()
                }
            }
        }
        column.addView(followers)
        owner.lifecycleScope.launch {
            val count = viewModel.followerCountOf(account)
            followers.text = when (count) {
                null -> "Followers"
                0 -> "No followers yet"
                1 -> "1 follower · see who"
                else -> "$count followers · see who"
            }
        }
        if (!row.isMine) {
            val isFollowing = viewModel.following.value?.contains(account) == true
            column.addView(MaterialButton(context, null, MaterialR.attr.materialButtonOutlinedStyle).apply {
                text = if (isFollowing) "Following" else "Follow"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                minHeight = 0
                minimumHeight = 0
                insetTop = 0
                insetBottom = 0
                setPadding(dp(16), dp(4), dp(16), dp(4))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply { topMargin = dp(8) }
                setOnClickListener {
                    val follow = text == "Follow"
                    viewModel.setFollowing(account, row.name, follow)
                    text = if (follow) "Following" else "Follow"
                }
            })
        }
    }

    // --- Max lifts ---
    val bests = row.allBests.entries.sortedByDescending { it.value.e1rm }
    column.addView(text("Max lifts", 20f, ink, bold = true).apply {
        layoutParams = fullWidth()
        setPadding(0, dp(22), 0, dp(8))
    })

    if (bests.isNotEmpty()) {
        // Summary: how many lifts, the top estimated max, and the strongest lift for their weight.
        val top = bests.first()
        val strongestForWeight = bests.maxByOrNull { it.value.p4p }?.takeIf { row.bodyWeightLb > 0f && it.value.p4p > 0f }
        val stats = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = card()
            setPadding(dp(12))
            layoutParams = fullWidth().apply { bottomMargin = dp(10) }
        }
        fun stat(value: String, label: String) = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(text(value, 20f, accent, bold = true).apply { gravity = Gravity.CENTER })
            addView(text(label, 12f, muted).apply { gravity = Gravity.CENTER })
        }
        stats.addView(stat("${bests.size}", if (bests.size == 1) "lift logged" else "lifts logged"))
        stats.addView(stat("${top.value.e1rm.wholeLb()} lb", "top est. max\n${top.key}"))
        if (strongestForWeight != null) {
            stats.addView(stat(strongestForWeight.value.p4p.timesBodyWeight(), "best for weight\n${strongestForWeight.key}"))
        }
        column.addView(stats)
    } else {
        column.addView(text("No lifts logged yet.", 15f, muted).apply { layoutParams = fullWidth() })
    }

    val canRemove = viewModel.canRemoveFromBoard(row)
    val dialog = MaterialAlertDialogBuilder(context)
        .setView(ScrollView(context).apply { addView(column) })
        .setPositiveButton("Close", null)
        .create()

    bests.forEachIndexed { index, (exercise, best) ->
        val liftCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = card()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = fullWidth().apply { bottomMargin = dp(8) }
        }
        // Rank among their own lifts, strongest first.
        liftCard.addView(text("${index + 1}", 15f, muted, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(dp(26), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        liftCard.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(text(exercise, 17f, ink, bold = true))
            addView(text("Best set ${best.weight.lb()} × ${best.reps} · ${best.date.monthDay()}", 13f, muted))
            if (row.bodyWeightLb > 0f && best.p4p > 0f) addView(text(best.p4p.timesBodyWeight(), 13f, muted))
        })
        liftCard.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(text("${best.e1rm.wholeLb()} lb", 20f, accent, bold = true).apply { gravity = Gravity.END })
            addView(text("est. max", 12f, muted).apply { gravity = Gravity.END })
            if (canRemove) {
                addView(TextView(context).apply {
                    text = "Remove"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(probe.themeColor(androidx.appcompat.R.attr.colorError))
                    setPadding(0, dp(6), 0, 0)
                    contentDescription = "Remove ${row.name}'s $exercise best"
                    setOnClickListener {
                        MaterialAlertDialogBuilder(context)
                            .setTitle("Remove ${row.name}'s $exercise best?")
                            .setMessage("This deletes the ${best.weight.lb()} × ${best.reps} set (est. ${best.e1rm.wholeLb()} lb). Their next-best $exercise set takes its place on the leaderboard. This can't be undone.")
                            .setPositiveButton("Remove") { _, _ ->
                                owner.lifecycleScope.launch {
                                    viewModel.removeFromBoard(row, exercise).onSuccess { newBest ->
                                        dialog.dismiss()
                                        onMessage(
                                            if (newBest == null) "Removed ${row.name}'s $exercise. They have no other $exercise sets."
                                            else "Removed ${row.name}'s ${best.weight.lb()} × ${best.reps} $exercise. Their best is now ${newBest.e1rm.wholeLb()} lb."
                                        )
                                    }
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                })
            }
        })
        column.addView(liftCard)
    }

    dialog.show()
}
