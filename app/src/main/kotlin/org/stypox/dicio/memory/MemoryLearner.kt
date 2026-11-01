package org.stypox.dicio.memory

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import org.stypox.dicio.history.ChatHistory
import org.stypox.dicio.history.SavedChat
import org.stypox.dicio.skills.localbrain.LocalBrain
import javax.inject.Singleton

/**
 * Fills the [Memory] from finished chats and tidies it when it grows, with Gemini Nano. Nano only
 * runs with Hey re in front, so this is called at such moments: a new chat, coming back to the
 * app, or the buttons in Settings.
 */
class MemoryLearner(
    private val memory: Memory,
    private val chatHistory: ChatHistory,
    private val localBrain: LocalBrain,
) {
    sealed interface Result {
        data class Learned(val chats: Int, val facts: Int) : Result
        data object Busy : Result
        data object NoModel : Result
        // Nano stopped partway (the app went to the background, or it failed); the rest waits
        data class Interrupted(val chats: Int, val facts: Int) : Result
    }

    private val running = Mutex()

    /** The chats not yet read for facts, the one still going ([currentChatId]) aside. */
    fun pendingChats(currentChatId: Flow<Long>): Flow<List<SavedChat>> =
        combine(chatHistory.chats, memory.digestedUpTo, currentChatId) { chats, since, current ->
            pending(chats, since, current)
        }

    /** Reads every finished chat not yet read, stopping early whenever [stop] says the person needs Nano. */
    suspend fun learn(currentChatId: Long, stop: () -> Boolean = { false }): Result {
        if (!running.tryLock()) return Result.Busy
        try {
            if (!localBrain.isReady()) return Result.NoModel
            val chats = pending(chatHistory.load(), memory.digestedUpTo(), currentChatId)
            var learned = 0
            chats.forEachIndexed { done, chat ->
                if (stop()) return Result.Interrupted(done, learned)
                val messages = chat.turns.mapNotNull { it.question?.takeIf(String::isNotBlank) }
                val facts = if (messages.isEmpty()) listOf()
                else localBrain.rememberFrom(messages, memory.facts.value.map { it.text })
                    ?: return Result.Interrupted(done, learned)
                learned += memory.learn(facts, chat.id, chat.updatedAt, System.currentTimeMillis())
            }
            if (memory.facts.value.size > Memory.COMPACT_FROM && !stop()) compactLocked()
            return Result.Learned(chats.size, learned)
        } finally {
            running.unlock()
        }
    }

    /**
     * Fills the memory from [notes] the person pasted, a part at a time ([onPart] says which);
     * then merges if it grew long.
     */
    suspend fun import(notes: String, onPart: (done: Int, total: Int) -> Unit): Result {
        if (!running.tryLock()) return Result.Busy
        try {
            if (!localBrain.isReady()) return Result.NoModel
            val parts = Memory.chunks(notes, NOTES_PART_CHARS)
            val start = System.currentTimeMillis()
            // what's still there at the end: past the cap, a long paste pushes out older facts
            fun kept() = memory.facts.value.count { it.savedAt >= start }
            parts.forEachIndexed { done, part ->
                onPart(done, parts.size)
                val facts = localBrain.factsFromNotes(part, memory.facts.value.map { it.text })
                    ?: return Result.Interrupted(done, kept())
                memory.addAll(facts, System.currentTimeMillis())
            }
            if (memory.facts.value.size > Memory.COMPACT_FROM) compactLocked()
            return Result.Learned(parts.size, kept())
        } finally {
            running.unlock()
        }
    }

    /** Merges duplicates and drops what later facts contradict; the count after, or null if it didn't run. */
    suspend fun compact(): Int? {
        if (!running.tryLock()) return null
        try {
            if (!localBrain.isReady()) return null
            return if (compactLocked()) memory.facts.value.size else null
        } finally {
            running.unlock()
        }
    }

    private suspend fun compactLocked(): Boolean {
        val facts = memory.facts.value
        val merged = localBrain.compactMemory(facts.sortedBy { it.savedAt }.map { it.text }) ?: return false
        memory.replaceWith(merged, System.currentTimeMillis())
        return true
    }

    companion object {
        // a part of pasted notes; with what's known, it fits Nano's context with room to answer
        private const val NOTES_PART_CHARS = 1500

        internal fun pending(chats: List<SavedChat>, since: Long, currentChatId: Long) =
            chats.filter { it.updatedAt > since && it.id != currentChatId }.sortedBy { it.updatedAt }
    }
}

@Module
@InstallIn(SingletonComponent::class)
class MemoryLearnerModule {
    @Provides
    @Singleton
    fun provideMemoryLearner(memory: Memory, chatHistory: ChatHistory, localBrain: LocalBrain) =
        MemoryLearner(memory, chatHistory, localBrain)
}
