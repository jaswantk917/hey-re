package org.stypox.dicio.skills.telephone

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.stypox.dicio.R
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics
import org.stypox.dicio.util.RecognizeYesNoSkill
import org.stypox.dicio.util.getString

/**
 * "Calling Maa in 4": a short countdown with a filling line instead of a yes/no question, so a
 * call needs no second answer but can still be stopped, by tapping Cancel or saying "nahi". The
 * countdown runs on its own, so it also completes if the assist sheet is closed.
 */
class ConfirmCallOutput(
    private val name: String,
    private val number: String,
    private val yesNoData: StandardRecognizerData<Sentences.UtilYesNo>,
) : SkillOutput {
    private enum class State { Counting, Calling, Cancelled }

    private val state = mutableStateOf(State.Counting)
    private var startedAt = SystemClock.elapsedRealtime()
    private var countdown: Job? = null

    override fun getSpeechOutput(ctx: SkillContext): String =
        ctx.getString(R.string.skill_telephone_calling, name)

    override fun getInteractionPlan(ctx: SkillContext): InteractionPlan {
        startedAt = SystemClock.elapsedRealtime()
        countdown?.cancel()
        countdown = scope.launch {
            delay(COUNTDOWN_MILLIS)
            placeCall(ctx)
        }

        // listen meanwhile, so "nahi" stops it and "haan" skips the wait
        val confirmYesNoSkill = object : RecognizeYesNoSkill(TelephoneInfo, yesNoData) {
            override suspend fun generateOutput(
                ctx: SkillContext,
                inputData: Boolean
            ): SkillOutput {
                return if (inputData) {
                    // also after Cancel: "haan" means they changed their mind
                    if (state.value == State.Cancelled) state.value = State.Counting
                    placeCall(ctx)
                    ConfirmedCallOutput(name, number)
                } else {
                    cancel(ctx)
                    ConfirmedCallOutput(null, null)
                }
            }
        }

        return InteractionPlan.ReplaceSubInteraction(
            reopenMicrophone = true,
            nextSkills = listOf(confirmYesNoSkill),
        )
    }

    private fun placeCall(ctx: SkillContext) {
        if (state.value != State.Counting) return
        countdown?.cancel()
        state.value = State.Calling
        // the dialer needs the microphone the cancel listening is holding
        sttInputDevice(ctx).stopListening()
        TelephoneSkill.call(ctx.android, number)
    }

    private fun cancel(ctx: SkillContext) {
        if (state.value != State.Counting) return
        countdown?.cancel()
        state.value = State.Cancelled
        sttInputDevice(ctx).stopListening()
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        val haptics = rememberHaptics()
        val current = state.value
        var secondsLeft by remember { mutableIntStateOf(secondsLeft()) }
        val headline = when (current) {
            State.Counting -> stringResource(R.string.skill_telephone_calling_in, name, secondsLeft)
            State.Calling -> stringResource(R.string.skill_telephone_calling, name)
            State.Cancelled -> stringResource(R.string.skill_telephone_not_calling)
        }
        Column {
            AnimatedContent(
                targetState = headline,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "callHeadline",
            ) { Headline(text = it) }
            Spacer(modifier = Modifier.height(8.dp))
            ContactHeader(name = name, number = number, calling = current == State.Calling)

            if (current == State.Counting) {
                val progress = remember { Animatable(elapsedFraction()) }
                LaunchedEffect(Unit) {
                    progress.animateTo(
                        1f,
                        tween(((1f - progress.value) * COUNTDOWN_MILLIS).toInt(), easing = LinearEasing),
                    )
                }
                // a light tick each second, like a clock counting down
                LaunchedEffect(Unit) {
                    while (true) {
                        val left = COUNTDOWN_MILLIS - (SystemClock.elapsedRealtime() - startedAt)
                        if (left <= 0) break
                        delay(left % 1000 + 1)
                        secondsLeft = secondsLeft()
                        if (secondsLeft > 0) haptics.play(Haptic.Segment)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearWavyProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = {
                            haptics.play(Haptic.ToggleOff)
                            cancel(ctx)
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.skill_telephone_cancel))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            LaunchedEffect(current) {
                if (current == State.Calling) haptics.play(Haptic.Success)
            }
        }
    }

    private fun secondsLeft() = ((COUNTDOWN_MILLIS - (SystemClock.elapsedRealtime() - startedAt) + 999) / 1000)
        .toInt().coerceAtLeast(0)

    private fun elapsedFraction() =
        ((SystemClock.elapsedRealtime() - startedAt).toFloat() / COUNTDOWN_MILLIS).coerceIn(0f, 1f)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SttEntryPoint {
        fun sttInputDevice(): SttInputDeviceWrapper
    }

    companion object {
        private const val COUNTDOWN_MILLIS = 4000L
        private val scope = CoroutineScope(Dispatchers.Main)

        private fun sttInputDevice(ctx: SkillContext) = EntryPointAccessors
            .fromApplication(ctx.android.applicationContext, SttEntryPoint::class.java)
            .sttInputDevice()
    }
}
