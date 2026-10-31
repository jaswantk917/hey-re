package org.stypox.dicio.skills.toggle

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.io.shell.Shell
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberFirstShow
import org.stypox.dicio.ui.util.rememberHaptics
import org.stypox.dicio.util.getString

/**
 * The switch as it now is, its icon popping into the new state. Without Shizuku it says what's
 * missing, and flips the switch by itself once Shizuku is allowed.
 */
class ToggleOutput(
    private val toggle: Toggle,
    private val on: Boolean,
    private val initial: Result,
) : SkillOutput {
    sealed interface Result {
        data object Done : Result
        data object NeedsShizuku : Result
        data class Failed(val reason: String) : Result
    }

    override fun getSpeechOutput(ctx: SkillContext): String = when (initial) {
        Result.Done -> ctx.getString(
            if (on) R.string.skill_toggle_on else R.string.skill_toggle_off, ctx.getString(toggle.label)
        )
        Result.NeedsShizuku -> ctx.getString(R.string.skill_toggle_needs_shizuku)
        is Result.Failed -> ctx.getString(R.string.skill_toggle_failed, ctx.getString(toggle.label))
    }

    // kept on the answer, not in the card: scrolling back to it must not flip the switch again
    private var result by mutableStateOf(initial)

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        val status by Shell.status.collectAsState()
        // allowed in Shizuku's dialog: do what was asked
        LaunchedEffect(status) {
            if (result == Result.NeedsShizuku && status == Shell.Status.Ready) {
                result = ToggleSkill.flip(toggle, on)
            }
        }
        when (val current = result) {
            Result.Done -> Switched(toggle, on, rememberFirstShow(this))
            Result.NeedsShizuku -> NeedsShizuku(toggle, status)
            is Result.Failed -> Fallback(toggle, stringResource(R.string.skill_toggle_failed, stringResource(toggle.label)))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Switched(toggle: Toggle, on: Boolean, firstShow: Boolean) {
    val haptics = rememberHaptics()
    val pop = remember { Animatable(if (firstShow) 0.6f else 1f) }
    val turn = remember { Animatable(if (!firstShow) 0f else if (on) -90f else 90f) }
    LaunchedEffect(Unit) {
        if (!firstShow) return@LaunchedEffect
        haptics.play(if (on) Haptic.ToggleOn else Haptic.ToggleOff)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(Unit) { turn.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
    val container by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "toggle container",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    rotationZ = turn.value
                }
                .background(container, MaterialShapes.Cookie6Sided.toShape()),
        ) {
            Icon(
                imageVector = if (on) toggle.onIcon else toggle.offIcon,
                contentDescription = null,
                tint = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = -turn.value },
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Headline(
            text = stringResource(
                if (on) R.string.skill_toggle_on else R.string.skill_toggle_off, stringResource(toggle.label)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NeedsShizuku(toggle: Toggle, status: Shell.Status) {
    val context = LocalContext.current
    Column {
        Headline(
            text = stringResource(
                if (status == Shell.Status.NotRunning) R.string.skill_toggle_start_shizuku
                else R.string.skill_toggle_allow_shizuku
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    if (status == Shell.Status.NotRunning) {
                        context.packageManager.getLaunchIntentForPackage(Shell.SHIZUKU_PACKAGE)
                            ?.let { context.startActivity(it) }
                    } else {
                        Shell.requestPermission()
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    stringResource(
                        if (status == Shell.Status.NotRunning) R.string.skill_toggle_open_shizuku
                        else R.string.skill_toggle_allow
                    )
                )
            }
            SettingsButton(toggle)
        }
    }
}

@Composable
private fun Fallback(toggle: Toggle, text: String) {
    Column {
        Headline(text = text)
        Spacer(modifier = Modifier.height(12.dp))
        SettingsButton(toggle)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsButton(toggle: Toggle) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            try {
                context.startActivity(toggle.settingsIntent())
            } catch (e: ActivityNotFoundException) {
                Log.w("ToggleOutput", "No settings page for $toggle", e)
            }
        },
        shapes = ButtonDefaults.shapes(),
    ) {
        Text(stringResource(R.string.skill_toggle_open_settings))
    }
}
