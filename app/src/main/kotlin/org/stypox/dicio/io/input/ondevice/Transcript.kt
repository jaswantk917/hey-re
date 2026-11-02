package org.stypox.dicio.io.input.ondevice

/**
 * Everything said in one listen. After a pause the recognizer keeps listening but starts its words
 * over, so what came before the pause is kept here instead of being replaced.
 */
internal class Transcript {
    private var earlier = ""
    private var segment = ""
    private var paused = false

    val heard: String?
        get() = join(earlier, segment).takeIf { it.isNotBlank() }

    fun pause() {
        paused = true
    }

    /** The whole of what was heard so far, given the recognizer's latest words. */
    fun partial(words: String): String {
        if (paused && !continues(segment, words)) earlier = join(earlier, segment)
        paused = false
        segment = words
        return join(earlier, segment)
    }

    // some recognizers end with every word of the listen, others only with those after the last pause
    fun final(words: String) = if (continues(earlier, words)) words else join(earlier, words)

    companion object {
        private fun continues(before: String, words: String) =
            before.isBlank() || firstWord(before).equals(firstWord(words), ignoreCase = true)

        private fun firstWord(text: String) = text.trim().substringBefore(' ')

        private fun join(before: String, after: String) = "$before $after".trim()
    }
}
