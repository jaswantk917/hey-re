package org.stypox.dicio.skills.nearby

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences

object NearbyInfo : SkillInfo("nearby") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_nearby)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_nearby)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.NearMe)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.Nearby[ctx.sentencesLanguage] ?: return null
        return NearbySkill(NearbyInfo, data)
    }
}
