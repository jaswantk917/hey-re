package org.stypox.dicio.skills.brief

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.Brief

/**
 * "Kuch important hai?", or summoning Hey re and saying nothing: the few things that need the
 * person right now, or that nothing does.
 */
class BriefSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Brief>) :
    StandardRecognizerSkill<Brief>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Brief): SkillOutput = brief(ctx)

    companion object {
        suspend fun brief(ctx: SkillContext): SkillOutput = BriefOutput(
            Attention.rank(BriefSources.candidates(ctx.android), System.currentTimeMillis()),
            BriefSources.nowPlaying(ctx.android),
        )
    }
}
