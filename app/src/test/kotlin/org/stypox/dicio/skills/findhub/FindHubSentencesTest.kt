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

    "ringing a device" {
        match("ring my watch").shouldBeInstanceOf<Said.Ring>().item?.trim() shouldBe "watch"
        match("stop ringing my watch").shouldBeInstanceOf<Said.StopRing>().item?.trim() shouldBe "watch"
    }
})
