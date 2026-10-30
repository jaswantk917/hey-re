package org.stypox.dicio.skills.home

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.dicio.numbers.ParserFormatter
import org.dicio.skill.standard.util.MatchHelper
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.sentences.Sentences.SmartHome
import org.stypox.dicio.util.Hinglish
import org.stypox.dicio.util.HinglishNumbers
import java.util.Locale

private val data = Sentences.SmartHome["en"]!!

private fun match(input: String): SmartHome = data.score(MatchHelper(null, input), input).second

class HomeSentencesTest : StringSpec({
    "Hinglish" {
        match("cooler band karo").shouldBeInstanceOf<SmartHome.TurnOff>().what?.trim() shouldBe "cooler"
        match("cooler chalu kar do").shouldBeInstanceOf<SmartHome.TurnOn>().what?.trim() shouldBe "cooler"
        match("bedroom ki light jala do").shouldBeInstanceOf<SmartHome.TurnOn>().what?.trim() shouldBe "bedroom ki light"
        match("hall light off kar do").shouldBeInstanceOf<SmartHome.TurnOff>().what?.trim() shouldBe "hall light"
        match("sab band karo").shouldBeInstanceOf<SmartHome.TurnOff>().what?.trim() shouldBe "sab"
    }

    "English" {
        match("turn off the hall light").shouldBeInstanceOf<SmartHome.TurnOff>().what?.trim() shouldBe "hall light"
        match("switch on the cooler").shouldBeInstanceOf<SmartHome.TurnOn>().what?.trim() shouldBe "cooler"
        match("turn the bedroom light on").shouldBeInstanceOf<SmartHome.TurnOn>().what?.trim() shouldBe "bedroom light"
    }

    "later, as a sleep timer" {
        val parser = ParserFormatter(Locale.ENGLISH)
        fun later(input: String): SmartHome {
            val normalized = HinglishNumbers.normalize(Hinglish.romanize(input))
            return data.score(MatchHelper(parser, normalized), normalized).second
        }
        later("1 ghanta baad cooler band kar dena").shouldBeInstanceOf<SmartHome.TurnOffLater>().let {
            it.what?.trim() shouldBe "cooler"
            it.delay?.toJavaDuration() shouldBe java.time.Duration.ofHours(1)
        }
        later("turn off the cooler in 30 minutes").shouldBeInstanceOf<SmartHome.TurnOffLater>()
            .delay?.toJavaDuration() shouldBe java.time.Duration.ofMinutes(30)
        later("एक घंटे बाद कूलर बंद कर देना").shouldBeInstanceOf<SmartHome.TurnOffLater>().what?.trim() shouldBe "cooler"
        later("लाइट्स बंद करो").shouldBeInstanceOf<SmartHome.TurnOff>().what?.trim() shouldBe "lights"
    }
})
