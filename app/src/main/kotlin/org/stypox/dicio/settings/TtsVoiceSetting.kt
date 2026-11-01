package org.stypox.dicio.settings

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.stypox.dicio.R
import org.stypox.dicio.io.speech.AndroidTtsSpeechDevice
import org.stypox.dicio.settings.ui.SettingsItem
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics

/**
 * Picks the voice Hey re speaks with, out of the offline voices on the phone. Tapping one plays a
 * short line in it, so the choice is made by ear rather than by a code like "en-us-x-iol-local".
 */
@Composable
fun TtsVoiceSetting(voiceName: String, onVoiceChange: (String) -> Unit) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var voices by remember { mutableStateOf<List<Voice>>(listOf()) }
    val tts = remember {
        // the voices are only known once the engine is up
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                voices = engine?.voices.orEmpty()
                    .filter(AndroidTtsSpeechDevice::isUsable)
                    .sortedWith(compareBy({ it.locale.displayName }, { it.name }))
            }
        }
        engine
    }
    DisposableEffect(tts) { onDispose { tts.shutdown() } }

    val automatic = stringResource(R.string.pref_tts_voice_automatic)
    SettingsItem(
        modifier = Modifier.clickable { sheetOpen = true },
        title = stringResource(R.string.pref_tts_voice),
        icon = Icons.Outlined.RecordVoiceOver,
        description = voices.firstOrNull { it.name == voiceName }?.let { voiceLabel(it, voices) }
            ?: automatic,
    )

    if (sheetOpen) {
        VoiceSheet(
            voices = voices,
            selected = voiceName,
            automatic = automatic,
            onPick = { voice ->
                onVoiceChange(voice?.name ?: "")
                tts.stop()
                voice?.let { tts.voice = it }
                tts.speak(context.getString(R.string.pref_tts_voice_sample), TextToSpeech.QUEUE_FLUSH, null, "sample")
            },
            onDismiss = {
                tts.stop()
                sheetOpen = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSheet(
    voices: List<Voice>,
    selected: String,
    automatic: String,
    onPick: (Voice?) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.pref_tts_voice),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            text = stringResource(R.string.pref_tts_voice_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                VoiceRow(automatic, null, selected.isEmpty()) {
                    haptics.play(Haptic.Tap)
                    onPick(null)
                }
            }
            items(voices, key = { it.name }) { voice ->
                VoiceRow(voiceLabel(voice, voices), voice.locale.displayName, voice.name == selected) {
                    haptics.play(Haptic.Tap)
                    onPick(voice)
                }
            }
        }
    }
}

@Composable
private fun VoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// "English (United States) · Voice 3": engines name voices with codes nobody can read
private fun voiceLabel(voice: Voice, all: List<Voice>): String {
    val sameAccent = all.filter { it.locale == voice.locale }
    val country = voice.locale.displayCountry.ifEmpty { voice.locale.displayLanguage }
    return if (sameAccent.size <= 1) country else "$country · ${sameAccent.indexOf(voice) + 1}"
}
