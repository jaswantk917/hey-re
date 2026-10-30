package org.stypox.dicio.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.di.SkillContextImpl
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.io.wake.WakeState
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.util.InteractionLogPreviews
import org.stypox.dicio.ui.util.SkillInfoPreviews
import org.stypox.dicio.ui.util.SkillOutputPreviews
import org.stypox.dicio.ui.util.UserInputPreviews
import org.stypox.dicio.ui.util.loadingProgressString

@Composable
fun InteractionList(
    skillContext: SkillContext,
    // will be null when skills have not been initialized yet
    skills: List<SkillInfo>?,
    interactionLog: InteractionLog,
    sttState: SttState?,
    onSttClick: () -> Unit,
    onConfirmedQuestionClick: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onSeeAllSkills: () -> Unit,
    // true once the user has had a few answers and no longer needs the big tiles
    suggestionsCompact: Boolean,
    wakeState: WakeState?,
    onWakeDownload: () -> Unit,
    onWakeDisable: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var totalItemCount = 0
    var lastAnchorIndex = 0
    fun LazyListScope.countedItem(
        canBeAnchor: Boolean,
        content: @Composable LazyItemScope.() -> Unit,
    ) {
        if (canBeAnchor) {
            lastAnchorIndex = totalItemCount
        }
        item(content = content)
        totalItemCount += 1
    }

    val interactions = interactionLog.interactions
    val pendingQuestion = interactionLog.pendingQuestion
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // entrance animations play once; scrolling an item back into view shows it at rest
    val seen = remember { mutableSetOf<Any>() }
    val showWakeSetup = wakeState?.needsSetup() == true

    LazyColumn(
        modifier = modifier.testTag("interaction_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = contentPadding,
        state = listState,
    ) {
        countedItem(canBeAnchor = false) {
            Welcome(seen = seen, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        }

        // only emitted when there is something to show, so that empty slots add no spacing
        if (wakeState != null && showWakeSetup) {
            countedItem(canBeAnchor = false) {
                WakeWordWidget(
                    wakeState = wakeState,
                    onWakeDownload = onWakeDownload,
                    onWakeDisable = onWakeDisable,
                )
            }
        }

        if (sttState.needsSetup()) {
            countedItem(canBeAnchor = false) {
                AppearOnce(key = "voiceSetup", seen = seen, delayMillis = 120) {
                    VoiceSetupCard(sttState = sttState, onSttClick = onSttClick)
                }
            }
        }

        countedItem(canBeAnchor = false) {
            if (skills != null) {
                // avoid showing anything if skills are null, i.e. not ready yet
                SkillTiles(
                    skills = skills,
                    seen = seen,
                    compact = suggestionsCompact || interactions.isNotEmpty() || pendingQuestion != null,
                    onSuggestionClick = onSuggestionClick,
                    onSeeAll = onSeeAllSkills,
                )
            }
        }

        interactions.forEachIndexed { interactionIndex, interaction ->
            countedItem(canBeAnchor = false) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            interaction.questionsAnswers.forEachIndexed { qaIndex, it ->
                if (it.question != null) {
                    // can be null if this QA contains only an error output
                    // (and therefore the error was not caused by the input, but e.g. by the STT)
                    countedItem(canBeAnchor = true) {
                        AppearOnce(key = "q$interactionIndex-$qaIndex", seen = seen, from = AppearFrom.End) {
                            UserBubble(text = it.question, onClick = { onConfirmedQuestionClick(it.question) })
                        }
                    }
                }
                countedItem(canBeAnchor = it.question == null) {
                    AppearOnce(key = "a$interactionIndex-$qaIndex", seen = seen, from = AppearFrom.Start) {
                        AssistantCard(skill = interaction.skill) {
                            it.answer.GraphicalOutput(ctx = skillContext)
                        }
                    }
                }
            }
        }

        if (pendingQuestion != null) {
            if (interactions.isEmpty() || !pendingQuestion.continuesLastInteraction) {
                countedItem(canBeAnchor = false) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (pendingQuestion.skillBeingEvaluated == null) {
                countedItem(canBeAnchor = true) {
                    AppearOnce(key = "pending", seen = seen, from = AppearFrom.End) {
                        UserBubble(text = pendingQuestion.userInput, isPartial = true)
                    }
                }
                countedItem(canBeAnchor = false) {
                    // still include a spacer in place of the loading card, so that when the
                    // pending question first appears and the LaunchedEffect below scrolls to it,
                    // there is enough space for the loading card below the question card that will
                    // only appear after the pending question has been confirmed
                    Spacer(modifier = Modifier.height(96.dp))
                }
            } else {
                countedItem(canBeAnchor = true) {
                    UserBubble(
                        text = pendingQuestion.userInput,
                        onClick = { onConfirmedQuestionClick(pendingQuestion.userInput) },
                    )
                }
                countedItem(canBeAnchor = false) {
                    AppearOnce(key = "loading${interactions.size}", seen = seen, from = AppearFrom.Start) {
                        LoadingAnswerCard(skill = pendingQuestion.skillBeingEvaluated)
                    }
                }
            }
        }
    }

    // TODO check if this causes performance problems
    var prevHasPending by rememberSaveable { mutableStateOf(false) }
    var prevAnswerCount by rememberSaveable { mutableIntStateOf(0) }
    val newHasPending = pendingQuestion != null
    // answers, not interactions: a follow-up (like the timer after "how long?") continues the
    // same interaction and must scroll too
    val newAnswerCount = interactions.sumOf { it.questionsAnswers.size }
    LaunchedEffect(key1 = newHasPending, key2 = newAnswerCount) {
        if ((!prevHasPending && newHasPending) || newAnswerCount > prevAnswerCount) {
            scope.launch {
                listState.animateScrollToItem(lastAnchorIndex)
                listState.revealLastItem()
            }
        }
        prevHasPending = newHasPending
        prevAnswerCount = newAnswerCount
    }
}

/**
 * Keeps the whole last item on screen while it settles (cards grow a moment after appearing), so
 * actions at its bottom, like a countdown's Cancel, aren't left below the fold; an item taller than
 * the screen keeps its top in view instead.
 */
internal suspend fun LazyListState.revealLastItem() {
    withTimeoutOrNull(REVEAL_SETTLE_MILLIS) {
        snapshotFlow {
            val info = layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            if (last == null || last.index != info.totalItemsCount - 1) {
                0
            } else {
                val viewportEnd = info.viewportEndOffset - info.afterContentPadding
                val hiddenBelow = last.offset + last.size - viewportEnd
                val roomAbove = last.offset - (info.viewportStartOffset + info.beforeContentPadding)
                minOf(hiddenBelow, roomAbove)
            }
        }.collect { if (it > 0) animateScrollBy(it.toFloat()) }
    }
}

private const val REVEAL_SETTLE_MILLIS = 1200L

// mirrors WakeWordWidget, which also shows itself while permissions are missing
@Composable
private fun WakeState.needsSetup(): Boolean {
    val permissions by dev.shreyaspatil.permissionflow.compose.rememberMultiplePermissionState(*wakeWordPermissions)
    return !permissions.allGranted || this == WakeState.NotDownloaded ||
            this is WakeState.Downloading || this is WakeState.ErrorDownloading ||
            this is WakeState.ErrorLoading
}

private fun SttState?.needsSetup() = this == SttState.NotDownloaded || this is SttState.Downloading ||
        this is SttState.ErrorDownloading || this == SttState.Downloaded ||
        this is SttState.Unzipping || this is SttState.ErrorUnzipping || this == SttState.NotAvailable

enum class AppearFrom { Bottom, Start, End }

/**
 * Springs [content] into place the first time [key] is composed. [seen] outlives the lazy items,
 * so an item scrolled away and back is simply there.
 */
@Composable
fun AppearOnce(
    key: Any,
    seen: MutableSet<Any>,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    from: AppearFrom = AppearFrom.Bottom,
    content: @Composable () -> Unit,
) {
    val isFirstTime = remember(key) { seen.add(key) }
    val progress = remember(key) { Animatable(if (isFirstTime) 0f else 1f) }
    LaunchedEffect(key) {
        if (isFirstTime) {
            delay(delayMillis.toLong())
            progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val rise = with(LocalDensity.current) { 28.dp.toPx() }
    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * rise
            val scale = 0.9f + 0.1f * p
            scaleX = scale
            scaleY = scale
            transformOrigin = when (from) {
                AppearFrom.Bottom -> TransformOrigin(0.5f, 1f)
                AppearFrom.Start -> TransformOrigin(0f, 0f)
                AppearFrom.End -> TransformOrigin(1f, 1f)
            }
        }
    ) {
        content()
    }
}

/** What the user said or typed, on the right like any chat. Partial speech shows in a softer bubble. */
@Composable
fun UserBubble(text: String, isPartial: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 16.dp),
    ) {
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 8.dp, bottomStart = 28.dp)
        val color = if (isPartial) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
        val contentColor = if (isPartial) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
        val bubble: @Composable () -> Unit = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLargeEmphasized,
                fontStyle = if (isPartial) FontStyle.Italic else FontStyle.Normal,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
        if (onClick == null) {
            Surface(shape = shape, color = color, contentColor = contentColor, content = bubble)
        } else {
            Surface(onClick = onClick, shape = shape, color = color, contentColor = contentColor, content = bubble)
        }
    }
}

