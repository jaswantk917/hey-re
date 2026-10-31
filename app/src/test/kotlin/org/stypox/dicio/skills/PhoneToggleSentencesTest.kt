package org.stypox.dicio.skills

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.dicio.skill.standard.util.MatchHelper
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.sentences.Sentences.PhoneToggle
import org.stypox.dicio.skills.toggle.Toggle

private val data = Sentences.PhoneToggle["en"]!!

private fun match(input: String) = data.score(MatchHelper(null, input), input)

class PhoneToggleSentencesTest : StringSpec({
    "each switch, in English and Hinglish" {
        mapOf(
            "wifi band karo" to PhoneToggle.WifiOff,
            "turn on wi fi" to PhoneToggle.WifiOn,
            "bluetooth on kar do" to PhoneToggle.BluetoothOn,
            "turn off bluetooth" to PhoneToggle.BluetoothOff,
            "mobile data band kar do" to PhoneToggle.MobileDataOff,
            "internet chalu karo" to PhoneToggle.MobileDataOn,
            "hotspot on karo" to PhoneToggle.HotspotOn,
            "turn off the hotspot" to PhoneToggle.HotspotOff,
            "turn on airplane mode" to PhoneToggle.AirplaneOn,
            "flight mode band karo" to PhoneToggle.AirplaneOff,
            "battery saver on" to PhoneToggle.BatterySaverOn,
            "dark mode chalu kar do" to PhoneToggle.DarkModeOn,
            "switch to light mode" to PhoneToggle.DarkModeOff,
            "location off kar do" to PhoneToggle.LocationOff,
            "gps on karo" to PhoneToggle.LocationOn,
        ).forEach { (input, expected) ->
            match(input).second shouldBe expected
            match(input).first.scoreIn01Range() shouldBeGreaterThan 0.75f
        }
    }

    "lights and the torch stay with their own skills" {
        val home = Sentences.SmartHome["en"]!!
        for (input in listOf("lights off karo", "cooler band karo")) {
            match(input).first.scoreIn01Range() shouldBeLessThan home.score(MatchHelper(null, input), input).first.scoreIn01Range()
        }
        val torch = Sentences.Flashlight["en"]!!
        val input = "torch on karo"
        match(input).first.scoreIn01Range() shouldBeLessThan torch.score(MatchHelper(null, input), input).first.scoreIn01Range()
    }

    "the hotspot opens its page, the rest switch through Shizuku" {
        Toggle.Hotspot.command(true) shouldBe null
        Toggle.Hotspot.command(false) shouldBe null
        for (toggle in Toggle.entries - Toggle.Hotspot) {
            toggle.command(true) shouldNotBe null
            toggle.command(false) shouldNotBe null
        }
    }
})
