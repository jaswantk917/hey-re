package org.stypox.dicio.skills.alarm

import java.time.LocalTime

/**
 * Reads the time of day out of what was said, in English or Hinglish: "6:30 pm", "7 am",
 * "saade 6", "shaam 7 baje", "subah 5". Without "am"/"pm" or a part of the day, the next time
 * it happens after [now] is picked, so "6 baje" late at night means 6 in the morning.
 */
object AlarmTime {
    fun parse(text: String, now: LocalTime = LocalTime.now()): LocalTime? {
        val words = text.lowercase().replace(Regex("[^a-z0-9: ]"), " ")
        var hour: Int
        var minute: Int
        val match = CLOCK.find(words)
        when {
            // "dedh baje" (1:30) and "dhai baje" (2:30) have no digits at all
            Regex("\\bdedh\\b").containsMatchIn(words) -> { hour = 1; minute = 30 }
            Regex("\\bdhai\\b|\\bdhaai\\b").containsMatchIn(words) -> { hour = 2; minute = 30 }
            match == null -> return null
            else -> {
                hour = match.groupValues[1].toInt()
                minute = match.groupValues[2].ifEmpty { "0" }.toInt()
            }
        }
        if (hour > 23 || minute > 59) return null

        when {
            Regex("\\bsaade\\b|\\bsade\\b").containsMatchIn(words) -> minute = 30
            Regex("\\bsava\\b|\\bsawa\\b").containsMatchIn(words) -> minute = 15
            Regex("\\bpaune\\b").containsMatchIn(words) -> {
                // "paune 8" is a quarter to 8
                hour = (hour + 23) % 24
                minute = 45
            }
        }
        if (hour > 12) return LocalTime.of(hour, minute)

        val pm = Regex("\\b(pm|p m|evening|night|tonight|shaam|sham|raat|dopahar|dopehar)\\b")
            .containsMatchIn(words)
        val am = Regex("\\b(am|a m|morning|subah|savere|sawere|tomorrow morning)\\b")
            .containsMatchIn(words)
        return when {
            am -> LocalTime.of(hour % 12, minute)
            // "raat 2 baje" is still night, and "dopahar 12" is noon
            pm && Regex("\\braat\\b").containsMatchIn(words) && hour in 1..4 -> LocalTime.of(hour, minute)
            pm -> LocalTime.of(hour % 12 + 12, minute)
            else -> {
                val morning = LocalTime.of(hour % 12, minute)
                val evening = LocalTime.of(hour % 12 + 12, minute)
                listOf(morning, evening).minBy { untilNext(now, it) }
            }
        }
    }

    private fun untilNext(now: LocalTime, time: LocalTime): Long {
        val seconds = time.toSecondOfDay() - now.toSecondOfDay()
        return if (seconds > 0) seconds.toLong() else seconds + 86_400L
    }

    // "6", "6:30", "6.30" or "6 30"
    private val CLOCK = Regex("\\b(\\d{1,2})(?:[:. ](\\d{2}))?\\b")
}
