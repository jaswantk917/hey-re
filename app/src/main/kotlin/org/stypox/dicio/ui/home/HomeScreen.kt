package org.stypox.dicio.ui.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.blur.BlurStop
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.hilt.navigation.compose.hiltViewModel
import dev.shreyaspatil.permissionflow.compose.rememberPermissionFlowRequestLauncher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Permission
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.di.SkillContextImpl
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.io.wake.WakeState
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.InteractionLogPreviews
import org.stypox.dicio.ui.util.SkillInfoPreviews
import org.stypox.dicio.ui.util.rememberHaptics
import org.stypox.dicio.util.checkPermissions
import org.stypox.dicio.util.getNonGrantedSecurePermissions

@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
) {
    val channel = remember { Channel<Boolean>() }
    val coroutineScope = rememberCoroutineScope()
    val launcher = rememberPermissionFlowRequestLauncher { isGranted ->
        coroutineScope.launch {
            channel.send(isGranted.values.all { it })
        }
    }
    val context = LocalContext.current

    suspend fun requestPermissions(permissions: List<Permission>): Boolean {
        val nonGrantedSecurePermissions = getNonGrantedSecurePermissions(
                context,
                permissions.filterIsInstance<Permission.SecurePermission>()
        )
        if (nonGrantedSecurePermissions.isNotEmpty()) {
            // do not request secure permissions directly, it would be confusing, so ask explicitly
            // instead
            return false
        }

        val normalPermissions = permissions.filterIsInstance<Permission.NormalPermission>()
            .map { it.id }.toTypedArray()
        if (checkPermissions(context, *normalPermissions)) {
            // permissions already granted
            return true
        }

        // some permission is not already granted, need to request it
        launcher.launch(normalPermissions)
        return channel.receive()
    }

    val viewModel: HomeScreenViewModel = hiltViewModel()
    // keep assigning permissionRequester at every recomposition because `launcher` changes when
    // the activity is recreated (no rememberSaveable is available)
    viewModel.skillEvaluator.permissionRequester = ::requestPermissions

    val enabledSkillsInfo = viewModel.skillHandler.enabledSkillsInfo.collectAsState()
    val interactionsState = viewModel.skillEvaluator.state.collectAsState()
    val sttState = viewModel.sttInputDevice.uiState.collectAsState()
    val wakeState = viewModel.wakeDevice.state.collectAsState()

    HomeScreen(
        skillContext = viewModel.skillContext,
        skills = enabledSkillsInfo.value,
        interactionLog = interactionsState.value,
        sttState = sttState.value,
        onSttClick = {
            viewModel.sttInputDevice.onClick(viewModel.skillEvaluator::processInputEvent)
        },
        wakeState = wakeState.value,
        onWakeDownload = {
            viewModel.wakeDevice.download()
        },
        onWakeDisable = viewModel::disableWakeWord,
        onManualUserInput = {
            viewModel.skillEvaluator.processInputEvent(InputEvent.Final(listOf(Pair(it, 1.0f))))
        },
        onSettingsClick = onSettingsClick,
        snackbarHost = {
            SnackbarHost(hostState = viewModel.snackbarHostState)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    skillContext: SkillContext,
    // will be null when skills have not been initialized yet
    skills: List<SkillInfo>?,
    interactionLog: InteractionLog,
    // if the STT state is null, it means the user disabled the STT
    sttState: SttState?,
    onSttClick: () -> Unit,
    wakeState: WakeState?,
    onWakeDownload: () -> Unit,
    onWakeDisable: () -> Unit,
    onManualUserInput: (String) -> Unit,
    onSettingsClick: () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    // null while talking, the text being typed while in typing mode
    var typedText by rememberSaveable { mutableStateOf<String?>(null) }
    var showSkillsSheet by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val density = LocalDensity.current

    val interactionCount = interactionLog.interactions.size
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }
    var answersSoFar by remember { mutableIntStateOf(uiPrefs.getInt(ANSWERS_SO_FAR, 0)) }
    val isHappy = rememberHappyOnNewAnswer(interactionLog) {
        answersSoFar += 1
        uiPrefs.edit { putInt(ANSWERS_SO_FAR, answersSoFar) }
    }
    SttHaptics(sttState)

    // closing the keyboard on an empty composer goes back to the orb
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeWasVisible = true
        } else if (imeWasVisible) {
            imeWasVisible = false
            if (typedText.isNullOrBlank()) typedText = null
        }
    }

    val mood = orbMood(sttState, interactionLog.pendingQuestion != null, isHappy)
    fun send(text: String) {
        if (text.isNotBlank()) {
            haptics.play(Haptic.Tap)
            onManualUserInput(text.trim())
            typedText = ""
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight
    // the dock rides on top of the keyboard, so the list and scrim must clear both
    val bottomInset = WindowInsets.ime.union(WindowInsets.navigationBars)
        .asPaddingValues().calculateBottomPadding()
    var dockHeight by remember { mutableStateOf(160.dp) }
    val surface = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surface)
    ) {
        InteractionList(
            skillContext = skillContext,
            skills = skills,
            interactionLog = interactionLog,
            sttState = sttState,
            onSttClick = onSttClick,
            onConfirmedQuestionClick = { typedText = it },
            onSuggestionClick = {
                haptics.play(Haptic.Tap)
                onManualUserInput(it)
            },
            onSeeAllSkills = { showSkillsSheet = true },
            suggestionsCompact = answersSoFar >= ANSWERS_UNTIL_COMPACT,
            wakeState = wakeState,
            onWakeDownload = onWakeDownload,
            onWakeDisable = onWakeDisable,
            // the last item rests just above where the blur and the scrim begin, never inside them
            contentPadding = PaddingValues(top = topInset + 8.dp, bottom = bottomInset + dockHeight + BottomFade + 8.dp),
            modifier = Modifier
                .fillMaxSize()
                .edgeBlur(top = topInset, bottom = bottomInset + dockHeight + BottomFade),
        )

        // scrims so the top bar and the dock stay legible over whatever scrolls beneath them
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + 12.dp)
                .background(Brush.verticalGradient(listOf(surface, surface.copy(alpha = 0.7f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomInset + dockHeight + BottomFade)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to surface.copy(alpha = 0.75f),
                        0.6f to surface.copy(alpha = 0.96f),
                        1f to surface,
                    )
                )
        )

        HomeTopBar(
            showTitle = interactionCount > 0 || interactionLog.pendingQuestion != null,
            onSettingsClick = onSettingsClick,
                )

        HomeDock(
            sttState = sttState,
            mood = mood,
            typedText = typedText,
            onTypedTextChange = { typedText = it },
            onSend = ::send,
            onSttClick = onSttClick,
            onOrbLongClick = { haptics.play(Haptic.Spin) },
            onShowSkills = {
                haptics.play(Haptic.Tap)
                showSkillsSheet = true
            },
            onToggleTyping = {
                haptics.play(Haptic.Tap)
                typedText = if (typedText == null) "" else null
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .onSizeChanged { dockHeight = with(density) { it.height.toDp() } },
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = dockHeight),
        ) {
            snackbarHost()
        }
    }

    if (showSkillsSheet && skills != null) {
        SkillsSheet(
            skills = skills,
            onSkillClick = {
                showSkillsSheet = false
                haptics.play(Haptic.Tap)
                onManualUserInput(it)
            },
            onDismiss = { showSkillsSheet = false },
        )
    }
}

