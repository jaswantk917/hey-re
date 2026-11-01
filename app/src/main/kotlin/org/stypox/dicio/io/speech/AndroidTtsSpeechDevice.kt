package org.stypox.dicio.io.speech

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import org.dicio.skill.context.SpeechOutputDevice
import org.stypox.dicio.R
import java.util.Locale

class AndroidTtsSpeechDevice(
    private var context: Context,
    private val locale: Locale,
) : SpeechOutputDevice {
    private var textToSpeech: TextToSpeech? = null
    private var initializedCorrectly = false
    private val runnablesWhenFinished: MutableList<Runnable> = ArrayList()
    private var lastUtteranceId = 0

    init {
        textToSpeech = TextToSpeech(context) { status: Int ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.run {
                    val errorCode = setLanguage(locale)
                    if (errorCode >= 0) { // errors are -1 or -2
                        initializedCorrectly = true
                        // the system's assistant stream, so its volume and ducking are the assistant's
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        defaultVoice(this)?.let { voice = it }
                        setOnUtteranceProgressListener(object :
                            UtteranceProgressListener() {
                            override fun onStart(utteranceId: String) {}
                            override fun onDone(utteranceId: String) {
                                if ("dicio_$lastUtteranceId" == utteranceId) {
                                    // run only when the last enqueued utterance is finished
                                    for (runnable in runnablesWhenFinished) {
                                        runnable.run()
                                    }
                                    runnablesWhenFinished.clear()
                                }
                            }

                            @Suppress("OVERRIDE_DEPRECATION")
                            @Deprecated("")
                            override fun onError(utteranceId: String) {
                            }
                        })
                    } else {
                        Log.e(TAG, "Unsupported language: $errorCode")
                        handleInitializationError(R.string.android_tts_unsupported_language)
                    }
                }
            } else {
                Log.e(TAG, "TTS error: $status")
                handleInitializationError(R.string.android_tts_error)
            }
        }
    }

    override fun speak(speechOutput: String) {
        if (initializedCorrectly) {
            lastUtteranceId += 1
            textToSpeech?.let { tts -> voiceFor(tts, speechOutput)?.let { tts.voice = it } }
            textToSpeech?.speak(
                speechOutput, TextToSpeech.QUEUE_ADD, null,
                "dicio_$lastUtteranceId"
            )
        } else {
            Toast.makeText(context, speechOutput, Toast.LENGTH_LONG).show()
        }
    }

    override fun stopSpeaking() {
        textToSpeech?.stop()
    }

    override val isSpeaking: Boolean
        get() = textToSpeech?.isSpeaking == true

    override fun runWhenFinishedSpeaking(runnable: Runnable) {
        if (isSpeaking) {
            runnablesWhenFinished.add(runnable)
        } else {
            runnable.run()
        }
    }

    override fun cleanup() {
        textToSpeech?.apply {
            shutdown()
            textToSpeech = null
        }
    }

    /**
     * Hinglish replies are mostly Roman script, which an Indian English voice reads naturally and a
     * Hindi voice mangles; Devanagari text gets the Hindi voice.
     */
    private fun voiceFor(tts: TextToSpeech, text: String): Voice? {
        if (locale.language != "hi") return null
        val hasDevanagari = text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.DEVANAGARI }
        return if (hasDevanagari) bestVoice(tts, Locale("hi", "IN")) else defaultVoice(tts)
    }

    private fun defaultVoice(tts: TextToSpeech): Voice? =
        bestVoice(tts, if (locale.language == "hi") Locale("en", "IN") else locale)

    private fun handleInitializationError(@StringRes errorString: Int) {
        Toast.makeText(context, errorString, Toast.LENGTH_SHORT).show()
        cleanup()
    }

    companion object {
        val TAG: String = AndroidTtsSpeechDevice::class.simpleName!!

        /**
         * The best installed offline voice for [target]: on Pixels these are Google's neural
         * voices, which the engine doesn't always pick by default.
         */
        private fun bestVoice(tts: TextToSpeech, target: Locale): Voice? =
            tts.voices.orEmpty()
                .filter {
                    it.locale.language == target.language &&
                            (target.country.isEmpty() || it.locale.country == target.country) &&
                            isUsable(it)
                }
                // all of Google's voices claim the top quality, so without a region in the app's
                // language the accent was a coin toss: prefer the most common one
                .maxWithOrNull(
                    compareBy<Voice> { it.quality }
                        .thenBy { PREFERRED_COUNTRIES.size - PREFERRED_COUNTRIES.indexOf(it.locale.country).let { i -> if (i < 0) PREFERRED_COUNTRIES.size else i } }
                        .thenByDescending { it.latency }
                )

        /** Offline voices whose data is on the phone. */
        private fun isUsable(voice: Voice) = !voice.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features

        private val PREFERRED_COUNTRIES = listOf("US", "GB")
    }
}
