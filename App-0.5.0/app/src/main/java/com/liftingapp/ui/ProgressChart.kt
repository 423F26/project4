package com.example.liftingapp.ui

import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.example.liftingapp.R
import com.example.liftingapp.data.ChartRange
import com.example.liftingapp.data.DAY_MS
import com.example.liftingapp.data.DayBest
import com.example.liftingapp.data.LiftEntry
import com.example.liftingapp.data.dailyBests
import com.example.liftingapp.data.localDay
import com.example.liftingapp.data.progressWindow
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import com.google.android.material.R as MaterialR

// ---- How "zoomed out" the chart is. Raise these for a flatter, wider view. ----

/** Empty space above and below the data, as a fraction of the best max (0.20 = 20%). */
private const val Y_HEADROOM = 0.20f

/** Smallest vertical padding in pounds, so light lifts still get breathing room. */
private const val Y_MIN_HEADROOM_LB = 20f

/** Pinch-zoom stops at this many days across. */
private const val MIN_VISIBLE_DAYS = 7f

/** Circles are hidden once there are more points than this, so long histories stay readable. */
private const val MAX_POINTS_WITH_CIRCLES = 45

/** Header numbers for the card above the chart. [gain] is null when only one day is in view. */
data class ProgressSummary(val best: DayBest, val gain: Float?, val since: String)

/** One-time styling. Colors come from the current theme, so this re-runs after a theme change. */
fun LineChart.setUpProgressChart() {
    val muted = themeColor(MaterialR.attr.colorOnSurfaceVariant)
    val onSurface = themeColor(MaterialR.attr.colorOnSurface)
    val grid = themeColor(MaterialR.attr.colorOutlineVariant)
    val font = ResourcesCompat.getFont(context, R.font.poppins_regular)

    description.isEnabled = false
    axisRight.isEnabled = false
    setTouchEnabled(true)
    isDragEnabled = true
    isDragYEnabled = false            // vertical swipes scroll the page instead
    isScaleXEnabled = true
    isScaleYEnabled = false           // the y-axis always stays zoomed out
    setPinchZoom(false)
    isDoubleTapToZoomEnabled = false  // accidental double taps were zooming the chart in
    setNoDataTextColor(muted)
    if (font != null) setNoDataTextTypeface(font)
    minOffset = 4f
    extraBottomOffset = 6f

    legend.apply {
        textColor = onSurface
        typeface = font
        textSize = 12f
        form = Legend.LegendForm.LINE
        formSize = 18f
        formLineWidth = 2.5f
        xEntrySpace = 18f
        verticalAlignment = Legend.LegendVerticalAlignment.TOP
        horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
        orientation = Legend.LegendOrientation.HORIZONTAL
        setDrawInside(false)
    }

    xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        textColor = muted
        typeface = font
        textSize = 11f
        yOffset = 8f
        setDrawGridLines(false)
        axisLineColor = grid
        granularity = 1f
        isGranularityEnabled = true
        setAvoidFirstLastClipping(true)
        setLabelCount(5, false)
    }

    axisLeft.apply {
        textColor = muted
        typeface = font
        textSize = 11f
        xOffset = 8f
        gridColor = grid
        setDrawAxisLine(false)
        enableGridDashedLine(8f, 8f, 0f)
        setLabelCount(6, false)
        granularity = 5f
        isGranularityEnabled = true
        setDrawLimitLinesBehindData(true)
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = value.roundToInt().toString()
        }
    }

    marker = ProgressMarker(context).also { it.chartView = this }
}

/**
 * Plots one athlete's progress for one lift: the best estimated max per training day
 * (solid, filled) and the heaviest weight moved that day (dashed), on a real time axis.
 * Resets any pinch-zoom so every redraw starts fully zoomed out.
 *
 * Returns the numbers for the card header, or null when nothing falls inside [range]
 * (the caller then shows the no-data text).
 */
