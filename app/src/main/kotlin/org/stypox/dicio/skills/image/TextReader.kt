package org.stypox.dicio.skills.image

import android.graphics.Bitmap

/** Reads the text in an image, on the phone. */
interface TextReader {
    /** The text, top to bottom, or null when there's none or it can't be read. */
    suspend fun read(image: Bitmap): String?
}

object NoTextReader : TextReader {
    override suspend fun read(image: Bitmap): String? = null
}
