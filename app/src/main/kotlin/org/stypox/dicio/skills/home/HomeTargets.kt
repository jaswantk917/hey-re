package org.stypox.dicio.skills.home

/** A Google Home device that can be switched on and off, as the user named it in the Home app. */
data class HomeTarget(
    val id: String,
    val name: String,
    val room: String?,
    val isLight: Boolean,
)

/**
 * Works out which devices "bedroom ki light", "hall light", "cooler" or "saari lights" mean, by
 * the words of the device's own name and room. Plain words only: no model, so it's instant.
 */
object HomeTargets {
    sealed interface Match {
        data class Found(val targets: List<HomeTarget>) : Match
        /** Several devices fit equally well ("light" with two lights): let the user pick. */
        data class Ambiguous(val candidates: List<HomeTarget>) : Match
        data object None : Match
    }

    fun resolve(what: String, targets: List<HomeTarget>): Match {
        val words = words(what)
        // "lights off karo" means every light, not "which one?"
        val wantsAll = words.any { it in ALL } || PLURALS.any { it in rawWords(what) }
        val wanted = words - ALL
        if (wantsAll) {
            val pool = if (LIGHT in wanted) targets.filter { it.isLight } else targets
            val inRoom = pool.filter { target -> wanted.any { it != LIGHT && it in words(target.room.orEmpty()) } }
            return (inRoom.ifEmpty { pool }).takeIf { it.isNotEmpty() }?.let { Match.Found(it) } ?: Match.None
        }

        val scored = targets.map { it to score(wanted, it) }.filter { it.second > 0 }
        val best = scored.maxOfOrNull { it.second } ?: return Match.None
        val top = scored.filter { it.second == best }.map { it.first }
        return if (top.size == 1) Match.Found(top) else Match.Ambiguous(top)
    }

    /**
     * Whether [what] could be a device at all, so "timer band karo" or "music band karo" stay with
     * their own skills; before the devices are known, any generic device word ("light") is enough.
     */
    fun mayBeDevice(what: String, known: List<HomeTarget>): Boolean {
        val words = words(what)
        if (words.isEmpty()) return false
        if (known.isEmpty()) return words.any { it in GENERIC || it in ALL }
        val knownWords = known.flatMapTo(mutableSetOf()) { words(it.name) + words(it.room.orEmpty()) }
        return words.all { it in knownWords || it in ALL || it in GENERIC }
    }

    private fun score(wanted: Set<String>, target: HomeTarget): Int {
        val name = words(target.name)
        val room = words(target.room.orEmpty())
        // "light" alone says little; a name or room word decides
        return wanted.sumOf { word ->
            when {
                word == LIGHT -> if (target.isLight || LIGHT in name) 1 else 0
                word in name -> 3
                word in room -> 2
                else -> 0
            }
        }
    }

    private fun rawWords(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    internal fun words(text: String): Set<String> {
        val raw = rawWords(text)
        val joined = mutableListOf<String>()
        var i = 0
        while (i < raw.size) {
            // "bed room", "living room" written apart
            if (i + 1 < raw.size && raw[i + 1] == "room" && raw[i] in JOINS_ROOM) {
                joined.add(raw[i] + "room")
                i += 2
            } else {
                joined.add(raw[i])
                i += 1
            }
        }
        return joined.map { SYNONYMS[it] ?: it }.filter { it !in FILLERS }.toSet()
    }

    private const val LIGHT = "light"
    private val JOINS_ROOM = setOf("bed", "living", "drawing", "dining", "guest", "store", "pooja", "puja")
    private val SYNONYMS = mapOf(
        "lights" to LIGHT, "batti" to LIGHT, "bulb" to LIGHT, "lamp" to LIGHT, "lamps" to LIGHT,
        "bulbs" to LIGHT, "battiyan" to LIGHT, "lite" to LIGHT, "hal" to "hall",
    )
    private val FILLERS = setOf(
        "the", "my", "a", "an", "in", "of", "ki", "ka", "ke", "ko", "wali", "wala", "wale", "vali",
        "vala", "waali", "waala", "room", "kamre", "kamra", "please", "plz", "ji", "bhi", "and", "aur",
        // "google home se lights off karo"
        "google", "home", "se", "from", "on", "using",
    )
    private val ALL = setOf(
        "sab", "sabhi", "saari", "sari", "saare", "sare", "saara", "sara", "all", "everything", "every", "sabko",
    )
    private val PLURALS = setOf("lights", "battiyan", "bulbs", "lamps", "laaits", "laiton")
    private val GENERIC = setOf(LIGHT, "switch", "plug", "fan", "cooler", "ac", "tv", "socket", "heater", "geyser")
}