fun LineChart.showProgress(entries: List<LiftEntry>, range: ChartRange): ProgressSummary? {
    val window = progressWindow(dailyBests(entries), range, today = localDay(System.currentTimeMillis()))
    val shown = window.days
    if (shown.isEmpty()) return null
    val start = window.startDay

    // X: days since the start of the window, padded so edge points aren't clipped.
    val span = window.spanDays.toFloat()
    val xPad = max(0.6f, span * 0.03f)
    xAxis.axisMinimum = -xPad
    xAxis.axisMaximum = span + xPad
    val labelFormat = dayFormat(if (span > 300f) "MMM ''yy" else "MMM d")
    xAxis.valueFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String = labelFormat.formatDay(start + value.roundToLong())
    }

    // Y: padded well past the data and snapped to round numbers, so small changes
    // don't look like huge swings.
    val low = shown.minOf { it.heaviest }
    val high = shown.maxOf { it.bestE1rm }
    val headroom = max(high * Y_HEADROOM, Y_MIN_HEADROOM_LB)
    val step = if (high >= 400f) 50f else 25f
    axisLeft.axisMinimum = max(0f, floor((low - headroom) / step) * step)
    axisLeft.axisMaximum = ceil((high + headroom * 0.6f) / step) * step

    val primary = themeColor(MaterialR.attr.colorOnPrimary)
    val secondary = themeColor(MaterialR.attr.colorSecondary)
    val hole = themeColor(MaterialR.attr.colorSurface)
    val labelFont = ResourcesCompat.getFont(context, R.font.poppins_medium)
    val showCircles = shown.size <= MAX_POINTS_WITH_CIRCLES

    val heaviestLine = LineDataSet(
        shown.map { Entry((it.day - start).toFloat(), it.heaviest, it) },
        "Heaviest set"
    ).apply {
        color = secondary
        lineWidth = 1.5f
        enableDashedLine(10f, 8f, 0f)
        formLineDashEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        setDrawCircles(showCircles)
        setCircleColor(secondary)
        circleRadius = 2.5f
        setDrawCircleHole(false)
        setDrawValues(false)
        isHighlightEnabled = false   // taps go to the est. max line; its tooltip shows both
    }

    val maxLine = LineDataSet(
        shown.map { Entry((it.day - start).toFloat(), it.bestE1rm, it) },
        "Est. max"
    ).apply {
        color = primary
        lineWidth = 2.5f
        setDrawCircles(showCircles)
        setCircleColor(primary)
        circleRadius = 4f
        circleHoleRadius = 2f
        circleHoleColor = hole
        setDrawCircleHole(true)
        setDrawValues(false)
        setDrawFilled(true)
        fillDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(ColorUtils.setAlphaComponent(primary, 80), Color.TRANSPARENT)
        )
        highLightColor = ColorUtils.setAlphaComponent(primary, 160)
        highlightLineWidth = 1f
        setDrawHorizontalHighlightIndicator(false)
    }

    // Dashed reference line at the best max in view
    val best = shown.maxBy { it.bestE1rm }
    axisLeft.removeAllLimitLines()
    axisLeft.addLimitLine(LimitLine(best.bestE1rm, "Best ${best.bestE1rm.wholeLb()}").apply {
        lineColor = ColorUtils.setAlphaComponent(primary, 140)
        lineWidth = 1f
        enableDashedLine(6f, 6f, 0f)
        textColor = primary
        textSize = 10f
        typeface = labelFont
        labelPosition = LimitLine.LimitLabelPosition.LEFT_TOP
    })

    data = LineData(heaviestLine, maxLine)
    highlightValues(null)
    fitScreen()                                              // undo any leftover pinch-zoom
    setVisibleXRangeMinimum(min(MIN_VISIBLE_DAYS, span + 2 * xPad))
    invalidate()

    val first = shown.first()
    return ProgressSummary(
        best = best,
        gain = if (shown.size >= 2) best.bestE1rm - first.bestE1rm else null,
        since = dayFormat("MMM d").formatDay(first.day)
    )
}

/** Formats day numbers from localDay(). UTC, because the day number already has the local offset baked in. */
private fun dayFormat(pattern: String) =
    SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }

private fun SimpleDateFormat.formatDay(day: Long): String = format(Date(day * DAY_MS))

/** Tooltip for a tapped point: date, est. max (and the set it came from), heaviest set. */
private class ProgressMarker(context: Context) : MarkerView(context, R.layout.chart_marker) {

    private val text: TextView = findViewById(R.id.markerText)
    private val dateFormat = dayFormat("EEE, MMM d, yyyy")

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        val day = e?.data as? DayBest
        if (day != null) {
            val set = day.bestSet
            text.text = buildString {
                append(dateFormat.formatDay(day.day))
                append("\nEst. max ${day.bestE1rm.wholeLb()} lb (${set.weight.lb()} × ${set.reps})")
                if (day.heaviest > set.weight) append("\nHeaviest set ${day.heaviest.lb()} lb")
            }
        }
        super.refreshContent(e, highlight)   // re-measures to fit the new text
    }

    override fun getOffset(): MPPointF = MPPointF(-width / 2f, -height - 16f)
}
