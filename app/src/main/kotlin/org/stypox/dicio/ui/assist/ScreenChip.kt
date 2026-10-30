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
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
 * once the summary is ready, and opens to show it; nothing at all when there is no screen. It
 * closes again when [collapseOn] changes (a new answer), to leave the room to the answer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScreenChip(collapseOn: Any?, modifier: Modifier = Modifier) {
    val summary by ScreenContext.summary.collectAsState()
    val screen by ScreenContext.snapshot.collectAsState()
    val haptics = rememberHaptics()
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(collapseOn) { expanded = false }
    val ready = summary as? ScreenSummary.Ready
    val appName = screen?.appName ?: stringResource(R.string.assist_screen_fallback_name)

    AnimatedVisibility(
        visible = summary is ScreenSummary.Working || ready != null,
        enter = fadeIn() + scaleIn(initialScale = 0.8f) + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = {
                    if (ready != null) {
                        haptics.play(Haptic.Tap)
                        expanded = !expanded
                    }
                },
                enabled = ready != null,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    AnimatedContent(
                        targetState = ready != null,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screenChipIcon",
                    ) { isReady ->
                        if (isReady) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        } else {
                            LoadingIndicator(modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(
                        text = if (ready != null) stringResource(R.string.assist_screen_ready, appName)
                        else stringResource(R.string.assist_screen_reading, appName),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (ready != null) {
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
            AnimatedVisibility(
                visible = expanded && ready != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Text(
                    text = ready?.text.orEmpty(),
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
