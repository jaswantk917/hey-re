package org.stypox.dicio.eval

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.AlwaysBestScore
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.io.graphical.LocalAsk
import org.stypox.dicio.sentences.Sentences.UtilYesNo
import org.stypox.dicio.util.RecognizeEverythingSkill
import org.stypox.dicio.util.RecognizeYesNoSkill
import org.stypox.dicio.util.getString

/**
 * Gemini Nano's guess at what was meant landed on a skill that acts on the phone or reaches
 * someone ("flight mode on", "message Maa"): ask before doing it, since the guess may be wrong.
 */
fun confirmGuess(
    command: String,
    guess: SkillWithResult<*>,
    yesNo: StandardRecognizerData<UtilYesNo>,
): SkillWithResult<String> {
    val info = guess.skill.correspondingSkillInfo
    val skill = object : RecognizeEverythingSkill(info) {
        override suspend fun generateOutput(ctx: SkillContext, inputData: String): SkillOutput =
            ConfirmGuessOutput(command, guess, yesNo)
    }
    return SkillWithResult(skill, AlwaysBestScore, command)
}

private class ConfirmGuessOutput(
    private val command: String,
    private val guess: SkillWithResult<*>,
    private val yesNo: StandardRecognizerData<UtilYesNo>,
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String =
        ctx.getString(R.string.skill_guess_confirm, command)

    override fun getInteractionPlan(ctx: SkillContext): InteractionPlan {
        val answer = object : RecognizeYesNoSkill(guess.skill.correspondingSkillInfo, yesNo) {
            override suspend fun generateOutput(ctx: SkillContext, inputData: Boolean): SkillOutput =
                if (inputData) guess.generateOutput(ctx)
                else object : HeadlineSpeechSkillOutput {
                    override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(R.string.skill_guess_declined)
                }
        }
        return InteractionPlan.StartSubInteraction(reopenMicrophone = true, nextSkills = listOf(answer))
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        val ask = LocalAsk.current
        Column {
            Headline(text = getSpeechOutput(ctx))
            Spacer(modifier = Modifier.height(12.dp))
            // the command itself, so it lands on its skill directly even after the moment has passed
            FilledTonalButton(onClick = { ask(command) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.skill_guess_do_it))
            }
        }
    }
}
