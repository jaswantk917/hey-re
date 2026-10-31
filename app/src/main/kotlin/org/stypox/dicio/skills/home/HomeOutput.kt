package org.stypox.dicio.skills.home

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberFirstShow
import org.stypox.dicio.ui.util.rememberHaptics
import org.stypox.dicio.util.RecognizeEverythingSkill
import org.stypox.dicio.util.getString
import java.util.Date

sealed interface HomeOutput : SkillOutput {
    /** What was switched, each with its own switch to flip back by hand. */
    class Switched(
        private val devices: List<Switchable>,
        private val on: Boolean,
        private val failed: Set<String>,
        private val home: HomeControl,
    ) : HomeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String {
            val reached = devices.filter { it.target.id !in failed }
            return when {
                reached.isEmpty() -> ctx.getString(R.string.skill_home_unreachable_device, devices.first().target.name)
                reached.size == 1 -> ctx.getString(
                    if (on) R.string.skill_home_on_one else R.string.skill_home_off_one, reached[0].target.name
                )
                else -> ctx.getString(if (on) R.string.skill_home_on_many else R.string.skill_home_off_many, reached.size)
            }
        }

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            val haptics = rememberHaptics()
            val firstShow = rememberFirstShow(this)
            LaunchedEffect(Unit) {
                if (!firstShow) return@LaunchedEffect
                // "sab band" is the big moment; one light is just a switch flipping
                haptics.play(
                    when {
                        failed.size == devices.size -> Haptic.Nope
                        devices.size > 1 -> Haptic.Success
                        on -> Haptic.ToggleOn
                        else -> Haptic.ToggleOff
                    }
                )
            }
            Column {
                Headline(text = getSpeechOutput(ctx))
                Spacer(modifier = Modifier.height(8.dp))
                DeviceRows(devices, home) { if (it.target.id in failed) it.isOn else on }
            }
        }
    }

    /** "Light" with two lights: tap one, or say which. */
    class Choose(
        private val candidates: List<Switchable>,
        private val on: Boolean,
        private val home: HomeControl,
    ) : HomeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(R.string.skill_home_which)

        override fun getInteractionPlan(ctx: SkillContext): InteractionPlan {
            val pick = object : RecognizeEverythingSkill(HomeInfo) {
                override suspend fun generateOutput(ctx: SkillContext, inputData: String): SkillOutput {
                    val match = HomeTargets.resolve(inputData, candidates.map { it.target })
                    val chosen = (match as? HomeTargets.Match.Found)?.targets
                        ?: (match as? HomeTargets.Match.Ambiguous)?.candidates?.takeIf { HomeTargets.words(inputData).any { it in BOTH } }
                        ?: return NoDevice(inputData)
                    val devices = candidates.filter { it.target in chosen }
                    val failed = home.set(devices, on)
                    return Switched(devices, on, failed.map { it.target.id }.toSet(), home)
                }
            }
            return InteractionPlan.StartSubInteraction(reopenMicrophone = true, nextSkills = listOf(pick))
        }

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Column {
                Headline(text = getSpeechOutput(ctx))
                Spacer(modifier = Modifier.height(8.dp))
                DeviceRows(candidates, home) { it.isOn }
            }
        }

        companion object {
            private val BOTH = setOf("both", "dono", "donon", "sab", "all")
        }
    }

    class NotConnected(private val home: HomeControl) : HomeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(R.string.skill_home_not_connected)

        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            val scope = rememberCoroutineScope()
            Column {
                Headline(text = getSpeechOutput(ctx))
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { scope.launch { home.connect() } },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.skill_home_connect))
                }
            }
        }
    }

    /** "Cooler turns off at 12:55 AM", with Cancel until then. */
    class Scheduled(
        private val switch: ScheduledSwitch,
        private val devices: List<Switchable>,
        private val scheduler: HomeScheduler,
    ) : HomeOutput {
        private val cancelled = mutableStateOf(false)

        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(
            if (switch.on) R.string.skill_home_on_at else R.string.skill_home_off_at,
            switch.names.joinToString(", "),
            DateFormat.getTimeFormat(ctx.android).format(Date(switch.atMillis)),
        )

        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            val haptics = rememberHaptics()
            Column {
                AnimatedContent(
                    targetState = cancelled.value,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "scheduledHeadline",
                ) {
                    Headline(text = if (it) stringResource(R.string.skill_home_schedule_cancelled) else getSpeechOutput(ctx))
                }
                Spacer(modifier = Modifier.height(8.dp))
                for (device in devices) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                        DeviceIcon(isLight = device.target.isLight, isOn = device.isOn == true)
                        Text(
                            text = device.target.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                if (!cancelled.value) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilledTonalButton(
                            onClick = {
                                haptics.play(Haptic.ToggleOff)
                                scheduler.cancel(switch.id)
                                cancelled.value = true
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.countdown_cancel))
                        }
                    }
                }
            }
        }
    }

    class SayWhich(private val candidates: List<HomeTarget>) : HomeOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_home_say_which, candidates.joinToString(" / ") { it.name })
    }

    data object Unreachable : HomeOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(R.string.skill_home_unreachable)
    }

    class NoDevice(private val what: String) : HomeOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(R.string.skill_home_no_device, what)
    }
}

/** One row per device, like the Home app's tiles: the bulb lights up with the switch. */
@Composable
private fun DeviceRows(devices: List<Switchable>, home: HomeControl, initial: (Switchable) -> Boolean?) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val states = remember { mutableStateMapOf<String, Boolean>().apply {
        devices.forEach { device -> initial(device)?.let { put(device.target.id, it) } }
    } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (device in devices) {
            val isOn = states[device.target.id] == true
            var busy by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                DeviceIcon(isLight = device.target.isLight, isOn = isOn)
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)) {
                    Text(
                        text = device.target.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    device.target.room?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = isOn,
                    enabled = !busy,
                    onCheckedChange = { wanted ->
                        haptics.play(if (wanted) Haptic.ToggleOn else Haptic.ToggleOff)
                        states[device.target.id] = wanted
                        busy = true
                        scope.launch {
                            if (home.set(listOf(device), wanted).isNotEmpty()) {
                                states[device.target.id] = !wanted
                                haptics.play(Haptic.Nope)
                            }
                            busy = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DeviceIcon(isLight: Boolean, isOn: Boolean) {
    val container by animateColorAsState(
        if (isOn) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "deviceContainer",
    )
    val content by animateColorAsState(
        if (isOn) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "deviceContent",
    )
    // a small pop when it comes on, like a bulb flicking to life
    val pop by animateFloatAsState(
        if (isOn) 1f else 0.92f,
        spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "devicePop",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .scale(pop)
            .background(container, CircleShape),
    ) {
        Icon(
            imageVector = when {
                !isLight -> Icons.Default.PowerSettingsNew
                isOn -> Icons.Default.Lightbulb
                else -> Icons.Outlined.Lightbulb
            },
            contentDescription = null,
            tint = content,
        )
    }
}
