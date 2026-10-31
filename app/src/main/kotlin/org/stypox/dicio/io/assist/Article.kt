package org.stypox.dicio.io.assist

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

/** The readable text of the page behind a link, for summarising more than the screen shows. */
object Article {
    data class Page(val title: String?, val text: String)

    /**
     * Whether the link is a page worth reading in full. Fetching is a second visit without the
     * person's login, so one-time links (sign-in, reset, invite, verify) would be used up by it;
     * a homepage or a search has no one article to summarise.
     */
    fun worthFetching(link: String): Boolean {
        val uri = runCatching { URI(link) }.getOrNull() ?: return false
        val path = uri.path.orEmpty().trim('/')
        return path.isNotEmpty() &&
                !ONE_TIME_LINK.containsMatchIn(link) &&
                !SEARCH.containsMatchIn(path) &&
                uri.query.orEmpty().split('&').none { it.startsWith("q=") }
    }

    fun fetch(link: String): Page? = read(
        Jsoup.connect(link)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MILLIS)
            .get()
    )

    /** Paragraphs of the article (or main) element, else of the whole page; null if too little to be an article. */
    internal fun read(document: Document): Page? {
        document.select("script, style, nav, header, footer, aside, form").remove()
        val root = document.selectFirst("article") ?: document.selectFirst("main") ?: document.body()
        val text = root.select("p, li, h2, h3").map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")
            .take(MAX_CHARS)
        if (text.length < MIN_CHARS) return null
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?.takeIf { it.isNotBlank() } ?: document.title().takeIf { it.isNotBlank() }
        return Page(title, text)
    }

    private val ONE_TIME_LINK = Regex(
        "[?&#](token|code|key|auth|sig|signature|otp|session|state|ticket|magic|nonce)[^=&]*=|" +
                "/(login|log-in|signin|sign-in|signup|sign-up|reset|verify|confirm|invite|oauth|auth|callback|magic)" +
                "(?=[/?#.-]|$)",
        RegexOption.IGNORE_CASE,
    )

    private val SEARCH = Regex("(^|/)(search|results)(/|$)", RegexOption.IGNORE_CASE)

    // Nano reads about 4000 tokens; the start of an article carries its point
    private const val MAX_CHARS = 6000
    // less than a few sentences is a login wall or an app shell, not an article
    private const val MIN_CHARS = 400
    private const val TIMEOUT_MILLIS = 8000
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36"
}
