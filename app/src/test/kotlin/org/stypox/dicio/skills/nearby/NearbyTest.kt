package org.stypox.dicio.skills.nearby

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.dicio.skill.standard.util.MatchHelper
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.sentences.Sentences.Nearby as NearbySentences
import org.stypox.dicio.util.Hinglish

private val data = Sentences.Nearby["en"]!!

private fun match(input: String) = data.score(MatchHelper(null, input), input)

class NearbyTest : StringSpec({
    "asking for places nearby searches Maps around you" {
        mapOf(
            "khane ki jagah dhundho aaspaas" to "restaurants",
            "khaane ki jagah dhundo aaspaas" to "restaurants",
            "aaspaas petrol pump dikhao" to "petrol pump",
            "aas paas koi dawai ki dukaan batao" to "pharmacy",
            "sabse paas atm kahan hai" to "ATM",
            "restaurants near me" to "restaurants",
            "find a pharmacy nearby" to "pharmacy",
            "nearest petrol pump" to "petrol pump",
        ).forEach { (input, query) ->
            withClue(input) {
                val (score, result) = match(input)
                score.scoreIn01Range() shouldBeGreaterThan 0.85f
                val what = when (result) {
                    is NearbySentences.English -> result.what
                    is NearbySentences.Hinglish -> result.what
                }
                Nearby.query(what.orEmpty()) shouldBe query
            }
        }
    }

    "spoken in Devanagari it lands the same" {
        val input = Hinglish.romanize("खाने की जगह ढूंढो आसपास")
        withClue(input) {
            (match(input).second is NearbySentences.Hinglish) shouldBe true
        }
    }

    "directions still go to navigation" {
        val navigation = Sentences.Navigation["en"]!!
        val input = "navigate to connaught place"
        match(input).first.scoreIn01Range() shouldBeLessThan navigation.score(MatchHelper(null, input), input).first.scoreIn01Range()
    }

    "an unknown kind of place is searched as said" {
        Nearby.query("gym") shouldBe "gym"
    }
})