/** An answer from a skill, on the left, with the skill's badge on top. */
@Composable
fun AssistantCard(skill: SkillInfo?, content: @Composable BoxScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 32.dp, bottomEnd = 32.dp, bottomStart = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp),
    ) {
        Column(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
            if (skill != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp),
                ) {
                    SkillBadge(icon = skill.icon(), index = skill.id.hashCode().mod(8), size = 30.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = skill.name(LocalContext.current),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun LoadingAnswerCard(@PreviewParameter(SkillInfoPreviews::class) skill: SkillInfo) {
    AssistantCard(skill = skill) {
        LoadingIndicator(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .size(56.dp),
        )
    }
}

/** Shown until the offline voice model is ready, with the download right there in the card. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VoiceSetupCard(sttState: SttState?, onSttClick: () -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = stringResource(R.string.voice_setup_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(
                    if (sttState == SttState.NotAvailable) R.string.voice_setup_unavailable
                    else R.string.voice_setup_description
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            val progress = when (sttState) {
                is SttState.Downloading -> sttState.progress
                is SttState.Unzipping -> sttState.progress
                else -> null
            }
            if (sttState != SttState.NotAvailable) {
                Spacer(modifier = Modifier.height(16.dp))
                // the progress takes the button's place at the button's height, so nothing jumps
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ButtonDefaults.MediumContainerHeight),
                ) {
                    if (progress != null) {
                        Column {
                            if (progress.totalBytes > 0) {
                                LinearWavyProgressIndicator(
                                    progress = { (progress.currentBytes / progress.totalBytes.toFloat()).coerceIn(0f, 1f) },
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearWavyProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = loadingProgressString(context, progress),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else {
                        Button(
                            onClick = onSttClick,
                            shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary,
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                            contentPadding = ButtonDefaults.MediumContentPadding,
                            modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                        ) {
                            Text(
                                text = stringResource(
                                    when (sttState) {
                                        SttState.Downloaded -> R.string.stt_unzip
                                        is SttState.ErrorDownloading, is SttState.ErrorUnzipping -> R.string.retry
                                        else -> R.string.voice_setup_download
                                    }
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Kept for skill output previews, which show their content the way it appears in the list. */
@Composable
fun SkillAnswerCard(content: @Composable BoxScope.() -> Unit) {
    AssistantCard(skill = null, content = content)
}

@Preview
@Composable
fun SkillAnswerCardPreview(@PreviewParameter(SkillOutputPreviews::class) skillOutput: SkillOutput) {
    SkillAnswerCard {
        skillOutput.GraphicalOutput(ctx = SkillContextImpl.newForPreviews(LocalContext.current))
    }
}

@Composable
fun MessageCard(
    containerColor: Color,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    val shape = MaterialTheme.shapes.extraLargeIncreased
    if (onClick == null) {
        Surface(color = containerColor, shape = shape, modifier = modifier) {
            Column(content = content)
        }
    } else {
        Surface(onClick = onClick, color = containerColor, shape = shape, modifier = modifier) {
            Column(content = content)
        }
    }
}

@Preview
@Composable
private fun UserBubblePreview(@PreviewParameter(UserInputPreviews::class) userInput: String) {
    AppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UserBubble(text = userInput)
            UserBubble(text = userInput, isPartial = true)
        }
    }
}

@Preview
@Composable
fun InteractionListPreview(
    @PreviewParameter(InteractionLogPreviews::class) interactionLog: InteractionLog,
) {
    AppTheme {
        InteractionList(
            skillContext = SkillContextImpl.newForPreviews(LocalContext.current),
            skills = SkillInfoPreviews().values.toList(),
            interactionLog = interactionLog,
            sttState = SttState.NotDownloaded,
            onSttClick = {},
            onConfirmedQuestionClick = {},
            onSuggestionClick = {},
            onSeeAllSkills = {},
            suggestionsCompact = false,
            wakeState = null,
            onWakeDownload = {},
            onWakeDisable = {},
            contentPadding = PaddingValues(),
        )
    }
}
