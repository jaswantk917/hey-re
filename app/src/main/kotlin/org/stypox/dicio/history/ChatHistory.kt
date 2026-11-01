package org.stypox.dicio.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.File
import javax.inject.Singleton

@Serializable
data class SavedTurn(
    val question: String?,
    val answer: String,
    val skillId: String?,
    // a file in the history's image folder, for answers about an image
    val image: String? = null,
)

@Serializable
data class SavedChat(
    // when it started, which also orders and names its images
    val id: Long,
    val updatedAt: Long,
    val turns: List<SavedTurn>,
) {
    val title: String
        get() = turns.firstNotNullOfOrNull { it.question?.takeIf(String::isNotBlank) }
            ?: turns.firstOrNull()?.answer.orEmpty()
}

/**
 * The conversations worth coming back to, kept on the phone so an update or a restart loses
 * nothing. Quick commands (torch, timers, switches) aren't conversations and aren't kept.
 */
class ChatHistory(private val dir: File) {
    private val _chats = MutableStateFlow<List<SavedChat>>(listOf())
    val chats: StateFlow<List<SavedChat>> = _chats

    private val lock = Mutex()
    private var loaded = false
    private val file get() = File(dir, "chats.json")
    private val imageDir get() = File(dir, "images")

    suspend fun load(): List<SavedChat> = lock.withLock { loadLocked() }

    /** Saves [chat] if it's a conversation, writing any image not yet on disk. */
    suspend fun save(chat: SavedChat, images: Map<Int, Bitmap>) = lock.withLock {
        if (!isMeaningful(chat.turns)) return@withLock
        loadLocked()
        val turns = chat.turns.mapIndexed { index, turn ->
            val image = images[index]
            if (turn.image != null || image == null) turn
            else turn.copy(image = writeImage("${chat.id}-$index.jpg", image))
        }
        val (kept, dropped) = upsert(_chats.value, chat.copy(turns = turns))
        dropped.forEach(::deleteImages)
        write(kept)
    }

    suspend fun delete(id: Long) = lock.withLock {
        loadLocked()
        _chats.value.find { it.id == id }?.let(::deleteImages)
        write(_chats.value.filter { it.id != id })
    }

    suspend fun image(name: String): Bitmap? = withContext(Dispatchers.IO) {
        File(imageDir, name).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }

    private suspend fun loadLocked(): List<SavedChat> {
        if (!loaded) {
            _chats.value = withContext(Dispatchers.IO) {
                try {
                    if (file.exists()) json.decodeFromString<List<SavedChat>>(file.readText()) else listOf()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read the chat history", e)
                    listOf()
                }
            }
            loaded = true
        }
        return _chats.value
    }

    private suspend fun write(chats: List<SavedChat>) {
        _chats.value = chats
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            // written aside and moved, so a crash mid-write never leaves half a history
            val temp = File(dir, "chats.json.tmp")
            temp.writeText(json.encodeToString(chats))
            temp.renameTo(file)
        }
    }

    private suspend fun writeImage(name: String, image: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            imageDir.mkdirs()
            val target = File(imageDir, name)
            if (!target.exists()) target.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            name
        } catch (e: Exception) {
            Log.w(TAG, "Could not keep the image", e)
            null
        }
    }

    private fun deleteImages(chat: SavedChat) {
        chat.turns.mapNotNull { it.image }.forEach { File(imageDir, it).delete() }
    }

    companion object {
        private const val TAG = "ChatHistory"
        private const val JPEG_QUALITY = 85
        internal const val MAX_CHATS = 100

        private val json = Json { ignoreUnknownKeys = true }

        // what makes a chat worth keeping: something asked and answered, not a switch flipped
        private val CONVERSATION_SKILLS = setOf("local_brain", "image", "search", "translation", "lyrics")

        /** Answers that are talk rather than a command's result ("I could not calculate your request"). */
        fun isConversation(skillId: String?) = skillId in CONVERSATION_SKILLS

        internal fun isMeaningful(turns: List<SavedTurn>) =
            turns.any { isConversation(it.skillId) && it.answer.isNotBlank() }

        /** [chat] in place of its older self, newest first, and the chats that no longer fit. */
        internal fun upsert(chats: List<SavedChat>, chat: SavedChat): Pair<List<SavedChat>, List<SavedChat>> {
            val all = (chats.filter { it.id != chat.id } + chat).sortedByDescending { it.updatedAt }
            return Pair(all.take(MAX_CHATS), all.drop(MAX_CHATS))
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
class ChatHistoryModule {
    @Provides
    @Singleton
    fun provideChatHistory(@ApplicationContext context: Context) = ChatHistory(File(context.filesDir, "history"))
}
