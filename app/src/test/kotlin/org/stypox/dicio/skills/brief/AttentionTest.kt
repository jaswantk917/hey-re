package org.stypox.dicio.skills.brief

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.stypox.dicio.skills.brief.Attention.Candidate
import org.stypox.dicio.skills.brief.Attention.Kind

private const val NOW = 1_800_000_000_000L
private const val HOUR = 3_600_000L

private fun message(title: String, text: String, count: Int = 1, group: Boolean = false, ageHours: Long = 0) =
    Candidate(Kind.Message, "WhatsApp", title, text, NOW - ageHours * HOUR, count, group)

class AttentionTest : StringSpec({
    "a missed call outranks a chat, which outranks a live delivery" {
        val ranked = Attention.rank(
            listOf(
                Candidate(Kind.Live, "Swiggy", "Order on the way", "Arriving in 12 min", NOW),
                message("Papa", "Kab aa rahe ho?"),
                Candidate(Kind.MissedCall, "Phone", "Missed call", "Papa", NOW),
            ),
            NOW,
        )
        ranked.map { it.candidate.kind } shouldBe listOf(Kind.MissedCall, Kind.Message, Kind.Live)
    }

    "only the few that matter are shown, and none is fine" {
        Attention.rank(
            listOf(
                Candidate(Kind.Other, "Myntra", "Big Fashion Sale", "Flat 50% off", NOW),
                message("College group", "lol", count = 6, group = true),
            ),
            NOW,
        ).shouldBeEmpty()
        val many = (1..6).map { message("Friend $it", "Are you coming?") }
        Attention.rank(many, NOW).size shouldBe 3
    }

    "someone writing again and again, or asking something, matters more" {
        Attention.score(message("Maa", "Khana kha liya?"), NOW) shouldBeGreaterThan
                Attention.score(message("Rohit", "ok"), NOW)
        Attention.score(message("Maa", "Call me", count = 4), NOW) shouldBeGreaterThan
                Attention.score(message("Maa", "Call me"), NOW)
    }

    "old messages count for less" {
        Attention.score(message("Rohit", "hey", ageHours = 7), NOW) shouldBeLessThan
                Attention.score(message("Rohit", "hey"), NOW)
    }

    "promotions don't make it, a code might" {
        Attention.score(Candidate(Kind.Other, "Swiggy", "Flat 60% off today", "", NOW), NOW) shouldBeLessThan 50
        Attention.score(Candidate(Kind.Other, "Messages", "OTP 482913 for login", "", NOW), NOW) shouldBe 45
    }

    "each thing said in a few words" {
        Attention.phrase(Candidate(Kind.MissedCall, "Phone", "Missed call", "Papa")) shouldBe "missed call from Papa"
        Attention.phrase(message("Maa", "...", count = 3)) shouldBe "Maa sent 3 messages"
        Attention.phrase(Candidate(Kind.Task, "Google Tasks", "Pay rent", "")) shouldBe "task due: Pay rent"
    }
})
