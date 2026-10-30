package org.stypox.dicio.skills.sound

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences

object SoundModeInfo : SkillInfo("sound_mode") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_sound_mode)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_sound_mode)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.NotificationsOff)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.SoundMode[ctx.sentencesLanguage] ?: return null
        return SoundModeSkill(SoundModeInfo, data)
    }
}
