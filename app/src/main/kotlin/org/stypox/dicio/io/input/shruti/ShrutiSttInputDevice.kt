package org.stypox.dicio.io.input.shruti

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.io.input.ondevice.BiasingWords
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * Udgam Labs' Shruti: a 30 MB model for Hindi, English and Hinglish, bundled and entirely
 * offline. It records the microphone itself, shows words while the person talks and, once they're
 * done, transcribes the whole of it in one go, which reads better than the live pieces.
 */
class ShrutiSttInputDevice(
    private val context: Context,
    private val silencesBeforeStop: StateFlow<Int>,
) : SttInputDevice {

    // needle is one global, not thread-safe engine: every call, and the recording, on this thread
    private val needleThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + needleThread)
    private var listening: Job? = null
    @Volatile
    private var stopRequested = false
    @Volatile
    private var cancelled = false

    private val _uiState = MutableStateFlow<SttState>(if (model == null) SttState.NotLoaded else SttState.Loaded)
    override val uiState: StateFlow<SttState> = _uiState

    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        scope.launch {
            if (load(thenStartListeningEventListener != null)) {
                thenStartListeningEventListener?.let { startListening(it) }
            }
        }
        return true
    }

    override fun stopListening() {
        stopRequested = true
    }

    // left mid-listen: drop what was heard rather than act on it behind the person's back
    override fun cancelListening() {
        cancelled = true
    }

    override fun onClick(eventListener: (InputEvent) -> Unit) {
        when (_uiState.value) {
            SttState.Listening -> stopRequested = true
            SttState.WaitingForResult, is SttState.Loading -> {}
            else -> tryLoad(eventListener)
        }
    }

    override suspend fun destroy() {
        cancelled = true
        listening?.join()
        scope.cancel()
        needleThread.close()
    }

    private fun load(thenListen: Boolean): Boolean {
        if (model != null) return true
        _uiState.value = SttState.Loading(thenListen)
        return try {
            check(Needle.isAvailable) { "No needle engine for this phone's processor" }
            // stored uncompressed in the APK, so it maps straight from there without a copy
            val buffer = context.assets.openFd(MODEL).use { file ->
                FileInputStream(file.fileDescriptor).channel.use {
                    it.map(FileChannel.MapMode.READ_ONLY, file.startOffset, file.declaredLength)
                }
            }
            check(Needle.load(buffer) >= 0) { Needle.lastError() }
            model = buffer
            _uiState.value = SttState.Loaded
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not load Shruti", e)
            _uiState.value = SttState.ErrorLoading(e)
            false
        }
    }

    private fun startListening(listener: (InputEvent) -> Unit) {
        if (listening?.isActive == true) return
        stopRequested = false
        cancelled = false
        listening = scope.launch { listen(listener) }
    }

    // the microphone permission is asked for by the screens before they let anyone listen
    @SuppressLint("MissingPermission")
    private suspend fun listen(listener: (InputEvent) -> Unit) {
        val keywords = BiasingWords.gather(context, withCommands = false)
            .take(MAX_KEYWORDS).joinToString("\n").ifEmpty { null }
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT, RECORD_BUFFER_SAMPLES * Float.SIZE_BYTES,
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (e: Exception) {
            Log.e(TAG, "Could not open the microphone", e)
            null
        }
        if (record == null) {
            _uiState.value = SttState.Loaded
            listener(InputEvent.Error(IllegalStateException("The microphone is busy")))
            return
        }

        val heard = Audio()
        val endpointer = Endpointer(silencesBeforeStop.value * MILLIS_PER_SILENCE)
        val frame = FloatArray(FRAME_SAMPLES)
        var fed = 0
        var passEvery = STREAM_SAMPLES
        val committed = StringBuilder()
        var nothingSaid = false
        record.startRecording()
        _uiState.value = SttState.Listening
        try {
            while (!stopRequested && !cancelled) {
                val read = record.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) break
                heard.add(frame, read)
                val state = endpointer.frame(rms(frame, read), read * 1000 / SAMPLE_RATE)
                if (state == Endpointer.Heard.Done) break
                if (state == Endpointer.Heard.Nothing) {
                    nothingSaid = true
                    break
                }
                // what's been said so far, about once a second; the recorder keeps the audio
                // that arrives meanwhile
                if (endpointer.speechBegan && heard.size - fed >= passEvery) {
                    val passStartedAt = SystemClock.elapsedRealtime()
                    val json = Needle.streamProcess(heard.from(fed), heard.size - fed, LANGUAGE, keywords)
                    val passMillis = SystemClock.elapsedRealtime() - passStartedAt
                    Log.i(TAG, "Live pass over ${heard.size * 1000L / SAMPLE_RATE} ms took $passMillis ms")
                    // each pass rereads everything said, so passes slow down as it grows; spacing
                    // them out keeps the recording, and noticing the pause, from falling behind
                    passEvery = maxOf(STREAM_SAMPLES, (passMillis * 2 * SAMPLE_RATE / 1000).toInt())
                    fed = heard.size
                    if (json != null && !cancelled) {
                        val words = JSONObject(json)
                        committed.append(' ').append(words.optString("text"))
                        val shown = "$committed ${words.optString("pending")}".trim().replace(SPACES, " ")
                        if (shown.isNotEmpty()) listener(InputEvent.Partial(shown))
                    }
                }
            }
        } finally {
            record.stop()
            record.release()
            if (fed > 0) Needle.streamStop()
        }

        if (cancelled) {
            _uiState.value = SttState.Loaded
            return
        }
        if (nothingSaid) {
            heardNothing(listener)
            return
        }
        _uiState.value = SttState.WaitingForResult
        val startedAt = SystemClock.elapsedRealtime()
        val result = Needle.transcribe(heard.from(0), heard.size, LANGUAGE, keywords)?.let { JSONObject(it) }
        Log.i(TAG, "Transcribed ${heard.size * 1000L / SAMPLE_RATE} ms of audio in " +
                "${SystemClock.elapsedRealtime() - startedAt} ms, language=${result?.optString("language")}, " +
                "ttft=${result?.optDouble("ttft_ms")} tps=${result?.optDouble("decode_tps")}")
        val text = result?.optString("text")?.trim()?.takeIf { it.isNotEmpty() }
            ?: committed.toString().trim().takeIf { it.isNotEmpty() }
        if (cancelled) {
            _uiState.value = SttState.Loaded
        } else if (text == null) {
            heardNothing(listener)
        } else {
            _uiState.value = SttState.Loaded
            listener(InputEvent.Final(listOf(Pair(text, 1f))))
        }
    }

    private fun heardNothing(listener: (InputEvent) -> Unit) {
        _uiState.value = SttState.HeardNothing
        listener(InputEvent.None)
        scope.launch {
            delay(HEARD_NOTHING_MILLIS)
            if (_uiState.value == SttState.HeardNothing) _uiState.value = SttState.Loaded
        }
    }

    /** Everything recorded in one listen, growing as it comes. */
    private class Audio {
        private var samples = FloatArray(SAMPLE_RATE * 10)
        var size = 0
            private set

        fun add(frame: FloatArray, count: Int) {
            if (size + count > samples.size) samples = samples.copyOf(samples.size * 2)
            frame.copyInto(samples, size, 0, count)
            size += count
        }

        fun from(start: Int): FloatArray = samples.copyOfRange(start, size)
    }

    companion object {
        private val TAG = ShrutiSttInputDevice::class.simpleName
        private const val MODEL = "shruti/shruti.cact"
        // left to guess, a Hinglish sentence opening with "hello" was taken for English and
        // "kya haal samachar" heard as "kiah also sir"; Hindi still writes English words in English
        private const val LANGUAGE = "hi"
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_SAMPLES = SAMPLE_RATE / 10
        private const val STREAM_SAMPLES = SAMPLE_RATE
        // room for what arrives while a live pass runs
        private const val RECORD_BUFFER_SAMPLES = SAMPLE_RATE * 10
        private const val MILLIS_PER_SILENCE = 1000
        private const val HEARD_NOTHING_MILLIS = 2500L
        // names it would otherwise spell by sound; more only slows every pass down
        private const val MAX_KEYWORDS = 40
        private val SPACES = Regex("\\s+")

        // loaded once for as long as the app runs, since needle keeps reading from it
        @Volatile
        private var model: ByteBuffer? = null

        fun isAvailable(): Boolean = Needle.isAvailable

        private fun rms(frame: FloatArray, count: Int): Float {
            var sum = 0.0
            for (i in 0 until count) sum += frame[i] * frame[i]
            return sqrt(sum / count).toFloat()
        }
    }
}
