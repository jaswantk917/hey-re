package org.stypox.dicio.skills.nearby

/** What to search Maps for when someone asks for a kind of place nearby, said in Hinglish or English. */
object Nearby {
    fun query(what: String): String {
        val place = what.trim().lowercase()
        return PLACES.firstOrNull { (pattern, _) -> pattern.containsMatchIn(place) }?.second ?: place
    }

    private val PLACES = listOf(
        Regex("kh(a|aa)n(a|e|aa)|restaurant|dhaba|eat") to "restaurants",
        Regex("dawa|dava|medical|chemist|pharmacy") to "pharmacy",
        Regex("aspatal|hospital") to "hospital",
        Regex("doctor|clinic") to "clinic",
        Regex("chai|tea") to "tea",
        Regex("petrol|diesel|fuel") to "petrol pump",
        Regex("\\batm\\b|paise nikal") to "ATM",
        Regex("kiran|grocery|sabzi|sabji") to "grocery store",
        Regex("mandir|temple") to "temple",
    )
}
