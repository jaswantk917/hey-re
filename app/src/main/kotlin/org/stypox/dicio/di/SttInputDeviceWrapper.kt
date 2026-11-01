package org.stypox.dicio.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.io.input.ondevice.OnDeviceSttInputDevice
import org.stypox.dicio.io.input.vosk.VoskInputDevice


interface SttInputDeviceWrapper {
    val uiState: StateFlow<SttState?>

    fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean

    fun stopListening()

    fun cancelListening()

    fun onClick(eventListener: (InputEvent) -> Unit)

    fun reinitializeToReleaseResources()
}

class SttInputDeviceWrapperImpl(
    @param:ApplicationContext private val appContext: Context,
    private val localeManager: LocaleManager,
    private val okHttpClient: OkHttpClient,
) : SttInputDeviceWrapper {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val silencesBeforeStop = MutableStateFlow(SttInputDevice.DEFAULT_STT_SILENCE_DURATION)
    private var sttInputDevice: SttInputDevice = buildInputDevice()

    // null means that the user has not enabled any STT input device
    private val _uiState: MutableStateFlow<SttState?> = MutableStateFlow(null)
    override val uiState: StateFlow<SttState?> = _uiState
    private var uiStateJob: Job? = null

    init {
        scope.launch {
            restartUiStateJob()
        }
    }

    // the phone's own recognizer where it exists (far better, and no download), Vosk elsewhere
    private fun buildInputDevice(): SttInputDevice = if (OnDeviceSttInputDevice.isAvailable(appContext)) {
        OnDeviceSttInputDevice(appContext, localeManager, silencesBeforeStop)
    } else {
        VoskInputDevice(appContext, okHttpClient, localeManager, silencesBeforeStop)
    }

    private suspend fun restartUiStateJob() {
        uiStateJob?.cancel()
        val newSttInputDevice = sttInputDevice
        uiStateJob = scope.launch {
            newSttInputDevice.uiState.collect { _uiState.emit(it) }
        }
    }

    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        return sttInputDevice.tryLoad(thenStartListeningEventListener)
    }

    override fun stopListening() {
        sttInputDevice.stopListening()
    }

    override fun cancelListening() {
        sttInputDevice.cancelListening()
    }

    override fun onClick(eventListener: (InputEvent) -> Unit) {
        sttInputDevice.onClick(eventListener)
    }

    override fun reinitializeToReleaseResources() {
        scope.launch {
            val previous = sttInputDevice
            sttInputDevice = buildInputDevice()
            previous.destroy()
            restartUiStateJob()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
class SttInputDeviceWrapperModule {
    @Provides
    @Singleton
    fun provideInputDeviceWrapper(
        @ApplicationContext appContext: Context,
        localeManager: LocaleManager,
        okHttpClient: OkHttpClient,
    ): SttInputDeviceWrapper {
        return SttInputDeviceWrapperImpl(appContext, localeManager, okHttpClient)
    }
}
