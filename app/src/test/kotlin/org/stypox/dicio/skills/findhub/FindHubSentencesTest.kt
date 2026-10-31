package org.stypox.dicio.skills.findhub

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.dicio.skill.standard.util.MatchHelper
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.sentences.Sentences.FindHub as Said

private fun match(input: String) = Sentences.FindHub["en"]!!.score(MatchHelper(null, input), input).second

class FindHubSentencesTest : StringSpec({
    "where things are" {
        match("meri chabi kahan hai").shouldBeInstanceOf<Said.Where>().item?.trim() shouldBe "chabi"
        match("where are my keys").shouldBeInstanceOf<Said.Where>().item?.trim() shouldBe "keys"
        match("where did i put my passport").shouldBeInstanceOf<Said.Where>().item?.trim() shouldBe "passport"
    }

    "what to remember and where it is" {
        val english = match("remember my passport is in the almirah").shouldBeInstanceOf<Said.Remember>()
        english.item?.trim() shouldBe "passport"
        english.place?.trim() shouldBe "in the almirah"
    }

    "a Hinglish place is split off where it starts" {
        val hinglish = match("yaad rakhna ki chabi drawer mein hai").shouldBeInstanceOf<Said.Remember>()
        FindHubSkill.splitPlace(hinglish.item!!.trim()) shouldBe ("chabi" to "drawer mein")
        FindHubSkill.splitPlace("car ki chabi almari ke upar") shouldBe ("car ki chabi" to "almari ke upar")
        FindHubSkill.splitPlace("passport bag mein") shouldBe ("passport" to "bag mein")
        FindHubSkill.splitPlace("chabi") shouldBe null
    }

    "ringing a device" {
        match("ring my watch").shouldBeInstanceOf<Said.Ring>().item?.trim() shouldBe "watch"
        match("stop ringing my watch").shouldBeInstanceOf<Said.StopRing>().item?.trim() shouldBe "watch"
    }
})
