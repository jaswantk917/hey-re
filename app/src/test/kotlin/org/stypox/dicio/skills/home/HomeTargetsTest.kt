package org.stypox.dicio.skills.home

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.stypox.dicio.skills.home.HomeTargets.Match

private val bedroom = HomeTarget("1", "Bedroom light", "Bedroom", isLight = true)
private val hall = HomeTarget("2", "Hall light", "Hall", isLight = true)
private val cooler = HomeTarget("3", "Cooler", "Bedroom", isLight = false)
private val home = listOf(bedroom, hall, cooler)

private fun found(what: String) = (HomeTargets.resolve(what, home) as Match.Found).targets

class HomeTargetsTest : StringSpec({
    "a device by its name" {
        found("bedroom light") shouldBe listOf(bedroom)
        found("bedroom ki light") shouldBe listOf(bedroom)
        found("the hall lights") shouldBe listOf(hall)
        found("hall ki batti") shouldBe listOf(hall)
        found("cooler") shouldBe listOf(cooler)
        found("bed room light") shouldBe listOf(bedroom)
    }

    "light alone is ambiguous with two lights" {
        val match = HomeTargets.resolve("light", home)
        match.shouldBeInstanceOf<Match.Ambiguous>()
        match.candidates shouldBe listOf(bedroom, hall)
    }

    "everything, all lights, all in a room" {
        found("sab") shouldBe home
        found("everything") shouldBe home
        found("saari lights") shouldBe listOf(bedroom, hall)
        found("lights") shouldBe listOf(bedroom, hall)
        found("google home se lights") shouldBe listOf(bedroom, hall)
        found("sara light") shouldBe listOf(bedroom, hall)
        found("all the lights") shouldBe listOf(bedroom, hall)
        found("bedroom ka sab") shouldBe listOf(bedroom, cooler)
    }

    "nothing like it" {
        HomeTargets.resolve("kitchen fan", home) shouldBe Match.None
    }

    "only device-like words claim the request" {
        HomeTargets.mayBeDevice("cooler", home) shouldBe true
        HomeTargets.mayBeDevice("bedroom ki light", home) shouldBe true
        HomeTargets.mayBeDevice("sab", home) shouldBe true
        HomeTargets.mayBeDevice("timer", home) shouldBe false
        HomeTargets.mayBeDevice("music", home) shouldBe false
        HomeTargets.mayBeDevice("flashlight", home) shouldBe false
        // before the first fetch, generic words still work
        HomeTargets.mayBeDevice("hall light", listOf()) shouldBe true
        HomeTargets.mayBeDevice("timer", listOf()) shouldBe false
        HomeTargets.mayBeDevice("cooler", listOf()) shouldBe true
    }
})
