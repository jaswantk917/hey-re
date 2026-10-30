package org.stypox.dicio.ui.assist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.stypox.dicio.R
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.ui.home.AppearFrom
import org.stypox.dicio.ui.home.AppearOnce
import org.stypox.dicio.ui.home.AssistantCard
import org.stypox.dicio.ui.home.HomeDock
import org.stypox.dicio.ui.home.HomeScreenViewModel
import org.stypox.dicio.ui.home.LoadingAnswerCard
import org.stypox.dicio.ui.home.SkillsSheet
import org.stypox.dicio.ui.home.SttHaptics
import org.stypox.dicio.ui.home.UserBubble
import org.stypox.dicio.ui.home.orbMood
import org.stypox.dicio.ui.home.rememberHappyOnNewAnswer
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics

/**
 * What the assist gesture opens: a sheet over whatever app is in front, listening right away, with
 * only the questions and answers of this visit. The full app is one tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistSheet(
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val viewModel: HomeScreenViewModel = hiltViewModel()
    val interactionLog by viewModel.skillEvaluator.state.collectAsState()
    val sttState by viewModel.sttInputDevice.uiState.collectAsState()
    val skills by viewModel.skillHandler.enabledSkillsInfo.collectAsState()
    val haptics = rememberHaptics()

    // earlier conversations stay in the full app; the sheet starts empty. Counted per answer,
    // since a follow-up can continue an interaction that started before the sheet opened
    val allAnswers = interactionLog.interactions.flatMap { interaction ->
        interaction.questionsAnswers.map { interaction.skill to it }
    }
    val firstAnswer = rememberSaveable { allAnswers.size }
    val answers = allAnswers.drop(firstAnswer)
    val pendingQuestion = interactionLog.pendingQuestion
    var typedText by rememberSaveable { mutableStateOf<String?>(null) }
    var showSkillsSheet by rememberSaveable { mutableStateOf(false) }

    val isHappy = rememberHappyOnNewAnswer(interactionLog)
    SttHaptics(sttState)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.ime },
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
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
                IconButton(onClick = onOpenApp, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.OpenInFull, contentDescription = stringResource(R.string.assist_open_app))
                }
            }

            val seen = remember { mutableSetOf<Any>() }
            val listState = rememberLazyListState()
            val itemCount = answers.size * 2 + if (pendingQuestion != null) 2 else 0
            LaunchedEffect(itemCount) {
                if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
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
                                    qa.answer.GraphicalOutput(ctx = viewModel.skillContext)
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
                        haptics.play(Haptic.Tap)
                        viewModel.skillEvaluator.processInputEvent(
                            InputEvent.Final(listOf(Pair(it.trim(), 1.0f)))
                        )
                        typedText = ""
                    }
                },
                onSttClick = {
                    viewModel.sttInputDevice.onClick(viewModel.skillEvaluator::processInputEvent)
                },
                onOrbLongClick = { haptics.play(Haptic.Spin) },
                onShowSkills = {
                    haptics.play(Haptic.Tap)
                    showSkillsSheet = true
                },
                onToggleTyping = {
                    haptics.play(Haptic.Tap)
                    typedText = if (typedText == null) "" else null
                },
            )
        }
    }

    val skillList = skills
    if (showSkillsSheet && skillList != null) {
        SkillsSheet(
            skills = skillList,
            onSkillClick = {
                showSkillsSheet = false
                viewModel.skillEvaluator.processInputEvent(InputEvent.Final(listOf(Pair(it, 1.0f))))
            },
            onDismiss = { showSkillsSheet = false },
        )
    }
}
