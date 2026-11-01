package org.stypox.dicio.memory

import android.graphics.Bitmap
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.stypox.dicio.history.ChatHistory
import org.stypox.dicio.history.SavedChat
import org.stypox.dicio.history.SavedTurn
import org.stypox.dicio.io.assist.Article
import org.stypox.dicio.io.assist.ScreenSnapshot
import org.stypox.dicio.skills.localbrain.LocalBrain
import java.nio.file.Files

/** Learns one fact per chat from its first message; null (Nano gone) once [failAfter] chats were read. */
private class FakeBrain(private val failAfter: Int = Int.MAX_VALUE) : LocalBrain {
    val readMessages = mutableListOf<List<String>>()
    override suspend fun isReady() = true
    override suspend fun rewriteAsCommand(input: String, commands: List<String>, recent: List<LocalBrain.Turn>) = null
    override suspend fun answer(input: String, recent: List<LocalBrain.Turn>, screen: ScreenSnapshot?, memory: List<String>) = null
    override suspend fun summarizeScreen(screen: ScreenSnapshot) = null
    override suspend fun summarizeArticle(article: Article.Page) = null
    override suspend fun describeImage(image: Bitmap, text: String?) = null
    override suspend fun rememberFrom(messages: List<String>, known: List<String>): List<String>? {
        if (readMessages.size >= failAfter) return null
        readMessages += messages
        return listOf("They said: ${messages.first()}")
    }
    override suspend fun compactMemory(facts: List<String>) = facts.take(facts.size - 1)
}

private fun chat(id: Long, question: String) =
    SavedChat(id, id, listOf(SavedTurn(question, "Sure, here's what I think.", "local_brain")))

class MemoryLearnerTest : StringSpec({
    suspend fun setUp(brain: FakeBrain, vararg chats: SavedChat): Pair<MemoryLearner, Memory> {
        val dir = Files.createTempDirectory("learner").toFile().also { it.deleteOnExit() }
        val history = ChatHistory(dir.resolve("history"))
        chats.forEach { history.save(it, mapOf()) }
        val memory = Memory(dir.resolve("memory"))
        return Pair(MemoryLearner(memory, history, brain), memory)
    }

    "finished chats are read once, oldest first, and the one still going is left alone" {
        val brain = FakeBrain()
        val (learner, memory) = setUp(brain, chat(10, "my sister lives in pune"), chat(20, "i like tea"), chat(30, "still talking"))

        learner.learn(currentChatId = 30) shouldBe MemoryLearner.Result.Learned(chats = 2, facts = 2)
        brain.readMessages shouldBe listOf(listOf("my sister lives in pune"), listOf("i like tea"))
        memory.digestedUpTo() shouldBe 20L

        learner.learn(currentChatId = 30) shouldBe MemoryLearner.Result.Learned(chats = 0, facts = 0)
    }

    "only the person's words are read, never the answers" {
        val brain = FakeBrain()
        val (learner, _) = setUp(brain, chat(10, "my office is in pune"))
        learner.learn(currentChatId = 0)
        brain.readMessages.flatten().none { it.contains("here's what I think") } shouldBe true
    }

    "when Nano drops out midway, what was read is kept and the rest waits" {
        val brain = FakeBrain(failAfter = 1)
        val (learner, memory) = setUp(brain, chat(10, "first"), chat(20, "second"))

        learner.learn(currentChatId = 0) shouldBe MemoryLearner.Result.Interrupted(chats = 1, facts = 1)
        memory.digestedUpTo() shouldBe 10L
    }

    "the person asking something stops the reading" {
        val (learner, memory) = setUp(FakeBrain(), chat(10, "first"))
        learner.learn(currentChatId = 0) { true } shouldBe MemoryLearner.Result.Interrupted(chats = 0, facts = 0)
        memory.facts.value.size shouldBe 0
    }

    "facts can be added and corrected by hand" {
        val (_, memory) = setUp(FakeBrain())
        memory.add("They prefer tea to coffee.", now = 1)
        memory.add("they prefer tea to coffee", now = 2)
        memory.facts.value.size shouldBe 1
        memory.edit(memory.facts.value[0], "They prefer coffee now.")
        memory.facts.value.map { it.text } shouldBe listOf("They prefer coffee now.")
    }
})
