package org.stypox.dicio.skills.alarm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.dicio.numbers.ParserFormatter
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.util.MatchHelper
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.sentences.Sentences.Alarm
import org.stypox.dicio.sentences.Sentences.Timer
import org.stypox.dicio.util.HinglishNumbers
import java.util.Locale

private val parserFormatter = ParserFormatter(Locale.ENGLISH)

private fun <T> StandardRecognizerData<T>.match(input: String): T {
    val normalized = HinglishNumbers.normalize(input)
    return score(MatchHelper(parserFormatter, normalized), normalized).second
}

class ReminderSentencesTest : StringSpec({
    "a reminder in a while is a timer named after what to remember" {
        val timer = Sentences.Timer["en"]!!
        for ((input, name) in listOf(
            "2 ghante mein yaad dilana ki chhat se kapde lane hain" to "chhat se kapde lane hain",
            "remind me in 2 hours to bring the clothes from the roof" to "bring the clothes from the roof",
            "remind me to call papa in 30 minutes" to "call papa",
        )) {
            val set = timer.match(input).shouldBeInstanceOf<Timer.Set>()
            set.name?.trim() shouldBe name
        }
    }

    "a reminder at a time is an alarm labelled with what to remember" {
        val alarm = Sentences.Alarm["en"]!!
        val medicine = alarm.match("remind me to take medicine at 9 pm").shouldBeInstanceOf<Alarm.Set>()
        medicine.name?.trim() shouldBe "take medicine"
        medicine.time?.trim() shouldBe "9 pm"
        val milk = alarm.match("shaam 6 baje yaad dilana ki doodh lana hai").shouldBeInstanceOf<Alarm.Set>()
        milk.name?.trim() shouldBe "doodh lana hai"
    }

    "asking where the reminders are opens the alarms" {
        val alarm = Sentences.Alarm["en"]!!
        alarm.match("where is the reminder") shouldBe Alarm.Show
        alarm.match("mera reminder kahan hai") shouldBe Alarm.Show
    }
})
