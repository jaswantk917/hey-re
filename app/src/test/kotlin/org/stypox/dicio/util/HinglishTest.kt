package org.stypox.dicio.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.stypox.dicio.util.Hinglish.romanize

class HinglishTest : StringSpec({
    "what the recognizer wrote in Devanagari" {
        romanize("लाइट्स बंद करो") shouldBe "lights band karo"
        romanize("सरा लाइट ऑफ करो") shouldBe "sara light off karo"
        romanize("गूगल होम से लाइट्स ऑफ करो") shouldBe "google home se lights off karo"
        romanize("कूलर चालू कर दो") shouldBe "cooler chalu kar do"
    }

    "the silent a is dropped as it's said" {
        romanize("करना") shouldBe "karna"
        romanize("बेडरूम") shouldBe "bedroom"
        romanize("कमरे") shouldBe "kamre"
        romanize("मिनट") shouldBe "minat"
        romanize("सब") shouldBe "sab"
        romanize("बाद") shouldBe "baad"
    }

    "calls, timers and people" {
        romanize("माँ को कॉल करो") shouldBe "maa ko call karo"
        romanize("पांच मिनट का टाइमर लगाओ") shouldBe "paanch minat ka timer lagao"
        romanize("एक घंटे बाद कूलर बंद कर देना") shouldBe "ek ghante baad cooler band kar dena"
        romanize("हाँ") shouldBe "haan"
    }

    "Roman text and digits" {
        romanize("cooler band karo") shouldBe "cooler band karo"
        romanize("१० मिनट") shouldBe "10 minat"
    }
})
