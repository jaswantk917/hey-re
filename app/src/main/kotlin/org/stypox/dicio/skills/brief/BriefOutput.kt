package org.stypox.dicio.skills.brief

import android.app.ActivityOptions
import android.app.PendingIntent
import android.os.Build
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SdCardAlert
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.delay
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.io.graphical.Subtitle
import org.stypox.dicio.ui.home.groupedItemShape
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberFirstShow
import org.stypox.dicio.ui.util.rememberHaptics
import org.stypox.dicio.util.getString

/** The few things that need the person, each a row that opens where it came from. */
class BriefOutput(
    private val items: List<Attention.Ranked>,
    private val nowPlaying: NowPlaying?,
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String {
        if (items.isEmpty()) return ctx.getString(R.string.skill_brief_nothing)
        val phrases = items.map { Attention.phrase(it.candidate) }
        val line = if (phrases.size == 1) phrases[0]
        else phrases.dropLast(1).joinToString(", ") + " and " + phrases.last()
        return line.replaceFirstChar { it.uppercase() }
    }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        val firstShow = rememberFirstShow(this)
        Column {
            Headline(
                text = if (items.isEmpty()) stringResource(R.string.skill_brief_nothing)
                else pluralStringResource(R.plurals.skill_brief_count, items.size, items.size)
            )
            if (items.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Arrive(0, firstShow) { AllClear() }
            }
            items.forEachIndexed { index, ranked ->
                Spacer(modifier = Modifier.height(if (index == 0) 12.dp else 2.dp))
                Arrive(index, firstShow) { ItemRow(ranked.candidate, index, items.size) }
            }
            nowPlaying?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Arrive(items.size, firstShow) { NowPlayingRow(it) }
            }
        }
    }
}

@Composable
private fun ItemRow(candidate: Attention.Candidate, index: Int, count: Int) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            haptics.play(Haptic.Tap)
            candidate.open?.let { open(it) }
        },
        enabled = candidate.open != null,
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            val appIcon = remember(candidate.packageName) {
                candidate.packageName?.let { runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull() }
            }
            if (appIcon != null) {
                Image(rememberDrawablePainter(appIcon), contentDescription = null, modifier = Modifier.size(36.dp))
            } else {
                KindBadge(iconFor(candidate.kind))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleOf(candidate),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = detailOf(candidate)
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun titleOf(candidate: Attention.Candidate): String = when (candidate.kind) {
    Attention.Kind.Battery -> stringResource(R.string.skill_brief_battery)
    Attention.Kind.Storage -> stringResource(R.string.skill_brief_storage)
    else -> candidate.title
}

private fun detailOf(candidate: Attention.Candidate): String = when (candidate.kind) {
    Attention.Kind.Battery, Attention.Kind.Storage -> candidate.title
    Attention.Kind.Task -> candidate.app
    else -> candidate.text
}

private fun iconFor(kind: Attention.Kind): ImageVector = when (kind) {
    Attention.Kind.Battery -> Icons.Default.BatteryAlert
    Attention.Kind.Storage -> Icons.Default.SdCardAlert
    else -> Icons.Default.TaskAlt
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KindBadge(icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie6Sided.toShape()),
    ) {
        Icon(
            icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AllClear() {
    val firstShow = rememberFirstShow("allClear")
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) { if (firstShow) haptics.play(Haptic.Success) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Subtitle(text = stringResource(R.string.skill_brief_all_clear))
    }
}

@Composable
private fun NowPlayingRow(playing: NowPlaying) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
        Icon(
            Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(
                R.string.skill_brief_now_playing,
                listOfNotNull(playing.title, playing.artist).joinToString(" · ") + " (${playing.app})",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Rows rise in one after another the first time the brief is shown. */
@Composable
private fun Arrive(index: Int, firstShow: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (firstShow) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!firstShow) return@LaunchedEffect
        delay(index * STAGGER_MILLIS)
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 24.dp.toPx()
        }
    ) { content() }
}

private fun open(intent: PendingIntent) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // the tap is the person's, so the app it came from may come to the front
            intent.send(
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            )
        } else {
            intent.send()
        }
    } catch (e: PendingIntent.CanceledException) {
        Log.w("BriefOutput", "The notification is gone", e)
    }
}

private const val STAGGER_MILLIS = 70L
