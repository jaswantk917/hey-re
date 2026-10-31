package org.stypox.dicio.skills.phoneinfo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.io.graphical.Subtitle
import org.stypox.dicio.io.shell.ShizukuPrompt
import org.stypox.dicio.ui.util.rememberFirstShow
import org.stypox.dicio.util.getString

sealed interface PhoneInfoOutput : SkillOutput {
    data object Failed : PhoneInfoOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(R.string.skill_phone_info_failed)
    }

    data object NeedsShizuku : PhoneInfoOutput {
        override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(R.string.skill_toggle_needs_shizuku)

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) = ShizukuPrompt()
    }

    class Battery(private val level: Int, private val charging: Boolean) : PhoneInfoOutput {
        override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(
            if (charging) R.string.skill_phone_info_battery_charging else R.string.skill_phone_info_battery,
            level.toString(),
        )

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) = Meter(
            icon = if (charging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
            value = "$level%",
            caption = getSpeechOutput(ctx),
            from = level / 100f,
            to = level / 100f,
            firstShow = rememberFirstShow(this),
        )
    }

    class Storage(private val used: String, private val total: String) : PhoneInfoOutput {
        override fun getSpeechOutput(ctx: SkillContext) =
            ctx.getString(R.string.skill_phone_info_storage_speech, used, total)

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            val fraction = gigabytes(used)?.let { u -> gigabytes(total)?.let { t -> u / t } }
            Meter(
                icon = Icons.Default.Storage,
                value = used,
                caption = stringResource(R.string.skill_phone_info_storage, used, total),
                from = fraction,
                to = fraction,
                firstShow = rememberFirstShow(this),
            )
        }
    }

    class Brightness(private val from: Int, private val to: Int) : PhoneInfoOutput {
        override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(R.string.skill_phone_info_brightness, to)

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) = Meter(
            icon = Icons.Default.BrightnessMedium,
            value = "$to%",
            caption = getSpeechOutput(ctx),
            from = from / 100f,
            to = to / 100f,
            firstShow = rememberFirstShow(this),
        )
    }
}

/**
 * A big number over a wavy bar. The bar fills to [to] the first time it's shown, from empty or
 * from [from] (brightness moving from where it was).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Meter(icon: ImageVector, value: String, caption: String, from: Float?, to: Float?, firstShow: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = value, style = MaterialTheme.typography.displayMediumEmphasized)
        }
        if (to != null) {
            val start = if (!firstShow) to else if (from != null && from != to) from else 0f
            val progress = remember { Animatable(start) }
            LaunchedEffect(Unit) {
                progress.animateTo(to, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearWavyProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Subtitle(text = caption)
    }
}

// "190 GB" -> 190, "1.2 TB" -> 1200
private fun gigabytes(text: String): Float? {
    val match = Regex("([\\d.]+)\\s*(GB|TB|MB)", RegexOption.IGNORE_CASE).find(text) ?: return null
    val number = match.groupValues[1].toFloatOrNull() ?: return null
    return when (match.groupValues[2].uppercase()) {
        "TB" -> number * 1000
        "MB" -> number / 1000
        else -> number
    }
}
