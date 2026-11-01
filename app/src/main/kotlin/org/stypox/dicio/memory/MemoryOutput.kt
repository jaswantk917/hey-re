package org.stypox.dicio.memory

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.outlined.Psychology
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.io.graphical.Subtitle
import org.stypox.dicio.ui.home.groupedItemShape
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberFirstShow
import org.stypox.dicio.ui.util.rememberHaptics
import org.stypox.dicio.util.getString

/** Labels memory answers; asked for directly ([MemoryRequest]), never listed as a skill. */
object MemoryInfo : SkillInfo("memory") {
    override fun name(context: Context) = context.getString(R.string.pref_memory_title)

    override fun sentenceExample(context: Context) = ""

    @Composable
    override fun icon() = rememberVectorPainter(Icons.Outlined.Psychology)

    override fun build(ctx: SkillContext): Skill<*>? = null
}

sealed interface MemoryOutput : SkillOutput {
    class Remembered(private val fact: String) : MemoryOutput {
        override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(R.string.memory_remembered)

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            val firstShow = rememberFirstShow(this)
            val haptics = rememberHaptics()
            LaunchedEffect(Unit) { if (firstShow) haptics.play(Haptic.Success) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(firstShow)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Headline(text = fact)
                    Subtitle(text = stringResource(R.string.memory_remembered_hint))
                }
            }
        }
    }

    /** "passport kahan hai", answered by what was said about it. */
    class Found(private val facts: List<String>) : MemoryOutput {
        override fun getSpeechOutput(ctx: SkillContext) = facts.first()

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(rememberFirstShow(this@Found))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Headline(text = facts.first())
                    facts.drop(1).forEach { Subtitle(text = it) }
                    Subtitle(text = stringResource(R.string.memory_found_hint))
                }
            }
        }
    }

    class Recalled(private val facts: List<String>) : MemoryOutput {
        override fun getSpeechOutput(ctx: SkillContext) =
            if (facts.isEmpty()) ctx.getString(R.string.memory_nothing)
            else ctx.android.resources.getQuantityString(R.plurals.memory_recalled, facts.size, facts.size)

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Column {
                Headline(text = getSpeechOutput(ctx))
                if (facts.isEmpty()) {
                    Subtitle(text = stringResource(R.string.memory_nothing_hint))
                    return@Column
                }
                Spacer(modifier = Modifier.height(10.dp))
                facts.forEachIndexed { index, fact ->
                    Surface(
                        shape = groupedItemShape(index, facts.size),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                    ) {
                        Text(
                            text = fact,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Subtitle(text = stringResource(R.string.memory_edit_hint))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Badge(firstShow: Boolean) {
    val pop = remember { Animatable(if (firstShow) 0.6f else 1f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie6Sided.toShape()),
    ) {
        Icon(Icons.Outlined.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
