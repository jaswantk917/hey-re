package org.stypox.dicio.skills.brief

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences

object BriefInfo : SkillInfo("brief") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_brief)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_brief)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.NotificationImportant)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.Brief[ctx.sentencesLanguage] ?: return null
        return BriefSkill(BriefInfo, data)
    }
}
