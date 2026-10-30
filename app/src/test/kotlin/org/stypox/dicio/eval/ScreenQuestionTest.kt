package org.stypox.dicio.eval

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.stypox.dicio.eval.SkillEvaluatorImpl.Companion.SCREEN_QUESTION
import org.stypox.dicio.eval.SkillEvaluatorImpl.Companion.SUMMARY_REQUEST

class ScreenQuestionTest : StringSpec({
    "questions about the screen" {
        for (input in listOf(
            "summarize this", "Summarise it", "what's this", "what is on my screen",
            "yeh kya hai", "isme kya likha hai", "explain this", "translate this",
            "is message ka matlab kya hai", "isko samjhao",
        )) {
            SCREEN_QUESTION.containsMatchIn(input) shouldBe true
        }
    }

    "ordinary commands that happen to say this" {
        for (input in listOf(
            "set an alarm for this evening", "call Maa", "play this song by Arijit",
            "what time is it", "tell me a joke", "Maa ko bolo ki main aa raha hoon",
        )) {
            SCREEN_QUESTION.containsMatchIn(input) shouldBe false
        }
    }

    "a summary request can use the summary made on summon" {
        SUMMARY_REQUEST.containsMatchIn("summarize this") shouldBe true
        SUMMARY_REQUEST.containsMatchIn("ye kya hai") shouldBe true
        SUMMARY_REQUEST.containsMatchIn("explain this") shouldBe false
    }
})
