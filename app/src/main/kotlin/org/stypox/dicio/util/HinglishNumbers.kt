package org.stypox.dicio.util

/**
 * Rewrites Hindi number words that come with a time unit into digits ("paanch minute" ->
 * "5 minute", "aadha ghanta" -> "30 minutes"), so the English number parser understands
 * Hinglish timers and alarms. Number words elsewhere are left alone: "do" is also "laga do".
 */
object HinglishNumbers {
    fun normalize(text: String): String {
        var result = " ${text.lowercase()} "
        for ((phrase, replacement) in FRACTIONS) {
            result = result.replace(Regex("\\b$phrase\\b"), replacement)
        }
        result = NUMBER_BEFORE_UNIT.replace(result) { match ->
            "${NUMBERS.getValue(match.groupValues[1])} ${match.groupValues[2]}"
        }
        result = HOURS.replace(result) { match ->
            if (match.groupValues[1] == "ghanta") "hour" else "hours"
        }
        return result.trim().replace(Regex("\\s+"), " ")
    }

    private val NUMBERS = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "chaar" to 4, "paanch" to 5, "panch" to 5,
        "chhe" to 6, "che" to 6, "chhah" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10,
        "gyarah" to 11, "gyaarah" to 11, "barah" to 12, "baarah" to 12, "pandrah" to 15,
        "bees" to 20, "pachchis" to 25, "pachis" to 25, "tees" to 30, "chalis" to 40,
        "chaalis" to 40, "pachas" to 50, "pachaas" to 50,
    )

    private const val UNITS = "minute|minutes|minat|min|mins|ghanta|ghante|ghanton|hour|hours|" +
            "second|seconds|sec|secs|baje"

    private val NUMBER_BEFORE_UNIT =
        Regex("\\b(${NUMBERS.keys.joinToString("|")})\\s+($UNITS)\\b")

    private val HOURS = Regex("\\b(ghanta|ghante|ghanton)\\b")

    // "aadha ghanta" is a duration on its own, without a number
    private val FRACTIONS = listOf(
        "aadha ghanta" to "30 minutes",
        "adha ghanta" to "30 minutes",
        "dedh ghanta" to "90 minutes",
        "dedh ghante" to "90 minutes",
        "dhai ghante" to "150 minutes",
        "dhai ghanta" to "150 minutes",
    )
}
