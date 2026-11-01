package org.stypox.dicio.ui.home

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.stypox.dicio.history.SavedChat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HistoryDaysTest : StringSpec({
    val zone = ZoneId.of("Asia/Kolkata")
    fun at(day: Int, hour: Int, id: Long) =
        SavedChat(id, ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli(), listOf())

    "chats group by the local day, newest day and newest chat first" {
        // 23:00 IST is still the 1st there, though it's the 1st 17:30 UTC
        val days = byDay(listOf(at(1, 9, 1), at(2, 8, 2), at(1, 23, 3), at(2, 20, 4)), zone)
        days.map { it.first } shouldBe listOf(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1))
        days.map { (_, chats) -> chats.map { it.id } } shouldBe listOf(listOf(4L, 2L), listOf(3L, 1L))
    }
})
