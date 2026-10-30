package org.stypox.dicio.skills.toggle

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences

object ToggleInfo : SkillInfo("phone_toggle") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_phone_toggle)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_phone_toggle)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.ToggleOn)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.PhoneToggle[ctx.sentencesLanguage] ?: return null
        return ToggleSkill(ToggleInfo, data)
    }
}
