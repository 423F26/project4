package com.example.liftingapp.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.liftingapp.R
import com.example.liftingapp.data.Avatars
import kotlinx.coroutines.launch
import com.google.android.material.R as MaterialR

/**
 * Shows [uid]'s profile picture in this (round) ImageView, with their initials until it loads or
 * if they have none. Safe in RecyclerView rows: a row that was reused for someone else in the
 * meantime keeps its new picture.
 */
fun ImageView.showAvatar(owner: LifecycleOwner, uid: String?, name: String) {
    tag = uid
    val cached = uid?.let { Avatars.cached(it) }
    if (cached != null) {
        setImageBitmap(cached)
        return
    }
    setImageDrawable(InitialsDrawable(this, name))
    if (uid == null || Avatars.isKnownMissing(uid)) return
    owner.lifecycleScope.launch {
        val bitmap = Avatars.load(uid)
        if (bitmap != null && tag == uid) setImageBitmap(bitmap)
    }
}

/** "Jordan Smith" -> "JS", "Jordan" -> "J". */
fun initialsForAvatar(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "?" }

/** A filled circle with initials in the theme's secondary container colors. */
private class InitialsDrawable(view: ImageView, name: String) : Drawable() {
    private val text = initialsForAvatar(name)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = view.themeColor(MaterialR.attr.colorSecondaryContainer) }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = view.themeColor(MaterialR.attr.colorOnSecondaryContainer)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(view.context, R.font.poppins_medium) ?: Typeface.DEFAULT_BOLD
    }
    private val textBounds = Rect()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val radius = minOf(b.width(), b.height()) / 2f
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), radius, fill)
        ink.textSize = radius * if (text.length > 1) 0.78f else 0.9f
        ink.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, b.exactCenterX(), b.exactCenterY() + textBounds.height() / 2f, ink)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        ink.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        ink.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
