package org.stypox.dicio.skills.telephone

import android.icu.text.Transliterator
import android.os.Build
import org.stypox.dicio.util.StringUtils
import java.text.Normalizer

/**
 * Scores how well a spoken name fits a contact, word by word: every word the user said must match
 * one of the contact's words (exactly, as the start of it, or with a typo or two), so long contact
 * names no longer win just by sharing many letters. Relation words ("mummy", "maa", "mom") count
 * as the same word, and fillers like "hello" or "ko" are ignored.
 */
object ContactMatcher {

    /** Higher is better; null when the contact doesn't fit what was said at all. */
    fun score(spokenName: String, contactName: String, starred: Boolean = false): Int? {
        val spoken = words(spokenName).filter { it !in FILLERS }
        val contact = words(contactName)
        if (spoken.isEmpty() || contact.isEmpty()) return null

        val used = BooleanArray(contact.size)
        var total = 0
        for (word in spoken) {
            var best = 0
            var bestIndex = -1
            contact.forEachIndexed { i, other ->
                val score = if (used[i]) 0 else wordScore(word, other)
                if (score > best) {
                    best = score
                    bestIndex = i
                }
            }
            if (bestIndex < 0) return null
            used[bestIndex] = true
            total += best
        }

        val extraWords = contact.size - spoken.size
        return total / spoken.size -
                extraWords.coerceAtLeast(0) * EXTRA_WORD_PENALTY +
                (if (extraWords == 0) FULL_NAME_BONUS else 0) +
                (if (starred) STARRED_BONUS else 0)
    }

    /** A contact this close needs no list to choose from. */
    const val CLEAR_MATCH = 100

    /** A clear match must beat the runner-up by this much. */
    const val CLEAR_LEAD = 25

    internal fun words(text: String): List<String> {
        val latin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && text.any { it in DEVANAGARI }) {
            Transliterator.getInstance("Devanagari-Latin").transliterate(text)
        } else {
            text
        }
        return Normalizer.normalize(latin, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase()
            .split(NOT_ALPHANUMERIC)
            .filter { it.isNotEmpty() }
            .map { RELATIONS[it] ?: it }
    }

    private fun wordScore(spoken: String, contact: String): Int = when {
        spoken == contact -> EXACT
        spoken.length >= 2 && contact.startsWith(spoken) -> PREFIX
        contact.length >= 3 && spoken.startsWith(contact) -> PREFIX
        StringUtils.levenshteinDistance(spoken, contact) <= allowedTypos(spoken) -> TYPO
        else -> 0
    }

    private fun allowedTypos(word: String) = when {
        word.length >= 7 -> 2
        word.length >= 4 -> 1
        else -> 0
    }

    private const val EXACT = 100
    private const val PREFIX = 75
    private const val TYPO = 60
    private const val EXTRA_WORD_PENALTY = 8
    private const val FULL_NAME_BONUS = 15
    private const val STARRED_BONUS = 10

    private val DEVANAGARI = 'ऀ'..'ॿ'
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NOT_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")

    // what people say around a name: "hello mummy ko call karo", "call my mom please"
    private val FILLERS = setOf(
        "hello", "hey", "hi", "ok", "okay", "please", "plz", "pls", "zara", "jara", "jaldi",
        "ko", "se", "the", "my", "mere", "meri", "mera", "apne", "apni", "to", "a", "an", "now",
    )

    // spellings of the same relation, folded into one word
    private val RELATIONS = listOf(
        listOf("mummy", "mummy", "mummi", "mammi", "mamma", "mumma", "mom", "mommy", "mum", "maa",
            "ma", "mother", "amma", "mataji"),
        listOf("papa", "pappa", "papaji", "dad", "daddy", "father", "pitaji", "baba", "abbu"),
        listOf("bhaiya", "bhaiyya", "bhaia", "bhai", "bhaii", "brother", "bro"),
        listOf("didi", "dii", "sister", "behen", "bahan"),
        listOf("dada", "dadaji", "grandpa", "grandfather"),
        listOf("dadi", "daadi", "dadima", "grandma", "grandmother"),
        listOf("nana", "nanaji"),
        listOf("nani", "naani", "nanima"),
        listOf("chacha", "chachu", "chachaji"),
        listOf("mama", "mamu", "mamaji"),
        listOf("bhabhi", "bhabi"),
        listOf("jiju", "jijaji", "jija"),
    ).flatMap { group -> group.map { it to group.first() } }.toMap()
}
