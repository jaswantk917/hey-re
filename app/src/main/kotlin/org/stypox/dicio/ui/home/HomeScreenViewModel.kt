package org.stypox.dicio.ui.home

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.stypox.dicio.di.SkillContextInternal
import org.stypox.dicio.di.SpeechOutputDeviceWrapper
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.di.WakeDeviceWrapper
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.eval.SkillHandler
import org.stypox.dicio.history.ChatHistory
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.settings.datastore.UserSettings
import org.stypox.dicio.settings.datastore.WakeDevice
import org.stypox.dicio.settings.datastore.copy
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    application: Application,
    val skillContext: SkillContextInternal,
    val skillHandler: SkillHandler,
    val dataStore: DataStore<UserSettings>,
    val sttInputDevice: SttInputDeviceWrapper,
    val speechOutputDevice: SpeechOutputDeviceWrapper,
    val wakeDevice: WakeDeviceWrapper,
    val skillEvaluator: SkillEvaluator,
    val chatHistory: ChatHistory,
) : AndroidViewModel(application) {

    init {
        // stop speaking when the STT device starts listening
        viewModelScope.launch {
            sttInputDevice.uiState
                .filter { it == SttState.Listening }
                .collect { speechOutputDevice.stopSpeaking() }
        }
    }

    fun disableWakeWord() {
        viewModelScope.launch {
            dataStore.updateData {
                it.copy { wakeDevice = WakeDevice.WAKE_DEVICE_NOTHING }
            }
        }
    }
}
