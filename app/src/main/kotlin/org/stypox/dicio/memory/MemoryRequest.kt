package org.stypox.dicio.memory

import org.stypox.dicio.util.Hinglish

/**
 * "yaad rakhna ki passport almari mein hai", "what do you remember about me", "passport kahan
 * hai": said straight to the memory.
 */
object MemoryRequest {
    sealed interface Parsed {
        data class Remember(val fact: String) : Parsed
        data object Recall : Parsed
        // "where is my passport": answered from memory if it knows, else left to Find Hub's devices
        data class Where(val item: String) : Parsed
    }

    fun parse(input: String): Parsed? {
        val text = Hinglish.romanize(input).trim().trimEnd('.', '!', '?', '।')
        if (RECALL.containsMatchIn(text)) return Parsed.Recall
        (WHERE_EN.find(text) ?: WHERE_HI.find(text))?.let { return Parsed.Where(it.groups["item"]!!.value.trim()) }
        val fact = (REMEMBER_BEFORE.find(text)?.groupValues?.last() ?: REMEMBER_AFTER.find(text)?.groupValues?.get(1))
            ?.trim()?.takeIf { it.length >= MIN_FACT_CHARS }
            ?: return null
        return Parsed.Remember(fact.replaceFirstChar { it.uppercase() })
    }

    private val RECALL = Regex(
        "^(what do you (know|remember) about me|what('s| is) in your memory|" +
                "((tum|tumhe|aap|aapko)\\s+)?(mere|mujhe|mujhko) (baare|bare) (mein|me|main|men) " +
                "(kya|kya kya) (pata|yaad|maloom|malum)( hai| hain)?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val REMEMBER_BEFORE = Regex(
        "^(please\\s+)?(remember that|remember:|yaad (rakhna|rakho|(rakh lo)|rakhiye|rakh)( ki| ke| ye ki)?)\\s+(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val REMEMBER_AFTER = Regex("^(.+?),?\\s+yaad (rakhna|rakho|(rakh lo)|rakhiye)$", RegexOption.IGNORE_CASE)

    private val WHERE_EN = Regex(
        "^where (is|are|did i (put|leave|keep)) (my |the )?(?<item>.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val WHERE_HI = Regex(
        "^((mera|meri|mere) )?(?<item>.+?) (kahan|kidhar|kaha|kahaan|keedhar) " +
                "(hai|hain|rakha|rakhi|rakhe|gaya|gayi|gaye)( hai| hain| tha| thi| the)?$",
        RegexOption.IGNORE_CASE,
    )
    private const val MIN_FACT_CHARS = 6
}
