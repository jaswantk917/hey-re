package org.stypox.dicio.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.util.SkillInfoPreviews
import java.time.LocalTime

/** The big hello at the top of the home screen; it changes with the time of day. */
@Composable
fun Welcome(seen: MutableSet<Any>, modifier: Modifier = Modifier) {
    val hour = remember { LocalTime.now().hour }
    val greeting = stringResource(
        when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..16 -> R.string.greeting_afternoon
            in 17..21 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
    )
    Column(modifier = modifier.padding(horizontal = 24.dp)) {
        AppearOnce(key = "greeting", seen = seen) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppearOnce(key = "prompt", seen = seen, delayMillis = 90) {
            Text(
                text = stringResource(R.string.home_prompt),
                style = MaterialTheme.typography.displayMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// the skills most worth trying first; the rest live in the skills sheet
private val featuredSkillIds = listOf(
    "current_time", "weather", "timer", "joke", "calculator", "flashlight", "translation", "media",
)

/** A two-column grid of colorful tiles with what Hey re can do; tapping one asks it right away. */
@Composable
fun SkillTiles(
    skills: List<SkillInfo>,
    seen: MutableSet<Any>,
    compact: Boolean,
    onSuggestionClick: (String) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (skills.isEmpty()) {
        NoEnabledSkills(modifier)
        return
    }
    val featured = remember(skills) {
        skills.sortedBy { featuredSkillIds.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .take(FEATURED_COUNT)
    }

    val motion = MaterialTheme.motionScheme
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.try_saying),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            if (skills.size > featured.size) {
                TextButton(onClick = onSeeAll) {
                    Text(stringResource(R.string.see_all_skills))
                }
            }
        }
        Spacer(modifier = Modifier.height(if (compact) 2.dp else 8.dp))
        // once the user knows the ropes the big tiles fold into one row of chips
        AnimatedContent(
            targetState = compact,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> motion.defaultSpatialSpec() }))
            },
            label = "suggestions",
        ) { isCompact ->
            if (isCompact) {
                SuggestionChips(skills = featured, onSuggestionClick = onSuggestionClick)
            } else {
                Column { FeaturedTileGrid(featured, seen, onSuggestionClick) }
            }
        }
    }
}

@Composable
private fun FeaturedTileGrid(
    featured: List<SkillInfo>,
    seen: MutableSet<Any>,
    onSuggestionClick: (String) -> Unit,
) {
        featured.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { columnIndex, skill ->
                    val index = rowIndex * 2 + columnIndex
                    AppearOnce(
                        key = "tile-${skill.id}",
                        seen = seen,
                        delayMillis = 160 + index * 55,
                        modifier = Modifier.weight(1f),
                    ) {
                        SkillTile(skill = skill, index = index, onClick = onSuggestionClick)
                    }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
}

@Composable
private fun SuggestionChips(skills: List<SkillInfo>, onSuggestionClick: (String) -> Unit) {
    val context = LocalContext.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        // the row scrolls edge to edge, past the column's side padding
        modifier = Modifier.layout { measurable, constraints ->
            val extra = 16.dp.roundToPx()
            val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra * 2))
            layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
        },
    ) {
        itemsIndexed(skills, key = { _, skill -> skill.id }) { index, skill ->
            val (container, content) = tileColors()[index % 4]
            Surface(
                onClick = { onSuggestionClick(skill.sentenceExample(context)) },
                shape = RoundedCornerShape(percent = 50),
                color = container,
                contentColor = content,
                modifier = Modifier.padding(start = if (index == 0) 12.dp else 0.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    SkillBadge(
                        icon = skill.icon(),
                        index = index,
                        size = 32.dp,
                        containerColor = content,
                        contentColor = container,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = skill.name(context),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillTile(skill: SkillInfo, index: Int, onClick: (String) -> Unit) {
    val context = LocalContext.current
    val example = skill.sentenceExample(context)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "tilePress",
    )
    val (container, content) = tileColors()[index % 4]

    Surface(
        onClick = { onClick(example) },
        interactionSource = interactionSource,
        // alternate which corner is tucked in, so the grid feels hand-arranged
        shape = if (index % 2 == 0)
            RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 32.dp, bottomStart = 12.dp)
        else
            RoundedCornerShape(topStart = 32.dp, topEnd = 12.dp, bottomEnd = 32.dp, bottomStart = 32.dp),
        color = container,
        contentColor = content,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SkillBadge(
                icon = skill.icon(),
                index = index,
                size = 44.dp,
                containerColor = content,
                contentColor = container,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = skill.name(context),
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "“$example”",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun tileColors() = MaterialTheme.colorScheme.let { c ->
    listOf(
        c.primaryContainer to c.onPrimaryContainer,
        c.tertiaryContainer to c.onTertiaryContainer,
        c.secondaryContainer to c.onSecondaryContainer,
        c.surfaceContainerHighest to c.onSurface,
    )
}

@Composable
private fun NoEnabledSkills(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.all_skills_disabled_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.all_skills_disabled_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val FEATURED_COUNT = 6

@Preview
@Composable
private fun SkillTilesPreview() {
    AppTheme {
        Column {
            Welcome(seen = mutableSetOf())
            SkillTiles(
                skills = SkillInfoPreviews().values.toList(),
                seen = mutableSetOf(),
                compact = false,
                onSuggestionClick = {},
                onSeeAll = {},
            )
        }
    }
}
