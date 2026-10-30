package org.stypox.dicio.skills

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.dicio.skill.standard.util.MatchHelper
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.sentences.Sentences.CheckIn
import org.stypox.dicio.skills.checkin.GoogleTasks
import org.stypox.dicio.skills.checkin.GoogleTasks.OpenTask
import org.stypox.dicio.skills.checkin.Routine
import org.stypox.dicio.skills.checkin.WalkNudge
import java.time.LocalDate
import java.time.LocalDateTime

private val data = Sentences.CheckIn["en"]!!

private fun match(input: String) = data.score(MatchHelper(null, input), input)

class CheckInTest : StringSpec({
    "greetings are a check-in" {
        for (input in listOf("hello", "hi there", "hello how are you", "hey re how are you doing", "what s up", "good morning")) {
            match(input).second shouldBe CheckIn.English
            match(input).first.scoreIn01Range() shouldBeGreaterThan 0.75f
        }
        for (input in listOf("kaise ho", "hello kaise ho aap", "kya haal hai", "aur kya chal raha hai")) {
            match(input).second shouldBe CheckIn.Hinglish
            match(input).first.scoreIn01Range() shouldBeGreaterThan 0.75f
        }
    }

    "the time still goes to the clock" {
        val time = Sentences.CurrentTime["en"]!!
        val input = "what time is it"
        match(input).first.scoreIn01Range() shouldBeLessThan time.score(MatchHelper(null, input), input).first.scoreIn01Range()
    }

    "walks fit the office day" {
        // Friday 30 October 2026 is a workday, Sunday 1 November is not
        fun friday(h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, 30, h, m)
        Routine.nudge(200, friday(9, 30)) shouldBe null
        Routine.nudge(800, friday(12)) shouldBe WalkNudge.AfterLunch
        Routine.nudge(1_500, friday(14, 20)) shouldBe WalkNudge.Now
        Routine.nudge(2_000, friday(16)) shouldBe WalkNudge.AtTea
        Routine.nudge(2_500, friday(17, 40)) shouldBe WalkNudge.Now
        Routine.nudge(3_000, friday(19)) shouldBe WalkNudge.OnTheWayHome
        Routine.nudge(3_000, friday(22)) shouldBe null
        Routine.nudge(5_000, friday(16)) shouldBe null
        Routine.nudge(500, LocalDateTime.of(2026, 11, 1, 15, 0)) shouldBe WalkNudge.Anytime
        Routine.nudge(500, LocalDateTime.of(2026, 11, 1, 8, 0)) shouldBe null
    }

    "tasks due today and overdue, soonest first" {
        val today = LocalDate.of(2026, 10, 31)
        val digest = GoogleTasks.digestOf(
            listOf(
                OpenTask("Pay rent", "2026-10-31T00:00:00.000Z"),
                OpenTask("Call bank", "2026-10-29T00:00:00.000Z"),
                OpenTask("Buy diyas", "2026-11-02T00:00:00.000Z"),
                OpenTask("Someday", null),
                OpenTask("", "2026-10-30T00:00:00.000Z"),
            ),
            today,
        )
        digest.due shouldBe listOf("Call bank", "Pay rent")
        digest.open shouldBe 4
    }
})
