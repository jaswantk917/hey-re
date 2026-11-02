package org.stypox.dicio.io.input.shruti

import kotlin.math.max

/**
 * When someone started talking and when they're done, from how loud each slice of the microphone
 * is. Loud means well above the room's own noise, so a cooler or traffic doesn't count as speech.
 * The noise is the quietest the microphone has been lately: it drops at once to any quieter slice
 * (even the gaps between words, when someone talks from the first moment) and creeps back up.
 */
internal class Endpointer(private val silenceMillis: Int, private val maxWaitMillis: Int = MAX_WAIT_MILLIS) {
    enum class Heard { Waiting, Talking, Done, Nothing }

    private var noiseFloor = Float.NaN
    private var elapsedMillis = 0
    private var loudMillis = 0
    private var quietMillis = 0
    var speechBegan = false
        private set

    fun frame(rms: Float, millis: Int): Heard {
        elapsedMillis += millis
        val loud = !noiseFloor.isNaN() && rms > max(noiseFloor * SPEECH_OVER_NOISE, MIN_SPEECH_RMS)
        noiseFloor = if (noiseFloor.isNaN() || rms < noiseFloor) rms else noiseFloor + (rms - noiseFloor) * FLOOR_RISE
        if (loud) {
            loudMillis += millis
            quietMillis = 0
            // a cough or a click is not someone starting to talk
            if (loudMillis >= START_MILLIS) speechBegan = true
        } else {
            loudMillis = 0
            quietMillis += millis
        }
        return when {
            speechBegan && quietMillis >= silenceMillis -> Heard.Done
            speechBegan && elapsedMillis >= MAX_LISTEN_MILLIS -> Heard.Done
            !speechBegan && elapsedMillis >= maxWaitMillis -> Heard.Nothing
            speechBegan -> Heard.Talking
            else -> Heard.Waiting
        }
    }

    companion object {
        // the same 8 s the phone's recognizer is given for "should I call?" to be answered
        const val MAX_WAIT_MILLIS = 8_000
        const val MAX_LISTEN_MILLIS = 60_000
        private const val START_MILLIS = 200
        private const val SPEECH_OVER_NOISE = 3f
        private const val MIN_SPEECH_RMS = 0.01f
        private const val FLOOR_RISE = 0.02f
    }
}
