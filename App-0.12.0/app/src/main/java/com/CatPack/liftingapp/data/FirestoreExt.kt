package com.example.liftingapp.data

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * Small bridges from Firebase's callback APIs to coroutines, so repositories can use
 * suspend functions and Flows (the same shape Room gave the rest of the app).
 * Written here instead of pulling in kotlinx-coroutines-play-services.
 */

/** Suspends until the task finishes; throws its exception if it failed. */
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> cont.resumeWithException(error)
            task.isCanceled -> cont.cancel()
            else -> cont.resume(task.result)
        }
    }
}

/**
 * Live query results. Emits the cached results first (so the screen fills in offline),
 * then every change, including this phone's own writes before the server confirms them.
 * The listener is removed when the collector stops.
 */
fun Query.changes(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

/** Live single document. Same behavior as [Query.changes]. */
fun DocumentReference.changes(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

// --- Reading loosely typed Firestore values (numbers come back as Long or Double) ---

internal fun Any?.asFloat(default: Float = 0f): Float = (this as? Number)?.toFloat() ?: default
internal fun Any?.asInt(default: Int = 0): Int = (this as? Number)?.toInt() ?: default
internal fun Any?.asLong(default: Long = 0L): Long = (this as? Number)?.toLong() ?: default
internal fun Any?.asBool(default: Boolean): Boolean = (this as? Boolean) ?: default
internal fun Any?.asStringOrNull(): String? = (this as? String)?.takeIf { it.isNotEmpty() }
