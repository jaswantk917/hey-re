package org.stypox.dicio.skills.help

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.io.graphical.LocalAsk
import org.stypox.dicio.ui.home.groupedItemShape
import org.stypox.dicio.ui.util.rememberFirstShow
import org.stypox.dicio.util.getString

/** Every skill that is on, with something to say to it; tapping one says it. */
class HelpOutput(
    private val skills: List<SkillInfo>,
    private val hinglish: Boolean,
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String =
        ctx.getString(if (hinglish) R.string.skill_help_speech_hi else R.string.skill_help_speech)

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        val context = LocalContext.current
        val ask = LocalAsk.current
        val firstShow = rememberFirstShow(this)
        Column {
            Headline(text = getSpeechOutput(ctx))
            Spacer(modifier = Modifier.height(12.dp))
            skills.forEachIndexed { index, skill ->
                if (index > 0) Spacer(modifier = Modifier.height(2.dp))
                val example = remember(skill) { skill.sentenceExample(context) }
                Arrive(index, firstShow) {
                    Surface(
                        onClick = { ask(example) },
                        shape = groupedItemShape(index, skills.size),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SkillRow(skill, example)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SkillRow(skill: SkillInfo, example: String) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialShapes.Cookie6Sided.toShape()),
        ) {
            Icon(
                painter = skill.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = skill.name(context),
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "“$example”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Rows rise in one after the other the first time, quickly, since there are many. */
@Composable
private fun Arrive(index: Int, firstShow: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (firstShow) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!firstShow) return@LaunchedEffect
        delay(100L + index * 45L)
        progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 24.dp.toPx()
        }
    ) { content() }
}
