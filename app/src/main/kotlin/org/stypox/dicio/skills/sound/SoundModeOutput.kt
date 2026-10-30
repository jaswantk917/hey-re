package org.stypox.dicio.skills.sound

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.DoNotDisturbOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import org.stypox.dicio.skills.sound.SoundModeSkill.Mode
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics
import org.stypox.dicio.util.getString

sealed interface SoundModeOutput : SkillOutput {
    class Set(private val mode: Mode) : SoundModeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(mode.done)

        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            val haptics = rememberHaptics()
            val pop = remember { Animatable(0.6f) }
            val shake = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                haptics.play(if (mode == Mode.Ring || mode == Mode.DndOff) Haptic.ToggleOn else Haptic.ToggleOff)
                pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                // vibrate mode shows itself: a quick buzz of the icon
                if (mode == Mode.Vibrate) {
                    repeat(3) {
                        shake.animateTo(6f, tween(45))
                        shake.animateTo(-6f, tween(45))
                    }
                    shake.animateTo(0f, tween(45))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                            translationX = shake.value
                        }
                        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialShapes.Cookie6Sided.toShape()),
                ) {
                    Icon(
                        imageVector = mode.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Headline(text = getSpeechOutput(ctx))
            }
        }
    }

    /** Silent and Do Not Disturb need Android's Do Not Disturb access, granted once in Settings. */
    class NeedsAccess(private val mode: Mode) : SoundModeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(R.string.skill_sound_needs_access)

        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            val context = LocalContext.current
            Column {
                Headline(text = getSpeechOutput(ctx))
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.skill_sound_allow))
                }
            }
        }
    }
}

private val Mode.done
    get() = when (this) {
        Mode.Silent -> R.string.skill_sound_silent
        Mode.Vibrate -> R.string.skill_sound_vibrate
        Mode.Ring -> R.string.skill_sound_ring
        Mode.DndOn -> R.string.skill_sound_dnd_on
        Mode.DndOff -> R.string.skill_sound_dnd_off
    }

private val Mode.icon
    get() = when (this) {
        Mode.Silent -> Icons.Default.NotificationsOff
        Mode.Vibrate -> Icons.Default.Vibration
        Mode.Ring -> Icons.Default.NotificationsActive
        Mode.DndOn -> Icons.Default.DoNotDisturbOn
        Mode.DndOff -> Icons.Default.DoNotDisturbOff
    }
