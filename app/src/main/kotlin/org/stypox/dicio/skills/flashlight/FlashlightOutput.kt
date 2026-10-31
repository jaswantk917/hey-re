package org.stypox.dicio.skills.flashlight

import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.getSystemService
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.error.UserAction
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.io.graphical.ReportButton
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberFirstShow
import org.stypox.dicio.ui.util.rememberHaptics
import org.stypox.dicio.util.getString

sealed interface FlashlightOutput : SkillOutput {
    /** The torch as it is now, with a switch; it follows the torch if Quick Settings flips it. */
    class Success(private val cameraId: String, private val turnedOn: Boolean) : FlashlightOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            if (turnedOn) {
                ctx.getString(R.string.skill_flashlight_turned_on)
            } else {
                ctx.getString(R.string.skill_flashlight_turned_off)
            }

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            val cameraManager = LocalContext.current.getSystemService<CameraManager>()
            var on by remember { mutableStateOf(turnedOn) }
            DisposableEffect(cameraManager) {
                val callback = object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(id: String, enabled: Boolean) {
                        if (id == cameraId) on = enabled
                    }
                }
                cameraManager?.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
                onDispose { cameraManager?.unregisterTorchCallback(callback) }
            }
            Torch(on, rememberFirstShow(this)) { wanted ->
                try {
                    cameraManager?.setTorchMode(cameraId, wanted)
                } catch (e: Exception) {
                    Log.w("FlashlightOutput", "Could not switch the torch", e)
                }
            }
        }
    }

    data class Error(private val throwable: Throwable?) : FlashlightOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_flashlight_error)

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Headline(text = getSpeechOutput(ctx))
                if (throwable != null) {
                    ReportButton(throwable, UserAction.FLASHLIGHT)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Torch(on: Boolean, firstShow: Boolean, onSwitch: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    val pop = remember { Animatable(if (firstShow) 0.6f else 1f) }
    LaunchedEffect(Unit) {
        if (!firstShow) return@LaunchedEffect
        haptics.play(if (on) Haptic.ToggleOn else Haptic.ToggleOff)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
    }
    // flipped from the card or Quick Settings: a smaller pop, so the icon visibly follows
    var shownOn by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on == shownOn) return@LaunchedEffect
        shownOn = on
        pop.snapTo(0.85f)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
    }
    val container by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "torch container",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .background(container, MaterialShapes.Cookie6Sided.toShape()),
        ) {
            Icon(
                imageVector = if (on) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                contentDescription = null,
                tint = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(modifier = Modifier.weight(1f)) {
            Headline(
                text = stringResource(if (on) R.string.skill_flashlight_turned_on else R.string.skill_flashlight_turned_off)
            )
        }
        Switch(
            checked = on,
            onCheckedChange = { wanted ->
                haptics.play(if (wanted) Haptic.ToggleOn else Haptic.ToggleOff)
                onSwitch(wanted)
            },
        )
    }
}
