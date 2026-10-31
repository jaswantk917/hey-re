package org.stypox.dicio.io.input.ondevice

import android.content.Context
import android.content.Intent
import android.icu.util.ULocale
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.stypox.dicio.di.LocaleManager
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.ui.util.Progress
import org.stypox.dicio.util.distinctUntilChangedBlockingFirst
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The phone's own offline speech recognition (on Pixels, the same Google model behind Recorder and
 * Gboard voice typing). Much more accurate than the small Vosk models, nothing to download for the
 * languages the phone already has, and on Android 14+ it can switch languages mid-sentence, which
 * is what Hinglish needs.
 */
@RequiresApi(Build.VERSION_CODES.S)
class OnDeviceSttInputDevice(
    private val context: Context,
    localeManager: LocaleManager,
    private val silencesBeforeStop: StateFlow<Int>,
) : SttInputDevice {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default)
    private var recognizer: SpeechRecognizer? = null
    private var listener: ((InputEvent) -> Unit)? = null
    // the recognizer sometimes shows words while listening, then ends with an empty result or a
    // "no match": the user saw what they said, so that's what gets used
    private var lastPartial: String? = null
    // the recognizer gives up ~2s after opening if nobody spoke, too soon to answer "should I
    // call?"; it is quietly restarted until the user speaks, stops it, or MAX_WAIT_MILLIS pass
    private var waitingSince = 0L
    private var speechBegan = false
    private var stopRequested = false
    private var locale: Locale
    // the offline language packs on the phone, null until asked (or if the phone can't tell)
    private var installedLanguages: Set<String>? = null

    private val _uiState = MutableStateFlow<SttState>(SttState.Loaded)
    override val uiState: StateFlow<SttState> = _uiState

    init {
        val (firstLocale, nextLocaleFlow) = localeManager.locale.distinctUntilChangedBlockingFirst()
        locale = firstLocale
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mainHandler.post { checkInstalledLanguages() }
        }
        scope.launch {
            nextLocaleFlow.collect {
                locale = it
                mainHandler.post { _uiState.value = SttState.Loaded }
            }
        }
    }

    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        if (thenStartListeningEventListener != null) {
            mainHandler.post { startListening(thenStartListeningEventListener) }
        }
        return true
    }

    override fun stopListening() {
        mainHandler.post {
            stopRequested = true
            recognizer?.stopListening()
        }
    }

    override fun onClick(eventListener: (InputEvent) -> Unit) {
        mainHandler.post {
            when (_uiState.value) {
                SttState.Listening -> {
                    stopRequested = true
                    recognizer?.stopListening()
                }
                SttState.NotDownloaded, is SttState.ErrorDownloading -> downloadLanguage()
                is SttState.Downloading -> {}
                else -> startListening(eventListener)
            }
        }
    }

    override suspend fun destroy() {
        scope.cancel()
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
        }
    }

    // SpeechRecognizer must only ever be touched from the main thread
    private fun startListening(eventListener: (InputEvent) -> Unit) {
        if (_uiState.value == SttState.Listening || _uiState.value == SttState.WaitingForResult) {
            // summoned again mid-listen: start over on a fresh recognizer, since reusing the busy
            // one fails the old session with ERROR_CLIENT and that would show as an error
            recognizer?.destroy()
            recognizer = null
        }
        listener = eventListener
        lastPartial = null
        speechBegan = false
        stopRequested = false
        waitingSince = SystemClock.elapsedRealtime()
        val recognizer = recognizer
            ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also {
                it.setRecognitionListener(CurrentOnly(it))
                this.recognizer = it
            }
        _uiState.value = SttState.WaitingForResult
        val intent = recognizerIntent()
        Log.i(TAG, "Listening in ${intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)}, " +
                "switch=${intent.getStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES)}")
        recognizer.startListening(intent)
    }

    // switching between Hindi and English needs both offline packs; until they're known to be
    // on the phone, listen in one language only. Uses its own recognizer so it never holds up
    // (or collides with) listening
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkInstalledLanguages() {
        val checker = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        checker.checkRecognitionSupport(
            recognizerIntent(),
            context.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    Log.i(TAG, "Offline languages: installed=${support.installedOnDeviceLanguages}, " +
                            "pending=${support.pendingOnDeviceLanguages}")
                    installedLanguages = support.installedOnDeviceLanguages.map { it.lowercase() }.toSet()
                    if (isIndian() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        HINDI.lowercase() !in installedLanguages.orEmpty() &&
                        support.pendingOnDeviceLanguages.none { it.equals(HINDI, ignoreCase = true) }
                    ) {
                        downloadHindiQuietly(checker)
                    } else {
                        checker.destroy()
                    }
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "Could not check offline languages: $error")
                    checker.destroy()
                }
            },
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun downloadHindiQuietly(recognizer: SpeechRecognizer) {
        recognizer.triggerModelDownload(
            recognizerIntent(HINDI),
            context.mainExecutor,
            object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) {}
                override fun onScheduled() {}

                override fun onSuccess() {
                    installedLanguages = installedLanguages.orEmpty() + HINDI.lowercase()
                    recognizer.destroy()
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "Hindi pack download failed: $error")
                    recognizer.destroy()
                }
            },
        )
    }

    private fun recognizerIntent(language: String = languageTag()) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            silencesBeforeStop.value * SILENCE_UNIT_MILLIS,
        )
        val hinglishInstalled = installedLanguages?.containsAll(HINGLISH_LANGUAGES) == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && isIndian() && hinglishInstalled) {
            // Hinglish: let the model hop between Hindi and Indian English within one sentence
            val languages = arrayListOf(HINDI, "en-IN")
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, languages)
        }
    }

    private fun isIndian() = languageTag().endsWith("-IN")

    // the recognizer only has packs per region (en-IN, en-US...), while the app's language is
    // often just "en": take the phone's own region for it, else the language's most likely one
    private fun languageTag(): String {
        val country = when {
            locale.language == "hi" -> "IN"
            locale.country.isNotEmpty() -> locale.country
            Locale.getDefault().language == locale.language && Locale.getDefault().country.isNotEmpty() ->
                Locale.getDefault().country
            else -> ULocale.addLikelySubtags(ULocale.forLocale(locale)).country
        }
        return if (country.isEmpty()) locale.language else "${locale.language}-$country"
    }

    private fun downloadLanguage() {
        val recognizer = recognizer ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            _uiState.value = SttState.Downloading(Progress.UNKNOWN)
            recognizer.triggerModelDownload(
                recognizerIntent(),
                Executors.newSingleThreadExecutor(),
                object : ModelDownloadListener {
                    // the percentage has no byte counts to show, so the progress stays indeterminate
                    override fun onProgress(completedPercent: Int) {}

                    override fun onSuccess() {
                        _uiState.value = SttState.Loaded
                    }

                    override fun onScheduled() {
                        _uiState.value = SttState.Downloading(Progress.UNKNOWN)
                    }

                    override fun onError(error: Int) {
                        _uiState.value = SttState.ErrorDownloading(RecognizerException(error))
                    }
                },
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // older versions only show the system's own download prompt
            recognizer.triggerModelDownload(recognizerIntent())
            _uiState.value = SttState.Loaded
        }
    }

    /** [owner]'s events, only while it's still the recognizer: a replaced one can still report. */
    private inner class CurrentOnly(private val owner: SpeechRecognizer) : RecognitionListener {
        private val current get() = recognizer === owner
        override fun onReadyForSpeech(params: Bundle?) { if (current) recognitionListener.onReadyForSpeech(params) }
        override fun onBeginningOfSpeech() { if (current) recognitionListener.onBeginningOfSpeech() }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { if (current) recognitionListener.onEndOfSpeech() }
        override fun onPartialResults(partialResults: Bundle?) { if (current) recognitionListener.onPartialResults(partialResults) }
        override fun onResults(results: Bundle?) { if (current) recognitionListener.onResults(results) }
        override fun onError(error: Int) { if (current) recognitionListener.onError(error) }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.i(TAG, "Ready for speech")
            _uiState.value = SttState.Listening
        }

        override fun onBeginningOfSpeech() {
            Log.i(TAG, "Speech began")
            speechBegan = true
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.i(TAG, "Speech ended")
            _uiState.value = SttState.WaitingForResult
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val best = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
            lastPartial = best
            listener?.invoke(InputEvent.Partial(best))
        }

        override fun onResults(results: Bundle?) {
            _uiState.value = SttState.Loaded
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.filter { it.isNotBlank() }
            Log.i(TAG, "Results: ${texts?.size} texts, keys=${results?.keySet()}, partial=${lastPartial != null}")
            if (texts.isNullOrEmpty()) {
                finishWithLastPartialOr(InputEvent.None)
                return
            }
            Log.i(TAG, "Heard ${texts.size} alternatives")
            val scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            listener?.invoke(
                InputEvent.Final(
                    texts.mapIndexed { i, text ->
                        // the recognizer reports -1 when it has no confidence to give; keep order
                        val score = scores?.getOrNull(i)?.takeIf { it >= 0f } ?: (1f - i * 0.1f)
                        Pair(text, score)
                    }
                )
            )
        }

        override fun onError(error: Int) {
            Log.w(TAG, "Recognition error $error")
            val heardNoWords = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (heardNoWords && lastPartial == null && !speechBegan && !stopRequested &&
                SystemClock.elapsedRealtime() - waitingSince < MAX_WAIT_MILLIS
            ) {
                recognizer?.startListening(recognizerIntent())
                return
            }
            if (heardNoWords && lastPartial != null) {
                _uiState.value = SttState.Loaded
                finishWithLastPartialOr(InputEvent.None)
                return
            }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    _uiState.value = SttState.HeardNothing
                    listener?.invoke(InputEvent.None)
                    mainHandler.postDelayed({
                        if (_uiState.value == SttState.HeardNothing) _uiState.value = SttState.Loaded
                    }, HEARD_NOTHING_MILLIS)
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    // the offline pack for this language is missing: offer to fetch it
                    _uiState.value = SttState.NotDownloaded
                    listener?.invoke(InputEvent.None)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                    // a stale session, e.g. the mic reopening right after an answer; start fresh
                    // next time instead of showing an error the user can do nothing about
                    recognizer?.destroy()
                    recognizer = null
                    _uiState.value = SttState.Loaded
                    listener?.invoke(InputEvent.None)
                }
                else -> {
                    _uiState.value = SttState.Loaded
                    listener?.invoke(InputEvent.Error(RecognizerException(error)))
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun finishWithLastPartialOr(fallback: InputEvent) {
        val partial = lastPartial
        lastPartial = null
        listener?.invoke(if (partial != null) InputEvent.Final(listOf(Pair(partial, 1f))) else fallback)
    }

    class RecognizerException(val code: Int) : Exception("Speech recognizer error $code")

    companion object {
        private val TAG = OnDeviceSttInputDevice::class.simpleName
        private const val MAX_RESULTS = 5
        private const val SILENCE_UNIT_MILLIS = 1000
        private const val HEARD_NOTHING_MILLIS = 2500L
        private const val MAX_WAIT_MILLIS = 8000L
        private const val HINDI = "hi-IN"
        private val HINGLISH_LANGUAGES = listOf("hi-in", "en-in")

        fun isAvailable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }
}
