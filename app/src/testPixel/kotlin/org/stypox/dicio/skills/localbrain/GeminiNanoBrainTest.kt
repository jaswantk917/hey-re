package org.stypox.dicio.skills.localbrain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class GeminiNanoBrainTest : StringSpec({
    "plain command" {
        parseCommandReply("set a timer for 5 minutes") shouldBe "set a timer for 5 minutes"
    }

    "quotes, bullets and trailing period are dropped" {
        parseCommandReply("\"what time is it\"") shouldBe "what time is it"
        parseCommandReply("“turn on the flashlight”") shouldBe "turn on the flashlight"
        parseCommandReply("- tell me a joke.") shouldBe "tell me a joke"
        parseCommandReply("* weather in Patna") shouldBe "weather in Patna"
    }

    "only the first non-empty line counts" {
        parseCommandReply("\n  \nset a timer for 2 minutes\nThis matches the timer command") shouldBe
            "set a timer for 2 minutes"
    }

    "NONE in any case means no command" {
        parseCommandReply("NONE") shouldBe null
        parseCommandReply("none.") shouldBe null
        parseCommandReply("  \"None\"  ") shouldBe null
    }

    "blank reply means no command" {
        parseCommandReply("") shouldBe null
        parseCommandReply(" \n ") shouldBe null
    }
})
