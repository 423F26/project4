package com.example.liftingapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a picked photo into a profile picture: upright (camera photos are often stored sideways
 * with a rotation tag), cropped to the middle square, [SIZE] pixels across, JPEG. Usually 15-30 KB.
 * Runs on a background thread (it decodes the whole photo).
 */
object AvatarImage {
    const val SIZE = 256
    private const val QUALITY = 82

    /** Null if the image can't be read. */
    fun prepare(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver

        // Read just the size first, then decode at roughly 2x the final size to save memory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }   // returns null by design
        val smallest = min(bounds.outWidth, bounds.outHeight)
        if (smallest <= 0) return null
        var sample = 1
        while (smallest / (sample * 2) >= SIZE * 2) sample *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        // Middle square, rotated upright and scaled to SIZE in one step.
        val side = min(decoded.width, decoded.height)
        val left = (decoded.width - side) / 2
        val top = (decoded.height - side) / 2
        val scale = SIZE.toFloat() / max(side, 1)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val square = Bitmap.createBitmap(decoded, left, top, side, side, matrix, true)
        if (square != decoded) decoded.recycle()

        val out = ByteArrayOutputStream()
        square.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        square.recycle()
        return out.toByteArray()
    }
}
