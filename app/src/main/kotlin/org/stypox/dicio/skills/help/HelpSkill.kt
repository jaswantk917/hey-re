package org.stypox.dicio.skills.help

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.Help

class HelpSkill(private val helpInfo: HelpInfo, data: StandardRecognizerData<Help>) :
    StandardRecognizerSkill<Help>(helpInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Help): SkillOutput =
        HelpOutput(
            skills = helpInfo.enabledSkills().filter { it.id != helpInfo.id },
            hinglish = inputData is Help.Hinglish,
        )
}