/**
 * Plays the answer haptic whenever an answer lands (follow-ups in the same interaction count too)
 * and returns true for a moment afterwards, while the orb beams.
 */
@Composable
internal fun rememberHappyOnNewAnswer(
    interactionLog: InteractionLog,
    onNewAnswer: () -> Unit = {},
): Boolean {
    val haptics = rememberHaptics()
    val answerCount = interactionLog.interactions.sumOf { it.questionsAnswers.size }
    var isHappy by remember { mutableStateOf(false) }
    var previousAnswerCount by remember { mutableIntStateOf(answerCount) }
    LaunchedEffect(answerCount) {
        val isNewAnswer = answerCount > previousAnswerCount
        previousAnswerCount = answerCount
        if (isNewAnswer) {
            onNewAnswer()
            haptics.play(Haptic.Answer)
            isHappy = true
            delay(HAPPY_MILLIS)
            isHappy = false
        }
    }
    return isHappy
}

/** Plays the haptics that go with the microphone opening, closing, failing and getting ready. */
@Composable
internal fun SttHaptics(sttState: SttState?) {
    val haptics = rememberHaptics()
    var previous by remember { mutableStateOf(sttState) }
    LaunchedEffect(sttState) {
        val before = previous
        previous = sttState
        when {
            // checked first: giving up while listening is a "no", not just the mic closing
            sttState == SttState.HeardNothing && before != SttState.HeardNothing ->
                haptics.play(Haptic.Nope)
            sttState == SttState.Listening && before != SttState.Listening ->
                haptics.play(Haptic.ListenStart)
            before == SttState.Listening && sttState != SttState.Listening ->
                haptics.play(Haptic.ListenEnd)
            sttState.isError() && !before.isError() ->
                haptics.play(Haptic.Error)
            (before is SttState.Downloading || before is SttState.Unzipping ||
                    before == SttState.Downloaded) && sttState.isUsable() ->
                haptics.play(Haptic.Success)
        }
    }
}

