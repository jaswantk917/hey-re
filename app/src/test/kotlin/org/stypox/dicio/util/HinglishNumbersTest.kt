package org.stypox.dicio.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.stypox.dicio.util.HinglishNumbers.normalize

class HinglishNumbersTest : StringSpec({
    "number words before a time unit become digits" {
        normalize("paanch minute ka timer laga do") shouldBe "5 minute ka timer laga do"
        normalize("do ghante baad batana") shouldBe "2 hours baad batana"
        normalize("saat baje ka alarm") shouldBe "7 baje ka alarm"
    }

    "do stays do when it isn't a number" {
        normalize("timer laga do 5 minute ka") shouldBe "timer laga do 5 minute ka"
    }

    "half and one and a half hours" {
        normalize("aadha ghanta ka timer") shouldBe "30 minutes ka timer"
        normalize("dedh ghante ka timer") shouldBe "90 minutes ka timer"
    }

    "hours in Hindi become English" {
        normalize("1 ghanta") shouldBe "1 hour"
        normalize("3 ghante ka timer") shouldBe "3 hours ka timer"
    }

    "English is left alone" {
        normalize("Set a timer for five minutes") shouldBe "Set a timer for five minutes"
    }
})
