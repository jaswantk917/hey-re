package org.stypox.dicio.skills.checkin

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences

object CheckInInfo : SkillInfo("check_in") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_check_in)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_check_in)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.WavingHand)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.CheckIn[ctx.sentencesLanguage] ?: return null
        return CheckInSkill(CheckInInfo, data)
    }
}
