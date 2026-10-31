package org.stypox.dicio.skills.image

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ImageScaleTest : StringSpec({
    "an image that already fits is left alone" {
        scaledSize(1080, 1536) shouldBe null
        scaledSize(400, 300) shouldBe null
    }

    "a tall phone photo shrinks to the longest side, keeping its shape" {
        scaledSize(3072, 4096) shouldBe Pair(1152, 1536)
    }

    "a wide screenshot shrinks by its width" {
        scaledSize(4608, 1152) shouldBe Pair(1536, 384)
    }

    "a sliver of a panorama never collapses to nothing" {
        scaledSize(20000, 5) shouldBe Pair(1536, 1)
    }
})
