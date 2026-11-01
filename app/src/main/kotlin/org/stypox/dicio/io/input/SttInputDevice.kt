package org.stypox.dicio.io.input

import kotlinx.coroutines.flow.StateFlow

interface SttInputDevice {
    val uiState: StateFlow<SttState>

    fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean

    fun stopListening()

    /** Stops listening and drops whatever was heard, e.g. when the person leaves the app. */
    fun cancelListening() = stopListening()

    fun onClick(eventListener: (InputEvent) -> Unit)

    suspend fun destroy()

    companion object {
        // a breath mid-sentence ("kal... Maa ko call karna") mustn't end it
        const val DEFAULT_STT_SILENCE_DURATION = 3
        // chat mode: someone thinking aloud pauses longer than someone giving a command
        const val CHAT_STT_SILENCE_DURATION = 5
    }
}
