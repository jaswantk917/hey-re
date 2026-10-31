package org.stypox.dicio.skills.brief

import android.app.PendingIntent

/**
 * Everything on the phone that might want the person, each scored on its own; the brief shows
 * the few that clear [SHOW_FROM], most urgent first, and none at all is a fine answer.
 */
object Attention {
    enum class Kind { MissedCall, Message, Live, Task, Battery, Storage, Other }

    data class Candidate(
        val kind: Kind,
        val app: String,
        val title: String,
        val text: String,
        val postedAtMillis: Long? = null,
        // how many messages are waiting in the chat, for "Maa wrote 4 times"
        val count: Int = 1,
        val isGroup: Boolean = false,
        val packageName: String? = null,
        val open: PendingIntent? = null,
    )

    data class Ranked(val candidate: Candidate, val score: Int)

    /** One short phrase per item, for the spoken line: "Maa sent 3 messages". */
    fun phrase(candidate: Candidate): String = when (candidate.kind) {
        // the dialer titles it "Missed call" and puts who in the text
        Kind.MissedCall -> "missed call from " +
                (if (candidate.title.contains("missed call", ignoreCase = true)) candidate.text else candidate.title)
        Kind.Message -> if (candidate.count > 1) "${candidate.title} sent ${candidate.count} messages"
        else "${candidate.title} messaged"
        Kind.Live -> "${candidate.app}: ${candidate.title}"
        Kind.Task -> "task due: ${candidate.title}"
        Kind.Battery -> "battery at ${candidate.title}"
        Kind.Storage -> "storage almost full"
        Kind.Other -> "${candidate.app}: ${candidate.title}"
    }

    fun rank(candidates: List<Candidate>, nowMillis: Long): List<Ranked> = candidates
        .map { Ranked(it, score(it, nowMillis)) }
        .filter { it.score >= SHOW_FROM }
        .sortedByDescending { it.score }
        .take(MAX_SHOWN)

    internal fun score(candidate: Candidate, nowMillis: Long): Int {
        val text = "${candidate.title} ${candidate.text}".lowercase()
        var score = when (candidate.kind) {
            Kind.MissedCall -> 85 + 5 * (candidate.count - 1).coerceAtMost(2)
            // a busy group chat is noise however many messages pile up; one person writing again is not
            Kind.Message -> if (candidate.isGroup) 30 + (if (ASKING.containsMatchIn(text)) 10 else 0)
            else 55 + 10 * (candidate.count - 1).coerceAtMost(3) + (if (ASKING.containsMatchIn(text)) 15 else 0)
            Kind.Live -> 60
            Kind.Task -> 50
            Kind.Battery -> 80
            Kind.Storage -> 50
            Kind.Other -> if (CODE.containsMatchIn(text)) 45 else 20
        }
        if (PROMOTION.containsMatchIn(text)) score -= 40
        val ageHours = candidate.postedAtMillis?.let { (nowMillis - it) / 3_600_000 } ?: 0
        // a message from this morning has likely been seen on the watch or ignored on purpose
        if (ageHours >= 6) score -= 20 else if (ageHours >= 2) score -= 10
        return score
    }

    // someone waiting on an answer: a question, or asking to be called back
    private val ASKING = Regex(
        "\\?|\\b(call me|call karo|urgent|jaldi|asap|kahan ho|kab aaoge|kab aa rahe|reply|phone uthao)\\b"
    )
    private val CODE = Regex("\\b(otp|verification code|one time password)\\b|\\b\\d{4,8}\\b.*\\bcode\\b")
    private val PROMOTION = Regex("\\d+\\s?% off|\\b(offer|sale|cashback|deal|discount|coupon|upto|flat \\d+)\\b")

    private const val SHOW_FROM = 50
    private const val MAX_SHOWN = 3
}
