package org.stypox.dicio.eval

import android.content.Context
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.dicio.numbers.ParserFormatter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.context.SpeechOutputDevice
import org.dicio.skill.skill.FloatScore
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.Score
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity
import org.dicio.skill.standard.util.MatchHelper
import org.stypox.dicio.MockSkillInfo
import org.stypox.dicio.mocked
import org.stypox.dicio.sentences.Sentences
import java.util.Locale

private class AnswerContext(input: String) : SkillContext {
    override val android: Context get() = mocked()
    override val locale: Locale get() = mocked()
    override val sentencesLanguage: String get() = "en"
    override val parserFormatter: ParserFormatter? get() = null
    override val speechOutputDevice: SpeechOutputDevice get() = mocked()
    override val previousOutput: SkillOutput? get() = null
    override val standardMatchHelper: MatchHelper = MatchHelper(null, input)
}

/** "flight mode on", as Nano guessed it: counts how often it really ran. */
private class Acting : Skill<Unit>(MockSkillInfo, Specificity.HIGH) {
    var ran = 0
    val output = object : SkillOutput {
        override fun getSpeechOutput(ctx: SkillContext) = "done"
        @androidx.compose.runtime.Composable override fun GraphicalOutput(ctx: SkillContext) {}
    }
    override fun score(ctx: SkillContext, input: String): Pair<Score, Unit> = Pair(FloatScore(1f), Unit)
    override suspend fun generateOutput(ctx: SkillContext, inputData: Unit): SkillOutput {
        ran += 1
        return output
    }
}

private suspend fun answer(input: String, acting: Acting): SkillOutput {
    val guess = confirmGuess("turn on airplane mode", SkillWithResult(acting, FloatScore(1f), Unit), Sentences.UtilYesNo["en"]!!)
    val ctx = AnswerContext(input)
    val question = guess.generateOutput(ctx)
    acting.ran shouldBe 0
    val plan = question.getInteractionPlan(ctx).shouldBeInstanceOf<InteractionPlan.StartSubInteraction>()
    plan.reopenMicrophone shouldBe true
    val yesNo = plan.nextSkills.single()
    @Suppress("UNCHECKED_CAST")
    yesNo as Skill<Any?>
    return yesNo.generateOutput(ctx, yesNo.score(ctx, input).second)
}

class ConfirmGuessTest : StringSpec({
    "a guess that acts waits for a yes" {
        val acting = Acting()
        answer("haan", acting) shouldBeSameInstanceAs acting.output
        acting.ran shouldBe 1
    }

    "a no leaves the phone alone" {
        for (input in listOf("nahi", "no")) {
            val acting = Acting()
            answer(input, acting)
            acting.ran shouldBe 0
        }
    }
})
