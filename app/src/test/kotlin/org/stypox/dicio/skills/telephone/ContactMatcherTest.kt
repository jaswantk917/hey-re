package org.stypox.dicio.skills.telephone

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private fun best(spoken: String, vararg contacts: String) =
    contacts.mapNotNull { name -> ContactMatcher.score(spoken, name)?.let { name to it } }
        .maxByOrNull { it.second }?.first

class ContactMatcherTest : StringSpec({
    // the real address book where the old matcher picked junk for "hello mummy ko"
    val addressBook = arrayOf(
        "My Home",
        "CUSTOMER CARE",
        "Saloni SBI Credit Card Dilana Chahiti H Ye Mereko Sayad Rupya Milega Isko",
        "Abhishek Bhaiya Mama Ke Yahan Royal Enfield Misti",
        "Amritesh Senior MTH309",
        "Mummy",
    )

    "fillers around the name are ignored" {
        best("hello mummy ko", *addressBook) shouldBe "Mummy"
        best("my mummy please", *addressBook) shouldBe "Mummy"
    }

    "long names don't match just by sharing letters" {
        ContactMatcher.score("mummy", addressBook[2]).shouldBeNull()
        ContactMatcher.score("mummy", addressBook[3]).shouldBeNull()
    }

    "relation words are the same person" {
        best("mom", *addressBook) shouldBe "Mummy"
        best("maa", *addressBook) shouldBe "Mummy"
        best("mumma", "Mumma", "Papa") shouldBe "Mumma"
        best("dad", "Mummy", "Papa Ji") shouldBe "Papa Ji"
    }

    "mummy finds a contact saved as Maa, and is a clear match" {
        val phone = addressBook.filter { it != "Mummy" }.toTypedArray() + "Maa"
        best("hello mummy ko", *phone) shouldBe "Maa"
        ContactMatcher.score("mummy", "Maa")!! shouldBeGreaterThanOrEqual ContactMatcher.CLEAR_MATCH
        best("mom", "Maa", "Mama Ji") shouldBe "Maa"
    }

    "every spoken word must match" {
        ContactMatcher.score("abhishek sharma", "Abhishek Bhaiya").shouldBeNull()
        ContactMatcher.score("abhishek bhaiya", addressBook[3]).shouldNotBeNull()
    }

    "prefixes and small typos still match" {
        best("abhi", *addressBook) shouldBe addressBook[3]
        best("amritesh", *addressBook) shouldBe "Amritesh Senior MTH309"
        best("amritsh", *addressBook) shouldBe "Amritesh Senior MTH309"
    }

    "the exact full name beats a longer one" {
        best("mummy", "Mummy Office", "Mummy") shouldBe "Mummy"
        ContactMatcher.score("mummy", "Mummy")!! shouldBeGreaterThan ContactMatcher.score("mummy", "Mummy Office")!!
    }

    "a lone exact name is a clear match" {
        ContactMatcher.score("mummy", "Mummy")!! shouldBeGreaterThanOrEqual ContactMatcher.CLEAR_MATCH
    }

    "starred contacts win ties" {
        ContactMatcher.score("rahul", "Rahul", starred = true)!! shouldBeGreaterThan
                ContactMatcher.score("rahul", "Rahul")!!
    }

    "nothing to match" {
        ContactMatcher.score("", "Mummy").shouldBeNull()
        ContactMatcher.score("hello ko", "Mummy").shouldBeNull()
    }

    "accents and case don't matter" {
        best("JOSE", "José", "Joseph") shouldBe "José"
    }
})
