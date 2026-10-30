package org.stypox.dicio.skills.alarm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalTime

private val lateNight = LocalTime.of(23, 0)
private val morning = LocalTime.of(9, 0)

class AlarmTimeTest : StringSpec({
    "am and pm" {
        AlarmTime.parse("wake me up at 6:30 am", morning) shouldBe LocalTime.of(6, 30)
        AlarmTime.parse("alarm for 7 pm", morning) shouldBe LocalTime.of(19, 0)
        AlarmTime.parse("alarm at 12 am", morning) shouldBe LocalTime.of(0, 0)
        AlarmTime.parse("alarm at 12 pm", morning) shouldBe LocalTime.of(12, 0)
    }

    "24 hour times" {
        AlarmTime.parse("set an alarm for 18:45", morning) shouldBe LocalTime.of(18, 45)
    }

    "parts of the day in Hindi" {
        AlarmTime.parse("subah 6 baje ka alarm", lateNight) shouldBe LocalTime.of(6, 0)
        AlarmTime.parse("shaam 7 baje ka alarm", morning) shouldBe LocalTime.of(19, 0)
        AlarmTime.parse("raat 10 baje", morning) shouldBe LocalTime.of(22, 0)
        AlarmTime.parse("raat 2 baje", morning) shouldBe LocalTime.of(2, 0)
        AlarmTime.parse("dopahar 2 baje", morning) shouldBe LocalTime.of(14, 0)
    }

    "saade, sava, paune, dedh, dhai" {
        AlarmTime.parse("saade 6 baje", lateNight) shouldBe LocalTime.of(6, 30)
        AlarmTime.parse("sava 7 baje subah", lateNight) shouldBe LocalTime.of(7, 15)
        AlarmTime.parse("paune 8 baje subah", lateNight) shouldBe LocalTime.of(7, 45)
        AlarmTime.parse("dedh baje", morning) shouldBe LocalTime.of(13, 30)
        AlarmTime.parse("dhai baje", morning) shouldBe LocalTime.of(14, 30)
    }

    "without am or pm, the next time it happens" {
        AlarmTime.parse("6 baje ka alarm", lateNight) shouldBe LocalTime.of(6, 0)
        AlarmTime.parse("6 baje ka alarm", morning) shouldBe LocalTime.of(18, 0)
        AlarmTime.parse("alarm at 6 30", lateNight) shouldBe LocalTime.of(6, 30)
    }

    "no time" {
        AlarmTime.parse("set an alarm").shouldBeNull()
        AlarmTime.parse("alarm at 25").shouldBeNull()
    }
})
