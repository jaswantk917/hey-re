package org.stypox.dicio.skills.alarm

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences

object AlarmInfo : SkillInfo("alarm") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_alarm)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_alarm)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.Alarm)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.Alarm[ctx.sentencesLanguage] ?: return null
        return AlarmSkill(AlarmInfo, data)
    }
}
