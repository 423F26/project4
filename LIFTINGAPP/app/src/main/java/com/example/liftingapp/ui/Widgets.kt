package com.example.liftingapp.ui

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.content.ContextCompat
import com.example.liftingapp.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.LineDataSet

/** Spinner adapter with bone text on the grey wash (android.R.layout.simple_spinner_item would draw default text colors). */
fun boneSpinnerAdapter(context: Context, items: List<String>): ArrayAdapter<String> =
    ArrayAdapter(context, R.layout.item_spinner, items.toMutableList()).apply {
        setDropDownViewResource(R.layout.item_spinner_dropdown)
    }

/** Shorter way to write OnItemSelectedListener object. */
fun Spinner.onItemSelected(action: (position: Int) -> Unit) {
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = action(position)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
}

/** setupChart() settings, plus bone-colored text and faint grid lines for the dark background. */
fun LineChart.applyWashStyle() {
    val bone = ContextCompat.getColor(context, R.color.bone)
    val muted = ContextCompat.getColor(context, R.color.bone_muted)
    val grid = ContextCompat.getColor(context, R.color.bone_line)

    description.isEnabled = false
    axisRight.isEnabled = false
    setTouchEnabled(true)
    setPinchZoom(true)
    setNoDataTextColor(muted)
    legend.textColor = bone

    xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        granularity = 1f
        textColor = muted
        gridColor = grid
        axisLineColor = muted
    }
    axisLeft.apply {
        textColor = muted
        gridColor = grid
        axisLineColor = muted
    }
}

fun LineDataSet.styled(color: Int, dashed: Boolean = false): LineDataSet = apply {
    this.color = color
    setCircleColor(color)
    setDrawCircleHole(false)
    setDrawValues(false)
    highLightColor = color
    lineWidth = 2f
    circleRadius = 3.5f
    if (dashed) enableDashedLine(12f, 8f, 0f)
}
