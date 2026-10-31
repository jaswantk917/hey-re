package org.stypox.dicio.io.assist

import android.app.assist.AssistStructure
import android.graphics.Bitmap
import android.text.InputType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What was in front when the assist gesture summoned Hey re; it never leaves the phone. */
data class ScreenSnapshot(
    val packageName: String?,
    val appName: String?,
    val text: String,
    val screenshot: Bitmap? = null,
    /** The page's own link, when the app hands it over (Chrome does). */
    val link: String? = null,
)

sealed interface ScreenSummary {
    data object Working : ScreenSummary
    data class Ready(val text: String) : ScreenSummary
    data object Unavailable : ScreenSummary
}

/**
 * Holds the screen of the current assist visit, filled by [HeyReSession] and cleared when the sheet
 * closes, so questions asked later in the full app don't pick up a stale screen.
 */
object ScreenContext {
    private val _snapshot = MutableStateFlow<ScreenSnapshot?>(null)
    val snapshot: StateFlow<ScreenSnapshot?> = _snapshot

    private val _summary = MutableStateFlow<ScreenSummary?>(null)
    val summary: StateFlow<ScreenSummary?> = _summary

    /** The whole article behind [ScreenSnapshot.link], next to the summary of what's on screen. */
    private val _article = MutableStateFlow<ScreenSummary?>(null)
    val article: StateFlow<ScreenSummary?> = _article

    internal var hideSession: (() -> Unit)? = null

    internal fun begin() {
        _snapshot.value = null
        _summary.value = null
        _article.value = null
    }

    internal fun setText(packageName: String?, appName: String?, text: String) {
        _snapshot.value = (_snapshot.value ?: ScreenSnapshot(packageName, appName, ""))
            .copy(packageName = packageName, appName = appName, text = text)
    }

    internal fun setLink(link: String) {
        _snapshot.value = (_snapshot.value ?: ScreenSnapshot(null, null, "")).copy(link = link)
    }

    internal fun setScreenshot(screenshot: Bitmap) {
        _snapshot.value = (_snapshot.value ?: ScreenSnapshot(null, null, "")).copy(screenshot = screenshot)
    }

    fun setSummary(summary: ScreenSummary) {
        _summary.value = summary
    }

    fun setArticle(article: ScreenSummary) {
        _article.value = article
    }

    /** The sheet is gone: forget the screen and let the system tear the session down. */
    fun end() {
        _snapshot.value = null
        _summary.value = null
        _article.value = null
        hideSession?.invoke()
        hideSession = null
    }

    /** The visible text of every window, top to bottom, without passwords; capped for Nano. */
    fun textOf(structure: AssistStructure): String {
        val lines = mutableListOf<String>()
        for (i in 0 until structure.windowNodeCount) {
            collect(structure.getWindowNodeAt(i).rootViewNode, lines)
        }
        return lines.joinToString("\n").take(MAX_CHARS)
    }

    private fun collect(node: AssistStructure.ViewNode, lines: MutableList<String>) {
        if (node.visibility != android.view.View.VISIBLE) return
        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        val isPassword = node.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
                (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        if (!isPassword) {
            val text = (node.text ?: node.contentDescription)?.toString()?.trim()
            // lists often repeat the same label (a title in the bar and in the page)
            if (!text.isNullOrEmpty() && lines.lastOrNull() != text) lines.add(text)
        }
        for (i in 0 until node.childCount) {
            collect(node.getChildAt(i), lines)
        }
    }

    private const val MAX_CHARS = 3000
}
