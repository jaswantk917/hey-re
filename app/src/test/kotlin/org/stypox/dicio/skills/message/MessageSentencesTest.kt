package org.stypox.dicio.skills.message

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.dicio.skill.standard.util.MatchHelper
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.sentences.Sentences.Message

private val data = Sentences.Message["en"]!!

private fun match(input: String): Pair<Float, Message> =
    data.score(MatchHelper(null, input), input).let { (score, result) -> Pair(score.scoreIn01Range(), result) }

class MessageSentencesTest : StringSpec({
    "English with the message" {
        val (_, result) = match("send a message to Maa saying I'm on my way")
        result.shouldBeInstanceOf<Message.Send>()
        result.who?.trim() shouldBe "Maa"
        result.message?.trim() shouldBe "I'm on my way"

        val (_, tell) = match("tell Rahul that the meeting moved")
        tell.shouldBeInstanceOf<Message.Send>()
        tell.who?.trim() shouldBe "Rahul"
        tell.message?.trim() shouldBe "the meeting moved"
    }

    "Hinglish with the message" {
        val (_, result) = match("Maa ko message bhejo ki main late hoon")
        result.shouldBeInstanceOf<Message.Send>()
        result.who?.trim() shouldBe "Maa"
        result.message?.trim() shouldBe "main late hoon"

        val (_, bolo) = match("papa ko bolo ki khana kha liya")
        bolo.shouldBeInstanceOf<Message.Send>()
        bolo.who?.trim() shouldBe "papa"
        bolo.message?.trim() shouldBe "khana kha liya"
    }

    "without the message it asks for one" {
        match("message Maa").second.shouldBeInstanceOf<Message.Ask>()
        match("Maa ko whatsapp karo").second.shouldBeInstanceOf<Message.Ask>()
        (match("Maa ko whatsapp karo").second as Message.Ask).who?.trim() shouldBe "Maa"
    }

    "Slack by name" {
        val (_, result) = match("slack Rahul saying deploy is done")
        result.shouldBeInstanceOf<Message.Slack>()
        result.who?.trim() shouldBe "Rahul"
        result.message?.trim() shouldBe "deploy is done"

        val (_, hinglish) = match("Rahul ko slack pe bolo ki build ready hai")
        hinglish.shouldBeInstanceOf<Message.Slack>()
        hinglish.message?.trim() shouldBe "build ready hai"
    }

    "other skills win their own requests" {
        for ((input, other) in listOf(
            "tell me a joke" to Sentences.Joke["en"]!!,
            "set a timer for 5 minutes" to Sentences.Timer["en"]!!,
            "paanch minute ka timer laga do" to Sentences.Timer["en"]!!,
            "call Maa" to Sentences.Telephone["en"]!!,
            "Maa ko call karo" to Sentences.Telephone["en"]!!,
        )) {
            val otherScore = other.score(MatchHelper(null, input), input).first.scoreIn01Range()
            match(input).first shouldBeLessThan otherScore
        }
    }
})
