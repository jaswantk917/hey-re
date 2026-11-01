package org.stypox.dicio.history

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.nio.file.Files

private fun turn(skill: String, question: String? = "q", answer: String = "a") = SavedTurn(question, answer, skill)
private fun chat(id: Long, updatedAt: Long = id, vararg turns: SavedTurn) = SavedChat(id, updatedAt, turns.toList())

class ChatHistoryTest : StringSpec({
    "flipping switches and setting timers isn't a conversation" {
        ChatHistory.isMeaningful(listOf(turn("flashlight"), turn("timer"), turn("phone_toggle"))) shouldBe false
    }

    "a question Nano answered, or an image, is" {
        ChatHistory.isMeaningful(listOf(turn("flashlight"), turn("local_brain"))) shouldBe true
        ChatHistory.isMeaningful(listOf(turn("image", question = null))) shouldBe true
    }

    "an answer with nothing in it doesn't make a conversation" {
        ChatHistory.isMeaningful(listOf(turn("local_brain", answer = " "))) shouldBe false
    }

    "a chat is titled by its first question, or its first answer when it began with an image" {
        chat(1, 1, turn("timer", question = null), turn("local_brain", question = "kal mausam kaisa hai")).title shouldBe
                "kal mausam kaisa hai"
        chat(1, 1, turn("image", question = null, answer = "An electricity bill")).title shouldBe "An electricity bill"
    }

    "saving again replaces the chat and moves it to the top" {
        val old = listOf(chat(2, 20), chat(1, 10))
        val (kept, dropped) = ChatHistory.upsert(old, chat(1, 30, turn("local_brain")))
        kept.map { it.id } shouldBe listOf(1L, 2L)
        kept[0].turns.size shouldBe 1
        dropped.shouldBeEmpty()
    }

    "past the limit the oldest chats are let go" {
        val full = (1L..ChatHistory.MAX_CHATS).map { chat(it) }
        val (kept, dropped) = ChatHistory.upsert(full, chat(1000))
        kept.size shouldBe ChatHistory.MAX_CHATS
        kept.first().id shouldBe 1000L
        dropped.map { it.id } shouldBe listOf(1L)
    }

    "chats survive a restart, and only conversations are kept" {
        val dir = Files.createTempDirectory("history").toFile()
        val history = ChatHistory(dir)
        history.save(chat(1, 1, turn("local_brain", answer = "Haan, kal baarish hogi")), mapOf())
        history.save(chat(2, 2, turn("flashlight")), mapOf())

        val afterRestart = ChatHistory(dir).load()
        afterRestart.map { it.id } shouldBe listOf(1L)
        afterRestart[0].turns[0].answer shouldBe "Haan, kal baarish hogi"

        history.delete(1)
        ChatHistory(dir).load().shouldBeEmpty()
        dir.deleteRecursively()
    }
})
