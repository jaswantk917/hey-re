package org.stypox.dicio.skills.message

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Permission
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.util.PERMISSION_READ_CONTACTS

object MessageInfo : SkillInfo("message") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_message)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_message)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.AutoMirrored.Filled.Message)

    override val neededPermissions: List<Permission> = listOf(PERMISSION_READ_CONTACTS)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.Message[ctx.sentencesLanguage] ?: return null
        val yesNoData = Sentences.UtilYesNo[ctx.sentencesLanguage] ?: return null
        return MessageSkill(MessageInfo, data, yesNoData)
    }
}
