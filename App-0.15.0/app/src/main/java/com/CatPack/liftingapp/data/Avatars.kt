package com.example.liftingapp.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Profile pictures. Each is a small square JPEG (see ui/AvatarImage.kt) stored as base64 in
 * avatars/{uid}, about 15-30 KB. That fits comfortably in a Firestore document, so pictures need no
 * Cloud Storage bucket (which now requires the paid Firebase plan).
 *
 * [visible] mirrors the "Show my profile photo" privacy switch; the rules only let other people
 * read a picture while it's on.
 */
object Avatars {

    /** Decoded pictures by uid. Null values ("has no picture") are tracked in [missing]. */
    private val cache = LruCache<String, Bitmap>(48)
    private val missing = HashSet<String>()

    fun cached(uid: String): Bitmap? = cache.get(uid)

    fun isKnownMissing(uid: String): Boolean = synchronized(missing) { uid in missing }

    /** The picture for [uid], or null if they have none, hide it, or it can't be loaded right now. */
    suspend fun load(uid: String): Bitmap? {
        cache.get(uid)?.let { return it }
        if (isKnownMissing(uid)) return null
        val result = runCatching { CloudPaths.avatar(uid).get().awaitResult() }
        val snapshot = result.getOrNull()
        if (snapshot == null) {
            // Hidden pictures are refused by the rules; don't ask again this session. Offline: try later.
            val denied = (result.exceptionOrNull() as? FirebaseFirestoreException)?.code ==
                FirebaseFirestoreException.Code.PERMISSION_DENIED
            if (denied) synchronized(missing) { missing += uid }
            return null
        }
        val data = snapshot.getString("data")
        val bitmap = if (data.isNullOrEmpty()) null else withContext(Dispatchers.Default) { decode(data) }
        if (bitmap == null) synchronized(missing) { missing += uid } else cache.put(uid, bitmap)
        return bitmap
    }

    /** Saves the signed-in person's new picture ([jpeg] from AvatarImage.prepare) and shows it at once. */
    suspend fun upload(uid: String, jpeg: ByteArray, visible: Boolean) {
        val encoded = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        CloudPaths.avatar(uid).set(
            mapOf("data" to encoded, "visible" to visible, "updatedAt" to System.currentTimeMillis())
        ).awaitResult()
        withContext(Dispatchers.Default) { decode(encoded) }?.let { cache.put(uid, it) }
        synchronized(missing) { missing -= uid }
    }

    suspend fun remove(uid: String) {
        CloudPaths.avatar(uid).delete().awaitResult()
        forget(uid)
        synchronized(missing) { missing += uid }
    }

    /** Privacy switch: whether other people can see the picture. Does nothing if there's no picture yet. */
    suspend fun setVisible(uid: String, visible: Boolean) {
        val ref = CloudPaths.avatar(uid)
        if (ref.get().awaitResult().exists()) ref.set(mapOf("visible" to visible), SetOptions.merge()).awaitResult()
    }

    fun forget(uid: String) {
        cache.remove(uid)
        synchronized(missing) { missing -= uid }
    }

    private fun decode(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
