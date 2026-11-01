package org.stypox.dicio.ui.home

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.stypox.dicio.R
import org.stypox.dicio.history.SavedChat
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The conversations kept on the phone, by day; a tap brings one back, a swipe forgets it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    chats: List<SavedChat>,
    currentChatId: Long,
    loadImage: suspend (String) -> Bitmap?,
    onOpen: (SavedChat) -> Unit,
    onDelete: (SavedChat) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val days = remember(chats) { byDay(chats, zone) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            var shown = 0
            days.forEach { (day, dayChats) ->
                item(key = "day$day") {
                    Text(
                        text = dayLabel(day, today),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
                    )
                }
                val first = shown
                items(dayChats, key = { it.id }) { chat ->
                    val index = dayChats.indexOf(chat)
                    Arrive(first + index) {
                        ChatRow(
                            chat = chat,
                            isCurrent = chat.id == currentChatId,
                            shape = groupedItemShape(index, dayChats.size),
                            loadImage = loadImage,
                            onOpen = { onOpen(chat) },
                            onDelete = { onDelete(chat) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                shown += dayChats.size
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatRow(
    chat: SavedChat,
    isCurrent: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    loadImage: suspend (String) -> Bitmap?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val swipe = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        onDismiss = {
            haptics.play(Haptic.ToggleOff)
            onDelete()
        },
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.history_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        modifier = modifier,
    ) {
        Surface(
            onClick = {
                haptics.play(Haptic.Tap)
                onOpen()
            },
            shape = shape,
            color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                val imageName = chat.turns.firstNotNullOfOrNull { it.image }
                val thumbnail by produceState<Bitmap?>(null, imageName) { value = imageName?.let { loadImage(it) } }
                val image = thumbnail
                if (image != null) {
                    Image(
                        bitmap = remember(image) { image.asImageBitmap() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie6Sided.toShape()),
                    ) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = DateFormat.getTimeFormat(context).format(chat.updatedAt) + " · " +
                                pluralStringResource(R.plurals.history_answers, chat.turns.size, chat.turns.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(day, today)) {
    0L -> stringResource(R.string.history_today)
    1L -> stringResource(R.string.history_yesterday)
    else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEEE, d MMMM" else "d MMMM yyyy"))
}

/** Rows rise in one after another as the sheet opens. */
@Composable
private fun Arrive(index: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(MAX_STAGGERED) * STAGGER_MILLIS)
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 24.dp.toPx()
        }
    ) { content() }
}

/** Chats grouped by the day they were last added to, newest day and chat first. */
internal fun byDay(chats: List<SavedChat>, zone: ZoneId): List<Pair<LocalDate, List<SavedChat>>> = chats
    .sortedByDescending { it.updatedAt }
    .groupBy { Instant.ofEpochMilli(it.updatedAt).atZone(zone).toLocalDate() }
    .toList()

private const val STAGGER_MILLIS = 40L
private const val MAX_STAGGERED = 8
