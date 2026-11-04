package com.example.liftingapp.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Work that should finish even if the screen that started it closes, like updating an athlete's
 * best set after a lift is saved. Lives as long as the app process.
 */
object AppScope : CoroutineScope by MainScope()

/** A one-time message: read it with [take] so a rotation doesn't show it twice. */
class Event<out T>(private val value: T) {
    private var taken = false
    fun take(): T? = if (taken) null else value.also { taken = true }
}

/**
 * Cloud write failures, wherever they happen. Writes aren't awaited (Firestore queues them and they
 * show up on screen right away, even offline), so a failure arrives later; screens observe
 * [messages] and show a snackbar.
 */
object CloudErrors {
    private val _messages = MutableLiveData<Event<String>>()
    val messages: LiveData<Event<String>> = _messages

    fun report(what: String, error: Throwable) {
        _messages.postValue(Event(describe(what, error)))
    }

    fun describe(what: String, error: Throwable): String = when ((error as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "You don't have permission to $what."
        FirebaseFirestoreException.Code.UNAVAILABLE -> "Couldn't $what. Check your connection."
        FirebaseFirestoreException.Code.NOT_FOUND -> "Couldn't $what. It may have been deleted."
        else -> (error as? TeamException)?.message ?: "Couldn't $what. Try again."
    }
}

/** Starts a cloud write without waiting for the server, and reports it if it fails. */
fun <T> Task<T>.orReport(what: String): Task<T> = addOnFailureListener { CloudErrors.report(what, it) }

/** Runs [block] in [AppScope], reporting any failure as "Couldn't [what]". */
fun inBackground(what: String, block: suspend () -> Unit): Job = AppScope.launch {
    try {
        block()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        CloudErrors.report(what, e)
    }
}
