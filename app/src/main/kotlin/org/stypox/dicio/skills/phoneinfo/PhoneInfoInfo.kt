package org.stypox.dicio.skills.phoneinfo

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences

object PhoneInfoInfo : SkillInfo("phone_info") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_phone_info)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_phone_info)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.BatteryChargingFull)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.PhoneInfo[ctx.sentencesLanguage] ?: return null
        return PhoneInfoSkill(PhoneInfoInfo, data)
    }
}
