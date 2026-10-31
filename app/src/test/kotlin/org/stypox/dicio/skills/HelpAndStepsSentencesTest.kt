package org.stypox.dicio.skills

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.util.MatchHelper
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.sentences.Sentences.CheckIn
import org.stypox.dicio.sentences.Sentences.Help

private fun <T> StandardRecognizerData<T>.match(input: String) = score(MatchHelper(null, input), input)

// what the other skills make of it, for "it goes to help/steps and not there"
private val others = listOf(
    Sentences.Calculator["en"]!!, Sentences.SmartHome["en"]!!, Sentences.Alarm["en"]!!, Sentences.Timer["en"]!!,
    Sentences.Weather["en"]!!, Sentences.CurrentTime["en"]!!, Sentences.Search["en"]!!,
)

private fun bestOther(input: String) = others.maxOf { it.match(input).first.scoreIn01Range() }

class HelpAndStepsSentencesTest : StringSpec({
    "what can you do is help" {
        val help = Sentences.Help["en"]!!
        for (input in listOf("what can you do", "what all can you do for me", "how can you help me", "help")) {
            help.match(input).second shouldBe Help.English
            help.match(input).first.scoreIn01Range() shouldBeGreaterThan bestOther(input)
            help.match(input).first.scoreIn01Range() shouldBeGreaterThan 0.9f
        }
        for (input in listOf("tum kya kya kar sakte ho", "aap kya kar sakti ho", "tumhe kya aata hai")) {
            help.match(input).second shouldBe Help.Hinglish
            help.match(input).first.scoreIn01Range() shouldBeGreaterThan 0.9f
        }
    }

    "step questions are just the steps" {
        val checkIn = Sentences.CheckIn["en"]!!
        for (input in listOf("how many steps today", "how many steps have i walked today", "what s my step count")) {
            withClue(input) {
                checkIn.match(input).second shouldBe CheckIn.Steps
                checkIn.match(input).first.scoreIn01Range() shouldBeGreaterThan 0.9f
            }
        }
        for (input in listOf("kitna steps ho gaya aaj", "aaj kitne steps hue", "maine kitna chala aaj", "aaj ke steps batao")) {
            withClue(input) {
                checkIn.match(input).second shouldBe CheckIn.StepsHinglish
                checkIn.match(input).first.scoreIn01Range() shouldBeGreaterThan 0.9f
            }
        }
    }

    "the time and greetings stay where they were" {
        val checkIn = Sentences.CheckIn["en"]!!
        val time = Sentences.CurrentTime["en"]!!
        checkIn.match("what time is it").first.scoreIn01Range() shouldBeLessThan
                time.match("what time is it").first.scoreIn01Range()
        checkIn.match("hello how are you").second shouldBe CheckIn.English
        checkIn.match("kaise ho").second shouldBe CheckIn.Hinglish
    }
})
