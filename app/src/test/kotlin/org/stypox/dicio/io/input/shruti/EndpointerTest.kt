package org.stypox.dicio.io.input.shruti

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.stypox.dicio.io.input.shruti.Endpointer.Heard

private const val FRAME = 100
private const val ROOM = 0.004f
private const val VOICE = 0.08f

private fun Endpointer.feed(rms: Float, millis: Int): Heard {
    var heard = Heard.Waiting
    repeat(millis / FRAME) { heard = frame(rms, FRAME) }
    return heard
}

class EndpointerTest : StringSpec({
    "waits while nobody talks" {
        val endpointer = Endpointer(silenceMillis = 3000)
        endpointer.feed(ROOM, 2000) shouldBe Heard.Waiting
        endpointer.speechBegan shouldBe false
    }

    "talking, then the full silence, is done" {
        val endpointer = Endpointer(silenceMillis = 3000)
        endpointer.feed(ROOM, 500)
        endpointer.feed(VOICE, 1500) shouldBe Heard.Talking
        endpointer.feed(ROOM, 2900) shouldBe Heard.Talking
        endpointer.feed(ROOM, 100) shouldBe Heard.Done
    }

    "a pause shorter than the silence keeps listening" {
        val endpointer = Endpointer(silenceMillis = 3000)
        endpointer.feed(ROOM, 500)
        endpointer.feed(VOICE, 1000)
        endpointer.feed(ROOM, 2000) shouldBe Heard.Talking
        endpointer.feed(VOICE, 1000) shouldBe Heard.Talking
        endpointer.feed(ROOM, 2900) shouldBe Heard.Talking
        endpointer.feed(ROOM, 100) shouldBe Heard.Done
    }

    "a click is not speech" {
        val endpointer = Endpointer(silenceMillis = 3000)
        endpointer.feed(ROOM, 500)
        endpointer.feed(VOICE, 100)
        endpointer.feed(ROOM, 500) shouldBe Heard.Waiting
        endpointer.speechBegan shouldBe false
    }

    "talking from the very first moment is still heard" {
        val endpointer = Endpointer(silenceMillis = 3000)
        repeat(10) {
            endpointer.feed(VOICE, 300)
            endpointer.feed(ROOM, 100)
        }
        endpointer.speechBegan shouldBe true
    }

    "nothing said at all ends after the wait" {
        val endpointer = Endpointer(silenceMillis = 3000)
        endpointer.feed(ROOM, Endpointer.MAX_WAIT_MILLIS - FRAME) shouldBe Heard.Waiting
        endpointer.feed(ROOM, FRAME) shouldBe Heard.Nothing
    }

    "a steady noisy room is not speech, a voice over it is" {
        val endpointer = Endpointer(silenceMillis = 3000)
        endpointer.feed(0.03f, 3000) shouldBe Heard.Waiting
        endpointer.feed(0.2f, 500) shouldBe Heard.Talking
    }

    "someone who never stops is cut off eventually" {
        val endpointer = Endpointer(silenceMillis = 3000)
        endpointer.feed(ROOM, 500)
        endpointer.feed(VOICE, Endpointer.MAX_LISTEN_MILLIS) shouldBe Heard.Done
    }
})
