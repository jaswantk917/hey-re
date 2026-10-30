package org.stypox.dicio.skills.telephone

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Countdown
import org.stypox.dicio.io.graphical.CountdownBar
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.util.RecognizeYesNoSkill
import org.stypox.dicio.util.getString

/**
 * "Calling Maa in 4" with a filling line when the contact match is [confident], so a call needs no
 * second answer but can still be stopped by tapping Cancel or saying "nahi". A loose match instead
 * asks "Call Maa?" and waits for a tap or a "haan".
 */
class ConfirmCallOutput(
    private val name: String,
    private val number: String,
    private val yesNoData: StandardRecognizerData<Sentences.UtilYesNo>,
    confident: Boolean,
) : SkillOutput {
    private val countdown = Countdown(auto = confident)

    override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(
        if (countdown.state == Countdown.State.Waiting) R.string.skill_telephone_confirm_call
        else R.string.skill_telephone_calling,
        name,
    )

    override fun getInteractionPlan(ctx: SkillContext): InteractionPlan {
        countdown.start(ctx.android) { TelephoneSkill.call(ctx.android, number) }

        // listen meanwhile, so "nahi" stops it and "haan" skips the wait
        val confirmYesNoSkill = object : RecognizeYesNoSkill(TelephoneInfo, yesNoData) {
            override suspend fun generateOutput(
                ctx: SkillContext,
                inputData: Boolean
            ): SkillOutput {
                return if (inputData) {
                    countdown.confirm(ctx.android)
                    ConfirmedCallOutput(name, number)
                } else {
                    countdown.cancel(ctx.android)
                    ConfirmedCallOutput(null, null)
                }
            }
        }

        return InteractionPlan.ReplaceSubInteraction(
            reopenMicrophone = true,
            nextSkills = listOf(confirmYesNoSkill),
        )
    }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        val state = countdown.state
        Column {
            if (state == Countdown.State.Done || state == Countdown.State.Cancelled) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "callHeadline",
                ) {
                    Headline(
                        text = if (it == Countdown.State.Done) stringResource(R.string.skill_telephone_calling, name)
                        else stringResource(R.string.skill_telephone_not_calling)
                    )
                }
            }
            CountdownBar(
                countdown = countdown,
                confirmLabel = stringResource(R.string.skill_telephone_call_button),
            ) { secondsLeft ->
                Headline(
                    text = if (state == Countdown.State.Waiting)
                        stringResource(R.string.skill_telephone_confirm_call, name)
                    else stringResource(R.string.skill_telephone_calling_in, name, secondsLeft)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ContactHeader(name = name, number = number)
            }
            if (state == Countdown.State.Done) {
                Spacer(modifier = Modifier.height(8.dp))
                ContactHeader(name = name, number = number, calling = true)
            }
        }
    }
}
