package org.stypox.dicio.util

/**
 * Turns Devanagari into the Roman Hinglish the grammars are written in: the Pixel's recognizer
 * writes Hindi it hears in Devanagari ("लाइट्स बंद करो"), and people type it as "lights band
 * karo". Drops the silent "a" as speech does ("बंद" is "band", "करना" is "karna"), and maps the
 * English words spoken in Hindi back to English ("कूलर" is "cooler"). Other text is left alone.
 */
object Hinglish {
    fun romanize(text: String): String {
        if (text.none { it in 'ऀ'..'ॿ' }) return text
        val out = StringBuilder()
        var word = mutableListOf<Unit>()
        fun flush() {
            if (word.isNotEmpty()) {
                out.append(spell(word))
                word = mutableListOf()
            }
        }
        for (ch in text) {
            when {
                ch in CONSONANTS -> word.add(Unit(CONSONANTS.getValue(ch), consonant = true))
                ch == NUKTA -> word.lastOrNull()?.takeIf { it.consonant }?.let { last ->
                    last.text = NUKTA_FORMS[last.text] ?: last.text
                }
                ch in MATRAS -> {
                    word.lastOrNull()?.inherent = false
                    word.add(Unit(MATRAS.getValue(ch)))
                }
                ch == VIRAMA -> word.lastOrNull()?.inherent = false
                ch in VOWELS -> word.add(Unit(VOWELS.getValue(ch)))
                ch == ANUSVARA || ch == CHANDRABINDU -> word.add(Unit("n", nasal = true))
                ch == VISARGA -> word.add(Unit("h"))
                ch in '०'..'९' -> { flush(); out.append('0' + (ch - '०')) }
                else -> { flush(); out.append(ch) }
            }
        }
        flush()
        return out.toString()
    }

    private class Unit(
        var text: String,
        val consonant: Boolean = false,
        val nasal: Boolean = false,
    ) {
        // a consonant carries "a" unless a vowel sign or virama follows it
        var inherent = consonant
        val hasVowel get() = !consonant && !nasal || inherent
    }

    private fun spell(units: List<Unit>): String {
        // the last consonant of a word is said without its "a" ("बंद" -> "band"), unless alone
        units.lastOrNull { it.consonant || !it.nasal }?.takeIf { it.consonant && units.size > 1 }?.inherent = false
        // "करना": an "a" between a vowel and a voiced consonant is dropped -> "karna"
        for (i in 1 until units.size - 1) {
            val unit = units[i]
            if (unit.consonant && unit.inherent && units[i - 1].hasVowel &&
                units[i + 1].consonant && (units[i + 1].inherent || units.getOrNull(i + 2)?.let { !it.consonant && !it.nasal } == true)
            ) {
                unit.inherent = false
            }
        }
        var word = units.joinToString("") { if (it.inherent) it.text + "a" else it.text }
        // Hinglish writes the long vowel short at the end: "saara", "batti", "chalu"
        word = word.replace(Regex("aa$"), "a").replace(Regex("ee$"), "i").replace(Regex("oo$"), "u")
        return LOANWORDS[word] ?: word
    }

    private const val NUKTA = '़'
    private const val VIRAMA = '्'
    private const val ANUSVARA = 'ं'
    private const val CHANDRABINDU = 'ँ'
    private const val VISARGA = 'ः'

    private val CONSONANTS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        '\u0958' to "k", '\u0959' to "kh", '\u095A' to "g", '\u095B' to "z", '\u095C' to "d", '\u095D' to "dh",
        '\u095E' to "f", '\u095F' to "y",
    )
    private val NUKTA_FORMS = mapOf("k" to "k", "kh" to "kh", "g" to "g", "j" to "z", "d" to "d", "dh" to "dh", "f" to "f")
    private val VOWELS = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o", 'ऍ' to "e",
    )
    private val MATRAS = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e",
    )

    // English said in Hindi, as the recognizer writes it, back to the English the skills know
    private val LOANWORDS = mapOf(
        "laait" to "light", "laaits" to "lights", "lait" to "light", "laiton" to "lights", "laaiton" to "lights",
        "koolar" to "cooler", "kular" to "cooler", "of" to "off", "aaf" to "off",
        "hol" to "hall", "haal" to "hall", "kol" to "call", "fon" to "phone",
        "taaimar" to "timer", "taimar" to "timer", "alaarm" to "alarm", "maisej" to "message", "mesej" to "message",
        "vhaatsaep" to "whatsapp", "vhaatsep" to "whatsapp", "googal" to "google", "hom" to "home",
        "sekand" to "second", "chaalu" to "chalu", "lagaao" to "lagao", "bataao" to "batao", "chalaao" to "chalao",
        "jalaao" to "jalao", "bujhaao" to "bujhao", "naheen" to "nahi", "nahin" to "nahi", "maan" to "maa",
        "mammi" to "mummy", "paapa" to "papa", "vaali" to "wali", "vaala" to "wala", "sitch" to "switch",
        "svich" to "switch", "plag" to "plug", "pankha" to "fan",
        "rimaaind" to "remind", "rimaind" to "remind", "rimaaindar" to "reminder", "rimaindar" to "reminder",
    )
}