private fun SttState?.isError() = this is SttState.ErrorDownloading ||
        this is SttState.ErrorUnzipping || this is SttState.ErrorLoading

private fun SttState?.isUsable() = this == SttState.NotLoaded || this is SttState.Loading ||
        this is SttState.Loaded || this == SttState.HeardNothing

internal fun orbMood(sttState: SttState?, hasPendingQuestion: Boolean, isHappy: Boolean): OrbMood =
    when {
        sttState == SttState.Listening -> OrbMood.Listening
        sttState == SttState.HeardNothing -> OrbMood.Sad
        sttState == SttState.WaitingForResult || hasPendingQuestion -> OrbMood.Thinking
        sttState is SttState.Loading && sttState.thenStartListening -> OrbMood.Thinking
        sttState.isError() -> OrbMood.Sad
        isHappy -> OrbMood.Happy
        sttState == SttState.NotDownloaded || sttState is SttState.Downloading ||
                sttState == SttState.Downloaded || sttState is SttState.Unzipping ||
                sttState == SttState.NotInitialized || sttState == SttState.NotAvailable ||
                sttState == SttState.NoMicrophonePermission -> OrbMood.Sleepy
        else -> OrbMood.Idle
    }

/**
 * Content melts into a progressive blur where it slides under the top bar and the dock. Only
 * Android 13+ can vary the radius, older versions simply draw the content sharp.
 */
private fun Modifier.edgeBlur(top: Dp, bottom: Dp) = blur {
    val height = size.height.value
    if (height > 0f) {
        val topEnd = (top.value / height).coerceIn(0.01f, 0.4f)
        val bottomStart = (1f - bottom.value / height).coerceIn(0.6f, 0.99f)
        blurRadiusSpec = BlurRadiusSpec.verticalGradient(
            listOf(
                BlurStop(0f, 14.dp),
                BlurStop(topEnd, 0.dp),
                BlurStop(bottomStart, 0.dp),
                BlurStop(1f, 28.dp),
            )
        )
    }
}

private const val HAPPY_MILLIS = 1600L
private const val UI_PREFS = "hey_re_ui"
private const val ANSWERS_SO_FAR = "answers_so_far"
private const val ANSWERS_UNTIL_COMPACT = 3
internal val TopBarHeight = 64.dp
// how far above the dock things start fading out as they scroll beneath it
private val BottomFade = 20.dp

@Preview
@Composable
private fun HomeScreenPreview(@PreviewParameter(InteractionLogPreviews::class) interactionLog: InteractionLog) {
    AppTheme {
        HomeScreen(
            skillContext = SkillContextImpl.newForPreviews(LocalContext.current),
            skills = SkillInfoPreviews().values.toList(),
            interactionLog = interactionLog,
            sttState = SttState.Loaded,
            onSttClick = {},
            wakeState = null,
            onWakeDownload = {},
            onWakeDisable = {},
            onManualUserInput = {},
            onSettingsClick = {},
            snackbarHost = {},
        )
    }
}
