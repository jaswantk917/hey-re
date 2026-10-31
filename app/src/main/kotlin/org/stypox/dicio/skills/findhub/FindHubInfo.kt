package org.stypox.dicio.skills.findhub

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences

object FindHubInfo : SkillInfo("find_hub") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_find_hub)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_find_hub)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.TravelExplore)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.FindHub[ctx.sentencesLanguage] ?: return null
        return FindHubSkill(FindHubInfo, data)
    }
}
