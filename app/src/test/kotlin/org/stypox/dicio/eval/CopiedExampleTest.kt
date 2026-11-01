package org.stypox.dicio.eval

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CopiedExampleTest : StringSpec({
    val examples = listOf(
        "Navigate to Vancouver international airport", "What time is it?", "Call Tom", "Set a timer for five minutes",
        "Wake me up at 6:30", "Paanch minute ka timer lagao",
    )

    "Nano copying an example's own details is thrown away" {
        SkillEvaluatorImpl.copiesAnExample(
            "Navigate to Vancouver International Airport.", "kya mujhe thoda aur chalna chahie", examples,
        ) shouldBe true
        SkillEvaluatorImpl.copiesAnExample("call tom", "kisi ko phone lagao", examples) shouldBe true
        SkillEvaluatorImpl.copiesAnExample("wake me up at 6:30", "subah uthana", examples) shouldBe true
        SkillEvaluatorImpl.copiesAnExample("Paanch minute ka timer lagao", "8 minutes a timer lagao", examples) shouldBe true
    }

    "a rewrite that happens to be an example is kept when it adds nothing the person didn't say" {
        SkillEvaluatorImpl.copiesAnExample("What time is it?", "kitne baje hain", examples) shouldBe false
        SkillEvaluatorImpl.copiesAnExample("Call Tom", "tom ko phone lagao", examples) shouldBe false
        SkillEvaluatorImpl.copiesAnExample("Set a timer for five minutes", "paanch minute ka timer", examples) shouldBe false
        SkillEvaluatorImpl.copiesAnExample("Paanch minute ka timer lagao", "paanch minute ka timer chahiye", examples) shouldBe false
    }

    "a real rewrite with the person's own details is kept" {
        SkillEvaluatorImpl.copiesAnExample("Navigate to Connaught Place", "connaught place le chalo", examples) shouldBe false
        SkillEvaluatorImpl.copiesAnExample("Call Papa", "papa ko phone lagao", examples) shouldBe false
    }

    "a guess that switches the other way is thrown away" {
        SkillEvaluatorImpl.flipsOnOff("Cooler band karo", "cooler chala do") shouldBe true
        SkillEvaluatorImpl.flipsOnOff("Cooler band karo", "on karo call cooler on karo") shouldBe true
        SkillEvaluatorImpl.flipsOnOff("Turn off the lights", "light jala do") shouldBe true
        SkillEvaluatorImpl.flipsOnOff("Cooler chalu karo", "cooler chala do") shouldBe false
        SkillEvaluatorImpl.flipsOnOff("Cooler band karo", "cooler ko rok do") shouldBe false
        SkillEvaluatorImpl.flipsOnOff("What time is it?", "kitne baje hain") shouldBe false
    }

    "the torch is only guessed when a light was asked about" {
        SkillEvaluatorImpl.mentionsLight("ek selfie lo") shouldBe false
        SkillEvaluatorImpl.mentionsLight("yahan bahut andhera hai") shouldBe true
        SkillEvaluatorImpl.mentionsLight("phone ki light jalao") shouldBe true
    }

    "media is only guessed when playback was asked for" {
        SkillEvaluatorImpl.mentionsMediaControl("which movie should i watch") shouldBe false
        SkillEvaluatorImpl.mentionsMediaControl("can you suggest me some movies to watch") shouldBe false
        SkillEvaluatorImpl.mentionsMediaControl("ye gaana aage badhao, agla lagao") shouldBe true
        SkillEvaluatorImpl.mentionsMediaControl("video rok do") shouldBe true
    }
})
