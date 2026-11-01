package org.stypox.dicio.settings

import android.app.Application
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.stypox.dicio.di.WakeDeviceWrapper
import org.stypox.dicio.io.wake.oww.OpenWakeWordDevice
import org.stypox.dicio.memory.Fact
import org.stypox.dicio.memory.Memory
import org.stypox.dicio.memory.MemoryLearner
import org.stypox.dicio.eval.SkillEvaluator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.stypox.dicio.settings.datastore.Language
import org.stypox.dicio.settings.datastore.SpeechOutputDevice
import org.stypox.dicio.settings.datastore.Theme
import org.stypox.dicio.settings.datastore.UserSettings
import org.stypox.dicio.settings.datastore.WakeDevice
import org.stypox.dicio.util.toStateFlowDistinctBlockingFirst
import javax.inject.Inject

@HiltViewModel
class MainSettingsViewModel @Inject constructor(
    application: Application,
    private val wakeDeviceWrapper: WakeDeviceWrapper?,
    private val dataStore: DataStore<UserSettings>,
    private val memory: Memory,
    private val memoryLearner: MemoryLearner,
    skillEvaluator: SkillEvaluator,
) : AndroidViewModel(application) {
    val facts: StateFlow<List<Fact>> = memory.facts
    val pendingChats: StateFlow<Int> = memoryLearner.pendingChats(skillEvaluator.chatId)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    private val _memoryWork = MutableStateFlow<MemoryWork>(MemoryWork.Idle)
    val memoryWork: StateFlow<MemoryWork> = _memoryWork
    private val currentChatId = skillEvaluator.chatId

    init {
        viewModelScope.launch { memory.load() }
    }

    fun forget(fact: Fact) {
        viewModelScope.launch { memory.forget(fact) }
    }

    fun forgetAll() {
        viewModelScope.launch { memory.forgetAll() }
    }

    fun addFact(text: String) {
        viewModelScope.launch { memory.add(text.trim(), System.currentTimeMillis()) }
    }

    fun editFact(fact: Fact, text: String) {
        viewModelScope.launch { memory.edit(fact, text.trim()) }
    }

    fun learnFromChats() {
        if (_memoryWork.value.isRunning) return
        viewModelScope.launch {
            _memoryWork.value = MemoryWork.Learning
            _memoryWork.value = when (val result = memoryLearner.learn(currentChatId.value)) {
                is MemoryLearner.Result.Learned -> MemoryWork.Learned(result.chats, result.facts)
                is MemoryLearner.Result.Interrupted -> MemoryWork.Learned(result.chats, result.facts, finished = false)
                MemoryLearner.Result.NoModel, MemoryLearner.Result.Busy -> MemoryWork.NotNow
            }
        }
    }

    fun importMemory(notes: String) {
        if (_memoryWork.value.isRunning || notes.isBlank()) return
        viewModelScope.launch {
            _memoryWork.value = MemoryWork.Importing(0, 1)
            val result = memoryLearner.import(notes) { done, total -> _memoryWork.value = MemoryWork.Importing(done, total) }
            _memoryWork.value = when (result) {
                is MemoryLearner.Result.Learned -> MemoryWork.Imported(result.facts)
                is MemoryLearner.Result.Interrupted -> MemoryWork.Imported(result.facts, finished = false)
                MemoryLearner.Result.NoModel, MemoryLearner.Result.Busy -> MemoryWork.NotNow
            }
        }
    }

    fun compactMemory() {
        if (_memoryWork.value.isRunning) return
        viewModelScope.launch {
            val before = facts.value.size
            _memoryWork.value = MemoryWork.Compacting
            _memoryWork.value = memoryLearner.compact()?.let { MemoryWork.Compacted(before, it) } ?: MemoryWork.NotNow
        }
    }

    // run blocking because the settings screen cannot start if settings have not been loaded yet
    val settingsState = dataStore.data
        .toStateFlowDistinctBlockingFirst(viewModelScope)

    private fun updateData(transform: (UserSettings.Builder) -> Unit) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .apply(transform)
                    .build()
            }
        }
    }

    val isHeyDicio: StateFlow<Boolean> = wakeDeviceWrapper?.isHeyDicio ?: MutableStateFlow(true)

    fun addOwwUserWakeFile(uri: Uri) {
        viewModelScope.launch {
            OpenWakeWordDevice.addUserWakeFile(getApplication(), uri)
            wakeDeviceWrapper?.reinitialize()
        }
    }

    fun removeOwwUserWakeFile() {
        viewModelScope.launch {
            OpenWakeWordDevice.removeUserWakeFile(getApplication())
            wakeDeviceWrapper?.reinitialize()
        }
    }

    fun setLanguage(value: Language) =
        updateData { it.setLanguage(value) }
    fun setTheme(value: Theme) =
        updateData { it.setTheme(value) }
    fun setDynamicColors(value: Boolean) =
        updateData { it.setDynamicColors(value) }
    fun setTopBarBlur(value: Boolean) =
        updateData { it.setTopBarBlur(value) }
    fun setWakeDevice(value: WakeDevice) =
        updateData { it.setWakeDevice(value) }
    fun setTtsVoice(value: String) =
        updateData { it.setTtsVoice(value) }
    fun setSpeakAnswers(value: Boolean) = updateData {
        it.setSpeechOutputDevice(
            if (value) SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_ANDROID_TTS else SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_NOTHING
        )
    }
}

/** What the memory buttons in Settings are doing, or what they last did. */
sealed interface MemoryWork {
    data object Idle : MemoryWork
    data object Learning : MemoryWork
    data object Compacting : MemoryWork
    data class Learned(val chats: Int, val facts: Int, val finished: Boolean = true) : MemoryWork
    data class Compacted(val before: Int, val after: Int) : MemoryWork
    data class Importing(val done: Int, val total: Int) : MemoryWork
    data class Imported(val facts: Int, val finished: Boolean = true) : MemoryWork
    // Nano isn't ready, or is busy with something else
    data object NotNow : MemoryWork

    val isRunning get() = this == Learning || this == Compacting || this is Importing
}
