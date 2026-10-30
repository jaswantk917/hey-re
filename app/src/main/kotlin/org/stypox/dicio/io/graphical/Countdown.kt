package org.stypox.dicio.io.graphical

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import org.stypox.dicio.R
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics

/**
 * A few seconds' grace before something that is awkward to undo (a call, a message): when [auto] it
 * happens by itself unless cancelled, so no second "yes" is needed; otherwise (a loose contact
 * match) it waits for a tap or a "haan". Leaving Hey re before it goes through cancels it, see
 * [cancelAll].
 */
class Countdown(
    private val auto: Boolean,
    private val millis: Long = DEFAULT_MILLIS,
) {
    enum class State { Waiting, Counting, Done, Cancelled }

    var state by mutableStateOf(if (auto) State.Counting else State.Waiting)
        private set
    private var startedAt = SystemClock.elapsedRealtime()
    private var job: Job? = null
    private var action: () -> Unit = {}

    /** [action] runs at the end of the countdown (or on [confirm]) unless cancelled first. */
    fun start(context: Context, action: () -> Unit) {
        this.action = action
        job?.cancel()
        active.add(this)
        if (!auto) {
            state = State.Waiting
            return
        }
        state = State.Counting
        startedAt = SystemClock.elapsedRealtime()
        job = scope.launch {
            delay(millis)
            confirm(context)
        }
    }

    /** Runs the action now, e.g. after "haan" or a tap; also after Cancel, as a change of mind. */
    fun confirm(context: Context) {
        if (state == State.Done) return
        job?.cancel()
        active.remove(this)
        state = State.Done
        // the listening for "cancel" would otherwise hold the microphone
        sttInputDevice(context).stopListening()
        action()
    }

    fun cancel(context: Context) {
        if (state != State.Counting && state != State.Waiting) return
        job?.cancel()
        active.remove(this)
        state = State.Cancelled
        sttInputDevice(context).stopListening()
    }

    internal fun millisLeft() = millis - (SystemClock.elapsedRealtime() - startedAt)

    internal fun secondsLeft() = ((millisLeft() + 999) / 1000).toInt().coerceAtLeast(0)

    internal fun elapsedFraction() =
        ((SystemClock.elapsedRealtime() - startedAt).toFloat() / millis).coerceIn(0f, 1f)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SttEntryPoint {
        fun sttInputDevice(): SttInputDeviceWrapper
    }

    companion object {
        const val DEFAULT_MILLIS = 4000L
        private val scope = CoroutineScope(Dispatchers.Main)
        private val active = mutableSetOf<Countdown>()

        /**
         * Hey re was closed or left: a call or message still waiting would otherwise go out
         * unseen. One that already went through is unaffected, as opening the dialer or chat is
         * what sends Hey re to the background.
         */
        fun cancelAll(context: Context) {
            active.toList().forEach { it.cancel(context) }
        }

        private fun sttInputDevice(context: Context) = EntryPointAccessors
            .fromApplication(context.applicationContext, SttEntryPoint::class.java)
            .sttInputDevice()
    }
}

/**
 * While counting, the line filling up with a Cancel button; while waiting for a tap, Cancel next to
 * [confirmLabel]. [label] gets the seconds left (0 while waiting). A light tick each second and a
 * firm one when it goes through.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CountdownBar(
    countdown: Countdown,
    confirmLabel: String,
    label: @Composable (secondsLeft: Int) -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val state = countdown.state
    LaunchedEffect(state) {
        if (state == Countdown.State.Done) haptics.play(Haptic.Success)
    }
    if (state != Countdown.State.Counting && state != Countdown.State.Waiting) return

    val counting = state == Countdown.State.Counting
    var secondsLeft by remember { mutableIntStateOf(if (counting) countdown.secondsLeft() else 0) }
    Column {
        label(secondsLeft)
        if (counting) {
            val progress = remember { Animatable(countdown.elapsedFraction()) }
            LaunchedEffect(Unit) {
                progress.animateTo(
                    1f,
                    tween(countdown.millisLeft().coerceAtLeast(0).toInt(), easing = LinearEasing),
                )
            }
            LaunchedEffect(Unit) {
                while (true) {
                    val left = countdown.millisLeft()
                    if (left <= 0) break
                    delay(left % 1000 + 1)
                    secondsLeft = countdown.secondsLeft()
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
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    haptics.play(Haptic.ToggleOff)
                    countdown.cancel(context)
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.countdown_cancel))
            }
            if (!counting) {
                Button(
                    onClick = { countdown.confirm(context) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}
