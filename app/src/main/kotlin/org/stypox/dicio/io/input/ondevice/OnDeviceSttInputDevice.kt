package org.stypox.dicio.io.input.ondevice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
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
    private var locale: Locale

    private val _uiState = MutableStateFlow<SttState>(SttState.Loaded)
    override val uiState: StateFlow<SttState> = _uiState

    init {
        val (firstLocale, nextLocaleFlow) = localeManager.locale.distinctUntilChangedBlockingFirst()
        locale = firstLocale
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
        mainHandler.post { recognizer?.stopListening() }
    }

    override fun onClick(eventListener: (InputEvent) -> Unit) {
        mainHandler.post {
            when (_uiState.value) {
                SttState.Listening -> recognizer?.stopListening()
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
        listener = eventListener
        val recognizer = recognizer
            ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also {
                it.setRecognitionListener(recognitionListener)
                this.recognizer = it
            }
        _uiState.value = SttState.WaitingForResult
        recognizer.startListening(recognizerIntent())
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            silencesBeforeStop.value * SILENCE_UNIT_MILLIS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && isIndian()) {
            // Hinglish: let the model hop between Hindi and Indian English within one sentence
            val languages = arrayListOf("hi-IN", "en-IN")
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, languages)
        }
    }

    private fun isIndian() = locale.language == "hi" || locale.country.equals("IN", ignoreCase = true)

    private fun languageTag(): String = when {
        locale.language == "hi" -> "hi-IN"
        locale.country.isNotEmpty() -> locale.toLanguageTag()
        // the recognizer wants a country; pick the most common one for the language
        else -> Locale.getAvailableLocales()
            .firstOrNull { it.language == locale.language && it.country.isNotEmpty() }
            ?.toLanguageTag() ?: locale.toLanguageTag()
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

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _uiState.value = SttState.Listening
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _uiState.value = SttState.WaitingForResult
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val best = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
            listener?.invoke(InputEvent.Partial(best))
        }

        override fun onResults(results: Bundle?) {
            _uiState.value = SttState.Loaded
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.filter { it.isNotBlank() }
            if (texts.isNullOrEmpty()) {
                listener?.invoke(InputEvent.None)
                return
            }
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
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    _uiState.value = SttState.Loaded
                    listener?.invoke(InputEvent.None)
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    // the offline pack for this language is missing: offer to fetch it
                    _uiState.value = SttState.NotDownloaded
                    listener?.invoke(InputEvent.None)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    // a stale session; start fresh next time
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

    class RecognizerException(val code: Int) : Exception("Speech recognizer error $code")

    companion object {
        private val TAG = OnDeviceSttInputDevice::class.simpleName
        private const val MAX_RESULTS = 5
        private const val SILENCE_UNIT_MILLIS = 1000

        fun isAvailable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }
}
