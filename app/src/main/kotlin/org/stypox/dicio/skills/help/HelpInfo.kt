package org.stypox.dicio.skills.help

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences

/** "What can you do": the skills that are on, from [enabledSkills]. */
class HelpInfo(
    val enabledSkills: () -> List<SkillInfo>,
) : SkillInfo("help") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_help)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_help)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.AutoMirrored.Filled.HelpOutline)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.Help[ctx.sentencesLanguage] ?: return null
        return HelpSkill(this, data)
    }
}
