package org.stypox.dicio.skills.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * The image at [uri], scaled so its longer side is at most [MAX_SIDE]: enough for reading text
 * and for Nano, and small enough to keep in the conversation. Software-backed, as ML Kit needs.
 */
fun loadImage(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.let(::prepareImage)
    } else ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        scaledSize(info.size.width, info.size.height)?.let { (width, height) -> decoder.setTargetSize(width, height) }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
} catch (e: Exception) {
    Log.w("ImageLoading", "Could not open $uri", e)
    null
}

/** A camera preview bitmap, made software-backed and scaled the same way. */
fun prepareImage(bitmap: Bitmap): Bitmap {
    val scaled = scaledSize(bitmap.width, bitmap.height)
        ?.let { (width, height) -> Bitmap.createScaledBitmap(bitmap, width, height, true) }
        ?: bitmap
    return if (scaled.config == Bitmap.Config.HARDWARE) scaled.copy(Bitmap.Config.ARGB_8888, false) else scaled
}

/** The size to shrink a [width]×[height] image to, or null when it already fits. */
internal fun scaledSize(width: Int, height: Int): Pair<Int, Int>? {
    val longer = maxOf(width, height)
    if (longer <= MAX_SIDE) return null
    val scale = MAX_SIDE.toFloat() / longer
    return Pair((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
}

internal const val MAX_SIDE = 1536
