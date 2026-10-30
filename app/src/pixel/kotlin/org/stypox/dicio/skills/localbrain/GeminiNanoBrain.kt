package org.stypox.dicio.skills.localbrain

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.stypox.dicio.io.assist.ScreenSnapshot

/**
 * Gemini Nano through AICore (Pixel 9 and newer). AICore only runs it while Hey re is the app in
 * front, which the home screen and the assist sheet both are.
 */
class GeminiNanoBrain : LocalBrain {
    private val model by lazy { Generation.getClient() }
    private val scope = CoroutineScope(Dispatchers.IO)
    private var download: Job? = null
    private var loggedModel = false

    override suspend fun isReady(): Boolean {
        val status = try {
            model.checkStatus()
        } catch (e: Exception) {
            Log.w(TAG, "AICore unavailable", e)
            return false
        }
        if (status == FeatureStatus.DOWNLOADABLE && download?.isActive != true) {
            // ready for a later question; this one goes to the usual fallback
            download = scope.launch {
                try {
                    model.download().collect {}
                } catch (e: Exception) {
                    Log.w(TAG, "Gemini Nano download failed", e)
                }
            }
        }
        if (status == FeatureStatus.AVAILABLE && !loggedModel) {
            loggedModel = true
            Log.i(TAG, "AICore model: ${runCatching { model.getBaseModelName() }.getOrNull()}")
        }
        return status == FeatureStatus.AVAILABLE
    }

    override suspend fun rewriteAsCommand(input: String, commands: List<String>, recent: List<LocalBrain.Turn>): String? {
        val reply = generate(
            system = """
                You turn what someone said to a voice assistant into one of the commands it knows.
                The commands it knows look like these:
                ${commands.joinToString("\n") { "- $it" }}
                Reply with exactly one command phrased like those, in English, keeping the person's
                own details (numbers, durations, places, names, apps). Translate Hindi or Hinglish.
                Use the conversation so far to fill in what the person leaves out: after
                "Should I call Maa?", "haan karo" means "call Maa".
                If none of them fits, reply with exactly NONE.
            """.trimIndent(),
            prompt = withHistory(input, recent),
            temperature = 0f,
            maxTokens = REWRITE_TOKENS,
        ) ?: return null
        return parseCommandReply(reply)
    }

    override suspend fun answer(
        input: String,
        recent: List<LocalBrain.Turn>,
        screen: ScreenSnapshot?,
    ): String? = generate(
        system = """
            You are Hey re, a friendly voice assistant running fully offline on a phone.
            Answer in at most two short sentences of plain text, no lists or markdown, since the
            answer is read out loud. Reply in the language the person used: English if they wrote
            English, Hindi if they wrote Hindi, and Hinglish in Roman letters only if they mixed
            Hindi words into their message.
            You have no internet: if the question needs live information like news, prices,
            scores or today's weather, say briefly that you can't check that offline.
        """.trimIndent() + screen?.let { "\n\n" + screenContext(it) }.orEmpty(),
        prompt = withHistory(input, recent),
        image = screen?.takeIf { it.text.length < MIN_SCREEN_TEXT }?.screenshot,
        temperature = ANSWER_TEMPERATURE,
        maxTokens = ANSWER_TOKENS,
    )?.trim()?.takeIf { it.isNotEmpty() }

    override suspend fun summarizeScreen(screen: ScreenSnapshot): String? {
        val useText = screen.text.length >= MIN_SCREEN_TEXT
        if (!useText && screen.screenshot == null) return null
        return generate(
            system = """
                You are Hey re, the voice assistant the person just summoned while looking at
                their phone. In one or two short sentences of plain text, tell them what's on
                their screen and the one thing on it most worth knowing, speaking to them as
                "you" (e.g. "You're in Maa's chat; she's asking when you'll be home."). No lists
                or markdown. Write in English, or in Hinglish in Roman letters if the screen is
                mostly in Hindi.
            """.trimIndent(),
            prompt = if (useText) screenContext(screen) else "This is a screenshot of their phone" +
                    screen.appName?.let { " in $it" }.orEmpty() + ".",
            image = if (useText) null else screen.screenshot,
            temperature = 0f,
            maxTokens = SUMMARY_TOKENS,
        )?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun generate(
        system: String,
        prompt: String,
        temperature: Float,
        maxTokens: Int,
        image: Bitmap? = null,
    ): String? =
        try {
            val options: GenerateContentRequest.Builder.() -> Unit = {
                this.temperature = temperature
                topK = TOP_K
                maxOutputTokens = maxTokens
            }
            model.generateContent(
                if (image == null) generateContentRequest(SystemInstruction(system), TextPart(prompt), options)
                else generateContentRequest(SystemInstruction(system), ImagePart(image), TextPart(prompt), options)
            ).candidates.firstOrNull()?.text
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Nano generation failed", e)
            null
        }

    companion object {
        private val TAG = GeminiNanoBrain::class.simpleName
        private const val REWRITE_TOKENS = 40
        private const val ANSWER_TOKENS = 120
        private const val ANSWER_TEMPERATURE = 0.4f
        private const val SUMMARY_TOKENS = 80
        // less text than this (a photo, a game, a video) and the screenshot says more
        private const val MIN_SCREEN_TEXT = 40
        private const val TOP_K = 16
    }
}

/** The last few exchanges as plain lines before the new message; Nano has no chat history API. */
internal fun withHistory(input: String, recent: List<LocalBrain.Turn>): String =
    if (recent.isEmpty()) input else buildString {
        appendLine("Conversation so far:")
        for (turn in recent) {
            appendLine("Person: ${turn.user}")
            appendLine("You: ${turn.assistant}")
        }
        append("Person now says: ").append(input)
    }

/** The screen's text as the model sees it, with the app it came from. */
internal fun screenContext(screen: ScreenSnapshot): String = buildString {
    append("What is on their screen right now")
    screen.appName?.let { append(" (in ").append(it).append(")") }
    appendLine(", for when they ask about \"this\" or the screen:")
    append(screen.text)
}

/** The model's first line without the quotes or bullet it sometimes adds; null for NONE. */
internal fun parseCommandReply(reply: String): String? =
    reply.lineSequence().map { it.trim().trim('"', '“', '”', '-', '*', ' ', '.') }
        .firstOrNull { it.isNotEmpty() }
        ?.takeUnless { it.equals("NONE", ignoreCase = true) }
