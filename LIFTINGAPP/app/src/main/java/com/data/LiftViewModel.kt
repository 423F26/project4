package com.example.lifttracker

import android.app.Application
import androidx.lifecycle.*
import com.example.lifttracker.data.LiftDatabase
import com.example.lifttracker.data.LiftEntry
import com.example.lifttracker.data.LiftRepository
import kotlinx.coroutines.launch

class LiftViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LiftRepository(LiftDatabase.getDatabase(application).liftDao())
    private val selectedExercise = MutableLiveData("Bench")

    val entries: LiveData<List<LiftEntry>> = selectedExercise.switchMap { exercise ->
        repository.getEntriesForExercise(exercise)
    }

    fun setExercise(exercise: String) {
        selectedExercise.value = exercise
    }

    fun addEntry(exercise: String, weight: Float) {
        viewModelScope.launch {
            repository.insert(LiftEntry(exercise = exercise, weight = weight))
        }
    }
}