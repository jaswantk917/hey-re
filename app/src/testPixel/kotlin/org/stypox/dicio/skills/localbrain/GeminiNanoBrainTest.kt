package org.stypox.dicio.skills.localbrain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.stypox.dicio.io.assist.ScreenSnapshot

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

    "history goes before the new message, oldest first" {
        withHistory(
            "haan karo",
            listOf(LocalBrain.Turn("mummy ko call karo", "Should I call Maa?")),
        ) shouldBe """
            Conversation so far:
            Person: mummy ko call karo
            You: Should I call Maa?
            Person now says: haan karo
        """.trimIndent()
    }

    "no history leaves the message as it is" {
        withHistory("what time is it", listOf()) shouldBe "what time is it"
    }

    "the label it copies from the conversation is taken off" {
        cleanAnswer("You: I am sorry, I couldn't help.") shouldBe "I am sorry, I couldn't help."
        cleanAnswer("you : You: Try Goa in winter.") shouldBe "Try Goa in winter."
        cleanAnswer("Your best bet is Goa.") shouldBe "Your best bet is Goa."
        cleanAnswer("You:") shouldBe null
    }

    "a repeat of an earlier answer is caught, label or not" {
        sameAnswer("You: I couldn't calculate your request.", "I couldn't calculate your request") shouldBe true
        sameAnswer("Try Goa in winter.", "Try Manali in summer.") shouldBe false
    }

    "the screen goes in with the app it came from" {
        screenContext(ScreenSnapshot("com.whatsapp", "WhatsApp", "Maa: khana kha liya?")) shouldBe
            "What is on their screen right now (in WhatsApp), for when they ask about \"this\" or the screen:\n" +
            "Maa: khana kha liya?"
    }
})
