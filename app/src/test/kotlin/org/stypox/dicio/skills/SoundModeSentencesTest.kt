package org.stypox.dicio.skills

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.dicio.skill.standard.util.MatchHelper
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.sentences.Sentences.SoundMode

private val data = Sentences.SoundMode["en"]!!

private fun match(input: String) = data.score(MatchHelper(null, input), input)

class SoundModeSentencesTest : StringSpec({
    "each mode" {
        match("phone silent kar do").second shouldBe SoundMode.Silent
        match("put my phone on silent").second shouldBe SoundMode.Silent
        match("vibrate pe daal do").second shouldBe SoundMode.Vibrate
        match("unmute the phone").second shouldBe SoundMode.Ring
        match("silent hata do").second shouldBe SoundMode.Ring
        match("dnd on karo").second shouldBe SoundMode.DndOn
        match("turn off do not disturb").second shouldBe SoundMode.DndOff
    }

    "the home skill's band karo isn't taken" {
        val home = Sentences.SmartHome["en"]!!
        val input = "cooler band karo"
        match(input).first.scoreIn01Range() shouldBeLessThan home.score(MatchHelper(null, input), input).first.scoreIn01Range()
    }
})
