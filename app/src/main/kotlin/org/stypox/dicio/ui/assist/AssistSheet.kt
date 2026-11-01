package org.stypox.dicio.ui.assist

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.LocalAsk
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.ui.home.AppearFrom
import org.stypox.dicio.ui.home.AppearOnce
import org.stypox.dicio.ui.home.AssistantCard
import org.stypox.dicio.ui.home.ChatModeButton
import org.stypox.dicio.ui.home.HomeDock
import org.stypox.dicio.ui.home.HomeScreenViewModel
import org.stypox.dicio.ui.home.LoadingAnswerCard
import org.stypox.dicio.history.RestoredAnswer
import org.stypox.dicio.ui.home.SttHaptics
import org.stypox.dicio.ui.home.UserBubble
import org.stypox.dicio.ui.home.orbMood
import org.stypox.dicio.ui.home.revealLastItem
import org.stypox.dicio.ui.home.rememberHappyOnNewAnswer
import org.stypox.dicio.ui.home.rememberImagePickers
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics

/**
 * What the assist gesture opens: a sheet over whatever app is in front, listening right away, with
 * only the questions and answers of this visit. The full app is one tap away.
 */
@Composable
fun AssistSheet(
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val viewModel: HomeScreenViewModel = hiltViewModel()
    val interactionLog by viewModel.skillEvaluator.state.collectAsState()
    val sttState by viewModel.sttInputDevice.uiState.collectAsState()
    val haptics = rememberHaptics()
    val ask: (String) -> Unit = {
        haptics.play(Haptic.Tap)
        viewModel.skillEvaluator.processInputEvent(InputEvent.Final(listOf(Pair(it, 1.0f))))
    }

    // earlier conversations stay in the full app; the sheet starts empty. Counted per answer,
    // since a follow-up can continue an interaction that started before the sheet opened
    val allAnswers = interactionLog.interactions.flatMap { interaction ->
        interaction.questionsAnswers.map { interaction.skill to it }
    }
    val firstAnswer = rememberSaveable { allAnswers.size }
    // a chat brought back from the history after the sheet opened belongs to the full app too
    val answers = allAnswers.drop(firstAnswer).filter { (_, qa) -> qa.answer !is RestoredAnswer }
    val pendingQuestion = interactionLog.pendingQuestion
    var typedText by rememberSaveable { mutableStateOf<String?>(null) }

    val isHappy = rememberHappyOnNewAnswer(interactionLog)
    SttHaptics(sttState)
    val imagePickers = rememberImagePickers(viewModel.skillEvaluator::processImage)
    val chatMode by viewModel.skillEvaluator.chatMode.collectAsState()

    OverlaySheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 24.dp, end = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.brand_name),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                ChatModeButton(chatMode, viewModel.skillEvaluator::setChatMode)
                IconButton(onClick = onOpenApp, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.OpenInFull, contentDescription = stringResource(R.string.assist_open_app))
                }
            }
            ScreenChip(
                collapseOn = answers.size,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )

            val seen = remember { mutableSetOf<Any>() }
            val listState = rememberLazyListState()
            val itemCount = answers.size * 2 + if (pendingQuestion != null) 2 else 0
            // keyed on both: a pending question turning into an answer keeps the same item count
            LaunchedEffect(answers.size, pendingQuestion != null) {
                if (itemCount > 0) {
                    listState.animateScrollToItem(itemCount - 1)
                    listState.revealLastItem()
                }
            }

            if (answers.isEmpty() && pendingQuestion == null) {
                Text(
                    text = stringResource(R.string.home_prompt),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                ) {
                    answers.forEachIndexed { index, (skill, qa) ->
                        if (qa.question != null) {
                            item(key = "q$index") {
                                AppearOnce(key = "q$index", seen = seen, from = AppearFrom.End) {
                                    UserBubble(text = qa.question, onClick = { typedText = qa.question })
                                }
                            }
                        }
                        item(key = "a$index") {
                            AppearOnce(key = "a$index", seen = seen, from = AppearFrom.Start) {
                                AssistantCard(skill = skill) {
                                    CompositionLocalProvider(LocalAsk provides ask) {
                                        qa.answer.GraphicalOutput(ctx = viewModel.skillContext)
                                    }
                                }
                            }
                        }
                    }
                    if (pendingQuestion != null) {
                        item(key = "pending") {
                            UserBubble(
                                text = pendingQuestion.userInput,
                                isPartial = pendingQuestion.skillBeingEvaluated == null,
                            )
                        }
                        val skill = pendingQuestion.skillBeingEvaluated
                        if (skill != null) {
                            item(key = "loading") { LoadingAnswerCard(skill = skill) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.padding(top = 4.dp))
            HomeDock(
                sttState = sttState,
                mood = orbMood(sttState, pendingQuestion != null, isHappy),
                typedText = typedText,
                onTypedTextChange = { typedText = it },
                onSend = {
                    if (it.isNotBlank()) {
                        ask(it.trim())
                        typedText = ""
                    }
                },
                onSttClick = {
                    // quiet first, as on the home screen
                    viewModel.speechOutputDevice.stopSpeaking()
                    viewModel.sttInputDevice.onClick(viewModel.skillEvaluator::processInputEvent)
                },
                onOrbLongClick = { haptics.play(Haptic.Spin) },
                onTakePhoto = imagePickers.takePhoto,
                onChoosePhoto = imagePickers.choosePhoto,
                onPasteImage = imagePickers.pasteImage,
                chatMode = chatMode,
                onToggleTyping = {
                    haptics.play(Haptic.Tap)
                    typedText = if (typedText == null) "" else null
                },
            )
        }
    }
}

/**
 * A bottom sheet drawn right in the activity's own transparent window (a ModalBottomSheet would
 * open a dialog window that doesn't go edge to edge here). It springs up, follows the finger when
 * dragged down, and shrinks along with the predictive back gesture.
 */
@Composable
private fun OverlaySheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val appear = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    val back = remember { Animatable(0f) }
    var sheetHeight by remember { mutableFloatStateOf(1f) }
    var dismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
    }

    fun dismiss() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            appear.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium))
            onDismiss()
        }
    }

    PredictiveBackHandler { progress ->
        try {
            progress.collect { back.snapTo(it.progress) }
            dismiss()
        } catch (e: CancellationException) {
            back.animateTo(0f)
            throw e
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.32f))
                .pointerInput(Unit) { detectTapGestures { dismiss() } }
        )
        Surface(
            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // with the keyboard up and a long answer the sheet would otherwise reach under the
                // status bar; the answers list shrinks instead
                .statusBarsPadding()
                .padding(top = 8.dp)
                .fillMaxWidth()
                .onSizeChanged { sheetHeight = it.height.toFloat() }
                .graphicsLayer {
                    translationY = (1f - appear.value) * sheetHeight + drag.value
                    val scale = 1f - 0.08f * back.value
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                    },
                    onDragStopped = { velocity ->
                        if (drag.value > with(density) { 120.dp.toPx() } || velocity > 2400f) {
                            dismiss()
                        } else {
                            drag.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
                        }
                    },
                ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                )
                content()
            }
        }
    }
}
