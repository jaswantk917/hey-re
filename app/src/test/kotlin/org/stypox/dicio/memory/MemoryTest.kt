package org.stypox.dicio.memory

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.nio.file.Files

private fun fact(text: String, usedAt: Long = 0, savedAt: Long = usedAt) = Fact(text, null, savedAt, usedAt)

class MemoryTest : StringSpec({
    "facts sharing words with the question come first, then the most recently used" {
        val facts = listOf(
            fact("They prefer tea to coffee.", usedAt = 50),
            fact("Their sister lives in Pune.", usedAt = 10),
            fact("They work night shifts on weekends.", usedAt = 90),
        )
        Memory.relevant(facts, "meri sister kahan rehti hai").map { it.text } shouldContainExactly listOf(
            "Their sister lives in Pune.", "They work night shifts on weekends.", "They prefer tea to coffee.",
        )
    }

    "a question in Devanagari finds a fact written in English" {
        Memory.overlap(fact("They visit Pune every month."), "पुणे कब जाना है") shouldBe 1
        Memory.overlap(fact("Their office is in Pune."), "office kab jana hai") shouldBe 1
    }

    "where something is comes from the facts that name it" {
        val facts = listOf(fact("Passport almari mein hai", savedAt = 1), fact("They prefer tea to coffee.", savedAt = 2))
        Memory.mentioning(facts, "passport") shouldBe listOf("Passport almari mein hai")
        Memory.mentioning(facts, "watch") shouldBe listOf()
    }

    "small words never count as a match" {
        Memory.overlap(fact("They are a fan of the cricket team."), "what is the time") shouldBe 0
    }

    "only so many facts go in front of a question" {
        val many = (1..30).map { fact("Fact number $it about them.", usedAt = it.toLong()) }
        Memory.relevant(many, "anything").size shouldBe Memory.MAX_RELEVANT
    }

    "past the cap the least recently used facts go" {
        val many = (1..Memory.MAX_FACTS + 5).map { fact("Fact $it", usedAt = it.toLong(), savedAt = it.toLong()) }
        val kept = Memory.capped(many)
        kept.size shouldBe Memory.MAX_FACTS
        kept.first().text shouldBe "Fact 6"
    }

    "the model's list loses bullets, numbering, questions and NONE" {
        Memory.parseFacts("- Their sister lives in Pune.\n2. They prefer tea.\nWhat do they do?\n\n", 3) shouldBe
                listOf("Their sister lives in Pune.", "They prefer tea.")
        Memory.parseFacts("NONE", 3) shouldBe listOf()
        Memory.parseFacts("NONE.", 3) shouldBe listOf()
        Memory.parseFacts("a\nb\nc\nd\ne".replace(Regex("\\w"), "Long enough fact"), 3).size shouldBe 1
    }

    "learning skips what's already known and survives a restart" {
        val dir = Files.createTempDirectory("memory").toFile()
        val memory = Memory(dir)
        memory.learn(listOf("Their sister lives in Pune."), fromChat = 1, chatUpdatedAt = 100, now = 100)
        memory.learn(listOf("their sister lives in Pune", "They prefer tea to coffee."), fromChat = 2, chatUpdatedAt = 200, now = 200)

        val again = Memory(dir)
        again.load()
        again.facts.value.map { it.text } shouldBe listOf("Their sister lives in Pune.", "They prefer tea to coffee.")
        again.digestedUpTo() shouldBe 200L

        again.forget(again.facts.value[0])
        Memory(dir).also { it.load() }.facts.value.size shouldBe 1
        dir.deleteRecursively()
    }

    "a merged rewrite keeps a fact's history when its words didn't change" {
        val dir = Files.createTempDirectory("memory").toFile()
        val memory = Memory(dir)
        memory.learn(listOf("They prefer tea to coffee.", "They use Chrome."), fromChat = 1, chatUpdatedAt = 1, now = 1)
        memory.replaceWith(listOf("They prefer tea to coffee.", "They use Helium now."), now = 5)
        memory.facts.value.map { it.savedAt } shouldBe listOf(1L, 5L)
        dir.deleteRecursively()
    }
})
