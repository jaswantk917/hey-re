package org.stypox.dicio.io.input.ondevice

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TranscriptTest : StringSpec({
    "words grow without a pause" {
        val transcript = Transcript()
        transcript.partial("mujhe")
        transcript.partial("mujhe ek movie") shouldBe "mujhe ek movie"
        transcript.final("mujhe ek movie batao") shouldBe "mujhe ek movie batao"
    }

    "talking again after a pause keeps what came before" {
        val transcript = Transcript()
        transcript.partial("mujhe ek movie batao")
        transcript.pause()
        transcript.partial("jo funny ho") shouldBe "mujhe ek movie batao jo funny ho"
        transcript.partial("jo funny ho aur short") shouldBe "mujhe ek movie batao jo funny ho aur short"
        transcript.heard shouldBe "mujhe ek movie batao jo funny ho aur short"
    }

    "the final words only after the pause get what came before" {
        val transcript = Transcript()
        transcript.partial("mujhe ek movie batao")
        transcript.pause()
        transcript.partial("jo funny ho")
        transcript.final("jo funny ho") shouldBe "mujhe ek movie batao jo funny ho"
    }

    "the final words of the whole listen are kept as they are" {
        val transcript = Transcript()
        transcript.partial("mujhe ek movie batao")
        transcript.pause()
        transcript.partial("jo funny ho")
        transcript.final("Mujhe ek movie batao jo funny ho") shouldBe "Mujhe ek movie batao jo funny ho"
    }

    "a recognizer that keeps its words across the pause is not doubled" {
        val transcript = Transcript()
        transcript.partial("call papa")
        transcript.pause()
        transcript.partial("call papa on whatsapp") shouldBe "call papa on whatsapp"
        transcript.final("call papa on whatsapp") shouldBe "call papa on whatsapp"
    }

    "a pause with nothing said after it changes nothing" {
        val transcript = Transcript()
        transcript.partial("set a timer")
        transcript.pause()
        transcript.final("set a timer for 5 minutes") shouldBe "set a timer for 5 minutes"
    }

    "several pauses" {
        val transcript = Transcript()
        transcript.partial("one")
        transcript.pause()
        transcript.partial("two")
        transcript.pause()
        transcript.partial("three") shouldBe "one two three"
    }

    "nothing heard" {
        Transcript().heard shouldBe null
    }
})
