package org.stypox.dicio.io.input.shruti

import java.nio.ByteBuffer

/**
 * The needle engine's C API (cpp/shruti_jni.cpp). One model per process, and never two calls at
 * once: [ShrutiSttInputDevice] makes every call from its one thread.
 */
internal object Needle {
    // only arm64 builds carry the engine
    val isAvailable: Boolean by lazy {
        try {
            System.loadLibrary("shruti")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    /** [model] is a direct buffer that must never be freed. Negative on failure. */
    external fun load(model: ByteBuffer): Int

    external fun lastError(): String

    /** JSON {"text": ..., "language": ...} for the whole clip, or null on failure. */
    external fun transcribe(pcm: FloatArray, samples: Int, keywords: String?): String?

    /** JSON {"text": newly committed words, "pending": the unconfirmed tail}, or null. */
    external fun streamProcess(pcm: FloatArray, samples: Int, keywords: String?): String?

    /** Ends the live transcription, so the next [streamProcess] starts a new one. */
    external fun streamStop(): String?
}
