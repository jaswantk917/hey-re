package org.stypox.dicio.skills.image

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * ML Kit's on-device text recognition through Play services. The Devanagari model reads English
 * too, so one pass covers a Hindi notice and an English receipt alike.
 */
class MlKitTextReader : TextReader {
    private val recognizer by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }

    override suspend fun read(image: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(image, 0))
            .addOnSuccessListener { continuation.resume(it.text.trim().takeIf { text -> text.isNotEmpty() }) }
            .addOnFailureListener {
                Log.w("MlKitTextReader", "Could not read the image", it)
                continuation.resume(null)
            }
    }
}
