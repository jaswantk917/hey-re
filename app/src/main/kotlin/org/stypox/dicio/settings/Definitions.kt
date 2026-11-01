package org.stypox.dicio.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.stypox.dicio.R
import org.stypox.dicio.settings.datastore.Language
import org.stypox.dicio.settings.datastore.Theme
import org.stypox.dicio.settings.datastore.WakeDevice
import org.stypox.dicio.settings.ui.BooleanSetting
import org.stypox.dicio.settings.ui.ListSetting
import org.stypox.dicio.settings.datastore.UserSettings


@Composable
fun languageSetting() = ListSetting(
    title = stringResource(R.string.pref_language),
    icon = Icons.Default.Language,
    description = stringResource(R.string.pref_language_summary),
    possibleValues = listOf(
        ListSetting.Value(Language.LANGUAGE_SYSTEM, stringResource(R.string.pref_language_system)),
        ListSetting.Value(Language.LANGUAGE_CS, "Čeština"),
        ListSetting.Value(Language.LANGUAGE_DE, "Deutsch"),
        ListSetting.Value(Language.LANGUAGE_EN, "English"),
        ListSetting.Value(Language.LANGUAGE_EN_IN, "English (India)"),
        ListSetting.Value(Language.LANGUAGE_ES, "Español"),
        ListSetting.Value(Language.LANGUAGE_EL, "Ελληνικά"),
        ListSetting.Value(Language.LANGUAGE_FR, "Français"),
        ListSetting.Value(Language.LANGUAGE_IT, "Italiano"),
        ListSetting.Value(Language.LANGUAGE_NL, "Nederlands"),
        ListSetting.Value(Language.LANGUAGE_PL, "Polski"),
        ListSetting.Value(Language.LANGUAGE_RU, "Русский"),
        ListSetting.Value(Language.LANGUAGE_SL, "Slovenščina"),
        ListSetting.Value(Language.LANGUAGE_SV, "Svenska"),
        ListSetting.Value(Language.LANGUAGE_UK, "Українська"),
        ListSetting.Value(Language.LANGUAGE_TR, "Türkçe"),
    ),
)

@Composable
fun themeSetting() = ListSetting(
    title = stringResource(R.string.pref_theme),
    icon = Icons.Default.ColorLens,
    description = stringResource(R.string.pref_theme_summary),
    possibleValues = listOf(
        ListSetting.Value(
            value = Theme.THEME_SYSTEM,
            name = stringResource(R.string.pref_theme_system),
            icon = Icons.Default.PhoneAndroid,
        ),
        ListSetting.Value(
            value = Theme.THEME_SYSTEM_DARK_BLACK,
            name = stringResource(R.string.pref_theme_system_dark_black),
            icon = Icons.Default.PhoneAndroid,
        ),
        ListSetting.Value(
            value = Theme.THEME_LIGHT,
            name = stringResource(R.string.pref_theme_light),
            icon = Icons.Default.LightMode,
        ),
        ListSetting.Value(
            value = Theme.THEME_DARK,
            name = stringResource(R.string.pref_theme_dark),
            icon = Icons.Default.Cloud,
        ),
        ListSetting.Value(
            value = Theme.THEME_BLACK,
            name = stringResource(R.string.pref_theme_black),
            icon = Icons.Default.DarkMode,
        ),
    ),
)

@Composable
fun dynamicColors() = BooleanSetting(
    title = stringResource(R.string.pref_dynamic_colors_title),
    icon = Icons.Default.InvertColors,
    descriptionOff = stringResource(R.string.pref_dynamic_colors_summary),
    descriptionOn = stringResource(R.string.pref_dynamic_colors_summary),
)

@Composable
fun topBarBlur() = BooleanSetting(
    title = stringResource(R.string.pref_top_bar_blur_title),
    icon = Icons.Default.BlurOn,
    descriptionOff = stringResource(R.string.pref_top_bar_blur_summary_off),
    descriptionOn = stringResource(R.string.pref_top_bar_blur_summary_on),
)

/** The blur under the top bar unless the user turned it off. */
val UserSettings.useTopBarBlur: Boolean
    get() = !hasTopBarBlur() || topBarBlur

/** Wallpaper colors unless the user turned them off. */
val UserSettings.useDynamicColors: Boolean
    get() = !hasDynamicColors() || dynamicColors

@Composable
fun wakeDevice() = ListSetting(
    title = stringResource(R.string.pref_wake_method),
    icon = Icons.Default.Hearing,
    description = stringResource(R.string.pref_wake_method_summary),
    possibleValues = listOf(
        ListSetting.Value(
            value = WakeDevice.WAKE_DEVICE_OWW,
            name = stringResource(R.string.pref_wake_method_openwakeword),
        ),
        ListSetting.Value(
            value = WakeDevice.WAKE_DEVICE_NOTHING,
            name = stringResource(R.string.pref_wake_method_disabled),
        )
    )
)

@Composable
fun speakAnswers() = BooleanSetting(
    title = stringResource(R.string.pref_speak_answers_title),
    icon = Icons.Default.RecordVoiceOver,
    descriptionOff = stringResource(R.string.pref_speak_answers_off),
    descriptionOn = stringResource(R.string.pref_speak_answers_on),
)
