package com.example.liftingapp.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 225.0 -> "225", 227.5 -> "227.5" */
fun Float.lb(): String =
    if (this % 1f == 0f) this.toInt().toString() else String.format(Locale.US, "%.1f", this)

/** Estimated maxes are shown rounded to the nearest pound. */
fun Float.wholeLb(): String = this.roundToInt().toString()

/** 1.4567 -> "1.46× BW" */
fun Float.timesBodyWeight(): String = String.format(Locale.US, "%.2f× BW", this)

fun Long.shortDateTime(): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))

/** Month and day only, e.g. "Sep 28". */
fun Long.monthDay(): String =
    java.text.SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(this))

/** Time of day only, e.g. "3:42 PM". */
fun Long.shortTime(): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(this))

/** "Jordan Smith (OL)" — how athletes appear in spinners and the leaderboard. */
fun com.example.liftingapp.data.Athlete.label(): String = "$name ($positionGroup)"
