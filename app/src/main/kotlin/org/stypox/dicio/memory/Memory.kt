package org.stypox.dicio.memory

import android.content.Context
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.stypox.dicio.util.Hinglish
import java.io.File
import javax.inject.Singleton

@Serializable
data class Fact(
    val text: String,
    // the saved chat it was learned from, or null once merged with others
    val fromChat: Long? = null,
    val savedAt: Long,
    val usedAt: Long = savedAt,
)

@Serializable
private data class MemoryFile(
    val facts: List<Fact> = listOf(),
    // chats last updated up to here have been read for facts
    val digestedUpTo: Long = 0,
)

/**
 * What Hey re has learned about the person over past chats, kept on the phone. Facts only ever
 * come from the person's own words (see [org.stypox.dicio.skills.localbrain.LocalBrain.rememberFrom]),
 * never from the model's answers, so a made-up answer can't become something it "knows".
 */
class Memory(private val dir: File) {
    private val _facts = MutableStateFlow<List<Fact>>(listOf())
    val facts: StateFlow<List<Fact>> = _facts

    // chats last updated up to here have been read for facts
    private val _digestedUpTo = MutableStateFlow(0L)
    val digestedUpTo: StateFlow<Long> = _digestedUpTo

    private val lock = Mutex()
    private var loaded = false
    private val file get() = File(dir, "memory.json")

    suspend fun load() = lock.withLock { loadLocked() }

    suspend fun digestedUpTo(): Long = lock.withLock { loadLocked(); _digestedUpTo.value }

    /** The facts worth putting before [question], most relevant first; the matching ones count as used. */
    suspend fun relevantTo(question: String, now: Long): List<String> = lock.withLock {
        loadLocked()
        val picked = relevant(_facts.value, question)
        val matched = picked.filter { overlap(it, question) > 0 }.toSet()
        if (matched.isNotEmpty()) write(_facts.value.map { if (it in matched) it.copy(usedAt = now) else it })
        picked.map { it.text }
    }

    /**
     * Adds what was learned from chat [fromChat] and moves the digest mark past [chatUpdatedAt];
     * how many facts were new.
     */
    suspend fun learn(texts: List<String>, fromChat: Long, chatUpdatedAt: Long, now: Long): Int = lock.withLock {
        loadLocked()
        val known = _facts.value.map { normalized(it.text) }.toMutableSet()
        val fresh = texts.filter { known.add(normalized(it)) }.map { Fact(it, fromChat, now) }
        _digestedUpTo.value = maxOf(_digestedUpTo.value, chatUpdatedAt)
        write(capped(_facts.value + fresh))
        fresh.size
    }

    /** Facts from the person's imported notes; how many were new. */
    suspend fun addAll(texts: List<String>, now: Long): Int = lock.withLock {
        loadLocked()
        val known = _facts.value.map { normalized(it.text) }.toMutableSet()
        val fresh = texts.filter { known.add(normalized(it)) }.map { Fact(it, null, now) }
        write(capped(_facts.value + fresh))
        fresh.size
    }

    /** A fact the person wrote themselves, in Settings. */
    suspend fun add(text: String, now: Long) = lock.withLock {
        loadLocked()
        if (_facts.value.none { normalized(it.text) == normalized(text) }) write(capped(_facts.value + Fact(text, null, now)))
    }

    /** [fact] corrected by the person; it keeps its place and history. */
    suspend fun edit(fact: Fact, text: String) = lock.withLock {
        loadLocked()
        write(_facts.value.map { if (it == fact) it.copy(text = text) else it })
    }

    /** Swaps the facts for [texts], a merged rewrite of them; a fact kept word for word keeps its history. */
    suspend fun replaceWith(texts: List<String>, now: Long) = lock.withLock {
        loadLocked()
        val old = _facts.value.associateBy { normalized(it.text) }
        write(capped(texts.distinctBy(::normalized).map { old[normalized(it)] ?: Fact(it, null, now) }))
    }

    suspend fun forget(fact: Fact) = lock.withLock {
        loadLocked()
        write(_facts.value.filter { it != fact })
    }

    suspend fun forgetAll() = lock.withLock {
        loadLocked()
        write(listOf())
    }

    private suspend fun loadLocked() {
        if (loaded) return
        val saved = withContext(Dispatchers.IO) {
            try {
                if (file.exists()) json.decodeFromString<MemoryFile>(file.readText()) else MemoryFile()
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the memory", e)
                MemoryFile()
            }
        }
        _facts.value = saved.facts
        _digestedUpTo.value = saved.digestedUpTo
        loaded = true
    }

