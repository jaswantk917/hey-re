package org.stypox.dicio.ui.assist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.stypox.dicio.R
import org.stypox.dicio.io.assist.ScreenContext
import org.stypox.dicio.io.assist.ScreenSummary
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics

/**
 * The little "looking at your screen" pill at the top of the sheet. It turns into "On screen: X"
 * once the summary is ready, and opens to show it; nothing at all when there is no screen. On a
 * page with a link a second pill reads the whole article beside it. An open pill closes again when
 * [collapseOn] changes (a new answer), to leave the room to the answer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenChip(collapseOn: Any?, modifier: Modifier = Modifier) {
    val summary by ScreenContext.summary.collectAsState()
    val article by ScreenContext.article.collectAsState()
    val screen by ScreenContext.snapshot.collectAsState()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(collapseOn) { expanded = null }
    val appName = screen?.appName ?: stringResource(R.string.assist_screen_fallback_name)
    val shown = mapOf(SCREEN to summary, ARTICLE to article)
        .filterValues { it is ScreenSummary.Working || it is ScreenSummary.Ready }

    AnimatedVisibility(
        visible = shown.isNotEmpty(),
        enter = fadeIn() + scaleIn(initialScale = 0.8f) + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryPill(
                    state = summary,
                    reading = stringResource(R.string.assist_screen_reading, appName),
                    ready = stringResource(R.string.assist_screen_ready, appName),
                    icon = Icons.Default.AutoAwesome,
                    expanded = expanded == SCREEN,
                    onToggle = { expanded = if (expanded == SCREEN) null else SCREEN },
                )
                SummaryPill(
                    state = article,
                    reading = stringResource(R.string.assist_article_reading),
                    ready = stringResource(R.string.assist_article_ready),
                    icon = Icons.AutoMirrored.Filled.Article,
                    expanded = expanded == ARTICLE,
                    onToggle = { expanded = if (expanded == ARTICLE) null else ARTICLE },
                )
            }
            val text = (shown[expanded] as? ScreenSummary.Ready)?.text
            AnimatedVisibility(
                visible = text != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                AnimatedContent(
                    targetState = text.orEmpty(),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screenChipText",
                ) { shownText ->
                    Text(
                        text = shownText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SummaryPill(
    state: ScreenSummary?,
    reading: String,
    ready: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val haptics = rememberHaptics()
    val isReady = state is ScreenSummary.Ready
    AnimatedVisibility(
        visible = state is ScreenSummary.Working || isReady,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
    ) {
        Surface(
            onClick = {
                haptics.play(Haptic.Tap)
                onToggle()
            },
            enabled = isReady,
            shape = RoundedCornerShape(50),
            color = if (expanded) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (expanded) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            ) {
                AnimatedContent(
                    targetState = isReady,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screenChipIcon",
                ) { done ->
                    if (done) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    } else {
                        LoadingIndicator(modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    text = if (isReady) ready else reading,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isReady) {
                    val turn by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "screenChipChevron",
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(turn),
                    )
                }
            }
        }
    }
}

private const val SCREEN = "screen"
private const val ARTICLE = "article"
