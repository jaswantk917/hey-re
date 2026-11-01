package org.stypox.dicio.skills.localbrain

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.util.RecognizeEverythingSkill
import org.stypox.dicio.R
import android.graphics.Bitmap
import org.stypox.dicio.io.assist.Article
import org.stypox.dicio.io.assist.ScreenSnapshot
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.skills.fallback.text.TextFallbackOutput

/**
 * An on-device language model that steps in when no skill understood the user: first to restate
 * the request as a command a skill does understand, then to answer by itself. The Pixel build
 * backs this with Gemini Nano; the FOSS build has none.
 */
interface LocalBrain {
    /** Whether a model is ready right now; may start fetching it in the background otherwise. */
    suspend fun isReady(): Boolean

    /**
     * Restates [input] as one command in the style of [commands] (keeping names, numbers and
     * places, and filling in what [recent] makes clear, e.g. who "haan karo" means), or returns
     * null when none of them fits.
     */
    suspend fun rewriteAsCommand(input: String, commands: List<String>, recent: List<Turn>): String?

    /**
     * A short answer to [input] meant to be read out loud, or null if the model can't give one;
     * [screen] is what's on the screen the person may be asking about.
     */
    suspend fun answer(
        input: String,
        recent: List<Turn>,
        screen: ScreenSnapshot? = null,
        memory: List<String> = listOf(),
    ): String?

    /** One or two sentences on what [screen] shows, or null if the model can't tell. */
    suspend fun summarizeScreen(screen: ScreenSnapshot): String?

    /** Two or three sentences on what the whole [article] says, or null if the model can't tell. */
    suspend fun summarizeArticle(article: Article.Page): String?

    /** What [image] is and the most useful thing in it, with [text] read from it if any. */
    suspend fun describeImage(image: Bitmap, text: String?): String?

    /**
     * Up to a few lasting facts about the person from their own [messages] in one chat, leaving
     * out what's [known] already; empty when there's nothing worth keeping, null if the model
     * couldn't run (it's tried again later).
     */
    suspend fun rememberFrom(messages: List<String>, known: List<String>): List<String>?

    /**
     * Lasting facts from [notes] the person wrote or pasted about themselves (to set up the
     * memory), leaving out what's [known]; null if the model couldn't run.
     */
    suspend fun factsFromNotes(notes: String, known: List<String>): List<String>?

    /** [facts] merged into a shorter list, the later one winning where two disagree; null on failure. */
    suspend fun compactMemory(facts: List<String>): List<String>?

    /** One earlier exchange, oldest first, so follow-ups make sense. */
    data class Turn(val user: String, val assistant: String)
}

object NoLocalBrain : LocalBrain {
    override suspend fun isReady() = false
    override suspend fun rewriteAsCommand(input: String, commands: List<String>, recent: List<LocalBrain.Turn>): String? = null
    override suspend fun answer(
        input: String,
        recent: List<LocalBrain.Turn>,
        screen: ScreenSnapshot?,
        memory: List<String>,
    ): String? = null
    override suspend fun summarizeScreen(screen: ScreenSnapshot): String? = null
    override suspend fun summarizeArticle(article: Article.Page): String? = null
    override suspend fun describeImage(image: Bitmap, text: String?): String? = null
    override suspend fun rememberFrom(messages: List<String>, known: List<String>): List<String>? = null
    override suspend fun factsFromNotes(notes: String, known: List<String>): List<String>? = null
    override suspend fun compactMemory(facts: List<String>): List<String>? = null
}

/** Only used to label the model's own answers; it is never in the list of selectable skills. */
object LocalBrainInfo : SkillInfo("local_brain") {
    override fun name(context: Context) = context.getString(R.string.skill_name_local_brain)

    override fun sentenceExample(context: Context) = ""

    @Composable
    override fun icon() = rememberVectorPainter(Icons.Default.AutoAwesome)

    override fun build(ctx: SkillContext): Skill<*>? = null
}

class LocalBrainSkill(
    private val brain: LocalBrain,
    private val recent: List<LocalBrain.Turn>,
    private val screen: ScreenSnapshot? = null,
    // what's remembered about the person that bears on the question
    private val memory: suspend (String) -> List<String> = { listOf() },
) : RecognizeEverythingSkill(LocalBrainInfo) {
    override suspend fun generateOutput(ctx: SkillContext, inputData: String): SkillOutput {
        val answer = brain.answer(inputData, recent, screen, memory(inputData))
        return if (answer.isNullOrBlank()) {
            TextFallbackOutput(askToRepeat = (ctx.previousOutput as? TextFallbackOutput)?.let { !it.askToRepeat } ?: true)
        } else {
            LocalBrainOutput(answer)
        }
    }
}

class LocalBrainOutput(private val answer: String) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = answer

    // up to two sentences, too long to read comfortably at headline size
    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        Text(
            text = answer,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}
