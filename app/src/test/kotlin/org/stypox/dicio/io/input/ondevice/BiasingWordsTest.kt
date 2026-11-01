package org.stypox.dicio.io.input.ondevice

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe

class BiasingWordsTest : StringSpec({
    "Hey re's words come first, then contacts and their first names, then apps" {
        val words = BiasingWords.words(listOf("Ravi Kumar", "Didi"), listOf("Swiggy"))
        words.shouldContainInOrder("chalao", "Ravi Kumar", "Didi", "Ravi", "Swiggy")
    }

    "each word once, whatever its case" {
        BiasingWords.words(listOf("maa", "Torch Light"), listOf("Torch")).count { it.equals("maa", true) } shouldBe 1
        BiasingWords.words(listOf(), listOf("Torch")).count { it.equals("torch", true) } shouldBe 1
    }

    "blank and overlong names are left out, and the list stays short" {
        val words = BiasingWords.words(listOf(" ", "x".repeat(60)) + (1..500).map { "Contact $it" }, listOf())
        words.none { it.isBlank() || it.length > 40 } shouldBe true
        words.size shouldBe 200
    }
})
