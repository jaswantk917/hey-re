package org.stypox.dicio.skills.findhub

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.io.graphical.LocalAsk
import org.stypox.dicio.io.graphical.Subtitle
import org.stypox.dicio.io.shell.ShizukuPrompt
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberFirstShow
import org.stypox.dicio.ui.util.rememberHaptics
import org.stypox.dicio.util.getString

sealed interface FindHubOutput : SkillOutput {
    data object NotUnderstood : FindHubOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(R.string.skill_find_hub_not_understood)
    }

    data object Failed : FindHubOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(R.string.skill_find_hub_failed)
    }

    data object NeedsShizuku : FindHubOutput {
        override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(R.string.skill_toggle_needs_shizuku)

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) = ShizukuPrompt()
    }

    class Located(private val device: FindHub.Device, private val location: FindHub.Location?) : FindHubOutput {
        override fun getSpeechOutput(ctx: SkillContext): String {
            val place = location?.place ?: return ctx.getString(R.string.skill_find_hub_no_location, device.name)
            return location.lastSeen?.let { ctx.getString(R.string.skill_find_hub_located_seen, device.name, place, it) }
                ?: ctx.getString(R.string.skill_find_hub_located, device.name, place)
        }

        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            val ask = LocalAsk.current
            val context = LocalContext.current
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(iconFor(device), rememberFirstShow(this@Located))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Headline(text = location?.place ?: getSpeechOutput(ctx))
                        Subtitle(text = listOfNotNull(device.name, location?.lastSeen).joinToString(" · "))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (device.ringable) {
                        val command = stringResource(R.string.skill_find_hub_ring_command, device.name)
                        FilledTonalButton(onClick = { ask(command) }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.skill_find_hub_ring))
                        }
                    }
                    OpenFindHub(context)
                }
            }
        }
    }

    class Recalled(private val item: String, private val memo: String?) : FindHubOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            if (memo == null) ctx.getString(R.string.skill_find_hub_not_remembered, item)
            else ctx.getString(R.string.skill_find_hub_recalled, item, memo)

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(if (memo == null) Icons.Default.QuestionMark else Icons.Default.Inventory2, rememberFirstShow(this@Recalled))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (memo == null) {
                        Headline(text = getSpeechOutput(ctx))
                        Subtitle(text = stringResource(R.string.skill_find_hub_remember_hint, item))
                    } else {
                        Headline(text = memo)
                        Subtitle(text = item)
                    }
                }
            }
        }
    }

    class Remembered(private val item: String, private val place: String) : FindHubOutput {
        override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(R.string.skill_find_hub_remembered, item, place)

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            val firstShow = rememberFirstShow(this)
            val haptics = rememberHaptics()
            LaunchedEffect(Unit) { if (firstShow) haptics.play(Haptic.Success) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(Icons.Default.CheckCircle, firstShow)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Headline(text = place)
                    Subtitle(text = item)
                }
            }
        }
    }

    class Ringing(private val device: FindHub.Device) : FindHubOutput {
        override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(R.string.skill_find_hub_ringing, device.name)

        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            val ask = LocalAsk.current
            val stop = stringResource(R.string.skill_find_hub_stop_command, device.name)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingingBadge(iconFor(device))
                    Spacer(modifier = Modifier.width(16.dp))
                    Headline(text = getSpeechOutput(ctx))
                }
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(onClick = { ask(stop) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.skill_find_hub_stop))
                }
            }
        }
    }

    class StoppedRinging(private val device: FindHub.Device) : FindHubOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(R.string.skill_find_hub_stopped, device.name)
    }

    class NoSuchDevice(private val item: String, private val devices: List<FindHub.Device>) : FindHubOutput {
        override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(R.string.skill_find_hub_no_device, item)

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            val ask = LocalAsk.current
            Column {
                Headline(text = getSpeechOutput(ctx))
                for (device in devices) {
                    val command = stringResource(R.string.skill_find_hub_ring_command, device.name)
                    OutlinedButton(onClick = { ask(command) }) { Text(command) }
                }
            }
        }
    }
}

private fun iconFor(device: FindHub.Device): ImageVector = when {
    device.type?.contains("watch", ignoreCase = true) == true || device.name.contains("watch", ignoreCase = true) ->
        Icons.Default.Watch
    device.type?.contains("tablet", ignoreCase = true) == true -> Icons.Default.Tablet
    device.type?.contains("bud", ignoreCase = true) == true || device.type?.contains("headphone", ignoreCase = true) == true ->
        Icons.Default.Headphones
    device.type?.contains("phone", ignoreCase = true) == true || device.isThisPhone -> Icons.Default.PhoneAndroid
    else -> Icons.Default.LocationOn
}

/** The answer's icon in a cookie, popping in the first time it's shown. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Badge(icon: ImageVector, firstShow: Boolean) {
    val pop = remember { Animatable(if (firstShow) 0.6f else 1f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie6Sided.toShape()),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** The device with rings going out, for as long as it rings. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RingingBadge(icon: ImageVector) {
    val ring = rememberInfiniteTransition(label = "findHubRing")
    val progress by ring.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(RING_MILLIS), RepeatMode.Restart),
        label = "findHubRingProgress",
    )
    val shape = MaterialShapes.Cookie6Sided.toShape()
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    val scale = 1f + progress * 0.5f
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - progress
                }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), shape)
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, shape),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OpenFindHub(context: Context) {
    OutlinedButton(
        onClick = {
            context.packageManager.getLaunchIntentForPackage(FindHub.APP_PACKAGE)?.let { context.startActivity(it) }
        },
        shapes = ButtonDefaults.shapes(),
    ) {
        Text(stringResource(R.string.skill_find_hub_open))
    }
}

private const val RING_MILLIS = 1400
