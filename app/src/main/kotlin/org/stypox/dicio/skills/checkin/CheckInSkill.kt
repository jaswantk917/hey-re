package org.stypox.dicio.skills.checkin

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.CheckIn as CheckInSentences
import org.stypox.dicio.ui.home.PhoneProfile

class CheckInSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<CheckInSentences>) :
    StandardRecognizerSkill<CheckInSentences>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: CheckInSentences): SkillOutput {
        val stepsOnly = inputData is CheckInSentences.Steps || inputData is CheckInSentences.StepsHinglish
        return CheckInOutput(
            name = PhoneProfile.load(ctx.android)?.firstName,
            hinglish = inputData is CheckInSentences.Hinglish || inputData is CheckInSentences.StepsHinglish,
            stepsOnly = stepsOnly,
            initial = if (stepsOnly) CheckIn.gatherSteps(ctx.android) else CheckIn.gather(ctx.android),
        )
    }
}
