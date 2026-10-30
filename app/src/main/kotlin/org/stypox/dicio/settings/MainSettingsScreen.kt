package org.stypox.dicio.settings

import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.stypox.dicio.R
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.ondevice.OnDeviceSttInputDevice
import org.stypox.dicio.settings.datastore.InputDevice
import org.stypox.dicio.settings.datastore.Language
import org.stypox.dicio.settings.datastore.SpeechOutputDevice
import org.stypox.dicio.settings.datastore.SttPlaySound
import org.stypox.dicio.settings.datastore.Theme
import org.stypox.dicio.settings.datastore.UserSettingsModule.Companion.newDataStoreForPreviews
import org.stypox.dicio.settings.datastore.WakeDevice
import org.stypox.dicio.settings.ui.SettingsGroup
import org.stypox.dicio.settings.ui.SettingsItem
import org.stypox.dicio.ui.theme.AppTheme


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainSettingsScreen(
    navigationIcon: @Composable () -> Unit,
    navigateToSkillSettings: () -> Unit,
    viewModel: MainSettingsViewModel = hiltViewModel(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = navigationIcon,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        }
    ) {
        MainSettingsScreen(
            navigateToSkillSettings = navigateToSkillSettings,
            viewModel = viewModel,
            modifier = Modifier.padding(it),
        )
    }
}

@Composable
private fun MainSettingsScreen(
    navigateToSkillSettings: () -> Unit,
    viewModel: MainSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settingsState.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) {
            viewModel.addOwwUserWakeFile(it)
        }
    }

    val context = LocalContext.current
    val wakeDevice = when (val device = settings.wakeDevice) {
        WakeDevice.UNRECOGNIZED,
        WakeDevice.WAKE_DEVICE_UNSET -> WakeDevice.WAKE_DEVICE_NOTHING
        else -> device
    }
    val speechOutput = when (val speechOutputDevice = settings.speechOutputDevice) {
        SpeechOutputDevice.UNRECOGNIZED,
        SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_UNSET ->
            SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_ANDROID_TTS
        else -> speechOutputDevice
    }
    val isHeyDicio by viewModel.isHeyDicio.collectAsState(true)

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            SettingsGroup(
                title = stringResource(R.string.pref_appearance),
                rows = listOfNotNull(
                    {
                        themeSetting().Render(
                            when (val theme = settings.theme) {
                                Theme.UNRECOGNIZED -> Theme.THEME_SYSTEM
                                else -> theme
                            },
                            viewModel::setTheme,
                        )
                    },
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        { dynamicColors().Render(settings.useDynamicColors, viewModel::setDynamicColors) }
                    } else null,
                    {
                        languageSetting().Render(
                            when (val language = settings.language) {
                                Language.UNRECOGNIZED -> Language.LANGUAGE_SYSTEM
                                else -> language
                            },
                            viewModel::setLanguage,
                        )
                    },
                ),
            )
        }

        item {
            SettingsGroup(
                title = stringResource(R.string.pref_listening),
                rows = listOfNotNull(
                    {
                        inputDevice().Render(
                            when (val inputDevice = settings.inputDevice) {
                                InputDevice.UNRECOGNIZED,
                                InputDevice.INPUT_DEVICE_UNSET ->
                                    if (OnDeviceSttInputDevice.isAvailable(context))
                                        InputDevice.INPUT_DEVICE_ON_DEVICE
                                    else
                                        InputDevice.INPUT_DEVICE_VOSK
                                else -> inputDevice
                            },
                            viewModel::setInputDevice,
                        )
                    },
                    { wakeDevice().Render(wakeDevice, viewModel::setWakeDevice) },
                    if (wakeDevice == WakeDevice.WAKE_DEVICE_OWW) {
                        {
                            if (isHeyDicio) {
                                // the wake word is "Hey Dicio", so there is no custom model at the moment
                                SettingsItem(
                                    modifier = Modifier.clickable { importLauncher.launch(arrayOf("*/*")) },
                                    title = stringResource(R.string.pref_wake_custom_import),
                                    icon = Icons.Default.UploadFile,
                                    description = stringResource(R.string.pref_wake_custom_import_summary_oww),
                                )
                            } else {
                                // a custom model is currently set, give the option to remove it
                                SettingsItem(
                                    modifier = Modifier.clickable { viewModel.removeOwwUserWakeFile() },
                                    title = stringResource(R.string.pref_wake_custom_delete),
                                    icon = Icons.Default.DeleteSweep,
                                    description = stringResource(R.string.pref_wake_custom_delete_summary),
                                )
                            }
                        }
                    } else null,
                    {
                        sttPlaySound().Render(
                            when (val sttPlaySound = settings.sttPlaySound) {
                                SttPlaySound.UNRECOGNIZED,
                                SttPlaySound.STT_PLAY_SOUND_UNSET -> SttPlaySound.STT_PLAY_SOUND_NONE
                                else -> sttPlaySound
                            },
                            viewModel::setSttPlaySound
                        )
                    },
                    {
                        sttSilenceDuration().Render(
                            SttInputDevice.getSttSilenceDurationOrDefault(settings),
                            viewModel::setSttSilenceDuration
                        )
                    },
                    { sttAutoFinish().Render(settings.autoFinishSttPopup, viewModel::setAutoFinishSttPopup) },
                ),
            )
        }

        item {
            SettingsGroup(
                title = stringResource(R.string.pref_speaking),
                rows = listOfNotNull(
                    { speechOutputDevice().Render(speechOutput, viewModel::setSpeechOutputDevice) },
                    if (speechOutput == SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_ANDROID_TTS) {
                        { TtsVoiceSetting(settings.ttsVoice, viewModel::setTtsVoice) }
                    } else null,
                ),
            )
        }

        item {
            SettingsGroup(
                title = stringResource(R.string.pref_skills_title),
                rows = listOf {
                    SettingsItem(
                        title = stringResource(R.string.pref_skills_title),
                        icon = Icons.Default.Extension,
                        description = stringResource(R.string.pref_skills_summary),
                        modifier = Modifier
                            .clickable(onClick = navigateToSkillSettings)
                            .testTag("skill_settings_item")
                    )
                },
            )
        }
    }
}

@Preview
@Composable
private fun MainSettingsScreenPreview() {
    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            MainSettingsScreen(
                navigateToSkillSettings = {},
                viewModel = MainSettingsViewModel(
                    application = Application(),
                    wakeDeviceWrapper = null,
                    dataStore = newDataStoreForPreviews(),
                ),
            )
        }
    }
}

@Preview
@Composable
private fun MainSettingsScreenWithTopBarPreview() {
    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            MainSettingsScreen(
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                navigateToSkillSettings = {},
                viewModel = MainSettingsViewModel(
                    application = Application(),
                    wakeDeviceWrapper = null,
                    dataStore = newDataStoreForPreviews()
                )
            )
        }
    }
}