    private suspend fun write(facts: List<Fact>) {
        _facts.value = facts
        val saved = MemoryFile(facts, _digestedUpTo.value)
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val temp = File(dir, "memory.json.tmp")
            temp.writeText(json.encodeToString(saved))
            temp.renameTo(file)
        }
    }

    companion object {
        private const val TAG = "Memory"
        private val json = Json { ignoreUnknownKeys = true }

        // the model is asked to merge its facts past this many, and the least used go past the cap
        const val COMPACT_FROM = 40
        // from here Settings offers to compact by hand
        const val COMPACT_OFFER_FROM = 15
        internal const val MAX_FACTS = 60
        // what fits beside a question in Nano's small context
        internal const val MAX_RELEVANT = 12

        /** Facts sharing words with [question] first, then the most recently used, up to [MAX_RELEVANT]. */
        internal fun relevant(facts: List<Fact>, question: String): List<Fact> = facts
            .sortedWith(compareByDescending<Fact> { overlap(it, question) }.thenByDescending { it.usedAt })
            .take(MAX_RELEVANT)

        /** The facts that name [item] ("passport", "car ki chabi"), best match first. */
        fun mentioning(facts: List<Fact>, item: String): List<String> = facts
            .map { it to overlap(it, item) }
            .filter { (_, shared) -> shared > 0 }
            .sortedByDescending { (fact, shared) -> shared * 1_000_000_000_000L + fact.savedAt / 1000 }
            .map { (fact, _) -> fact.text }

        internal fun overlap(fact: Fact, question: String): Int {
            val asked = words(question)
            return words(fact.text).count { word -> asked.any { sameWord(it, word) } }
        }

        // "brother" and "brother's", "office" and "offices": the same word for this purpose
        private fun sameWord(a: String, b: String) =
            a == b || (a.length >= STEM && b.length >= STEM && a.take(STEM) == b.take(STEM))

        private fun words(text: String) = Hinglish.romanize(text).lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 2 && it !in STOP_WORDS }
            .toSet()

        private fun normalized(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

        /**
         * [text] cut at line breaks (or sentences, for a long line) into parts of at most
         * [maxChars], each small enough for Nano to read beside what it already knows.
         */
        internal fun chunks(text: String, maxChars: Int): List<String> {
            val pieces = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.flatMap { line ->
                if (line.length <= maxChars) listOf(line)
                else line.split(Regex("(?<=[.!?।])\\s+")).flatMap { it.chunked(maxChars) }
            }
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            for (piece in pieces) {
                if (current.isNotEmpty() && current.length + 1 + piece.length > maxChars) {
                    parts += current.toString()
                    current.clear()
                }
                if (current.isNotEmpty()) current.append('\n')
                current.append(piece)
            }
            if (current.isNotEmpty()) parts += current.toString()
            return parts
        }

        /** Over [MAX_FACTS], the ones used least recently go first. */
        internal fun capped(facts: List<Fact>): List<Fact> =
            if (facts.size <= MAX_FACTS) facts
            else facts.sortedByDescending { it.usedAt }.take(MAX_FACTS).sortedBy { it.savedAt }

        /** The model's list, one fact per line, without bullets, questions or a NONE. */
        internal fun parseFacts(reply: String, max: Int): List<String> = reply.lineSequence()
            .map { it.trim().trimStart('-', '*', '•', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim().trim('"') }
            .filter { it.length in MIN_FACT_CHARS..MAX_FACT_CHARS && !it.endsWith("?") }
            .filterNot { it.equals("NONE", ignoreCase = true) || it.startsWith("NONE", ignoreCase = true) }
            .distinctBy(::normalized)
            .take(max)
            .toList()

        private const val STEM = 5
        private const val MIN_FACT_CHARS = 8
        private const val MAX_FACT_CHARS = 160
        private val STOP_WORDS = setOf(
            "the", "and", "for", "you", "your", "are", "was", "with", "they", "their", "them", "what",
            "how", "can", "who", "this", "that", "have", "has", "about", "tell", "please", "kya", "hai",
            "hain", "mera", "meri", "mere", "mujhe", "kaise", "kab", "kahan", "aur", "bhi", "nahi",
            "tum", "aap", "karo", "kar", "batao", "hoga", "tha", "thi", "wala", "wali", "liye",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
class MemoryModule {
    @Provides
    @Singleton
    fun provideMemory(@ApplicationContext context: Context) = Memory(File(context.filesDir, "memory"))
}
