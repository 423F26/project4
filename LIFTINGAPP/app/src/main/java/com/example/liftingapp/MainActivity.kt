package com.example.liftingapp

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.liftingapp.databinding.ActivityMainBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: LiftViewModel
    private val exercises = listOf("Bench", "Squat", "Deadlift")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[LiftViewModel::class.java]

        setupSpinner()
        setupChart()
        setupFab()
        observeData()
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, exercises)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.exerciseSpinner.adapter = adapter

        binding.exerciseSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setExercise(exercises[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupChart() {
        binding.liftChart.apply {
            description.isEnabled = false
            axisRight.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            setTouchEnabled(true)
            setPinchZoom(true)
        }
    }

    private fun setupFab() {
        binding.addWeightFab.setOnClickListener { showAddWeightDialog() }
    }

    private fun showAddWeightDialog() {
        val input = EditText(this).apply {
            hint = "Weight (lbs)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        AlertDialog.Builder(this)
            .setTitle("Add ${binding.exerciseSpinner.selectedItem} weight")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                input.text.toString().toFloatOrNull()?.let { weight ->
                    val exercise = binding.exerciseSpinner.selectedItem as String
                    viewModel.addEntry(exercise, weight)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeData() {
        viewModel.entries.observe(this) { entries ->
            val chartEntries = entries.mapIndexed { index, lift -> Entry(index.toFloat(), lift.weight) }

            val dateFormat = SimpleDateFormat("M/d", Locale.getDefault())
            binding.liftChart.xAxis.valueFormatter =
                IndexAxisValueFormatter(entries.map { dateFormat.format(Date(it.date)) })

            val dataSet = LineDataSet(chartEntries, binding.exerciseSpinner.selectedItem as? String ?: "").apply {
                setDrawValues(false)
                lineWidth = 2f
                circleRadius = 4f
            }

            binding.liftChart.data = LineData(dataSet)
            binding.liftChart.invalidate()
        }
    }
}