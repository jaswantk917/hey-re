package org.stypox.dicio.eval

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CopiedExampleTest : StringSpec({
    val examples = listOf(
        "Navigate to Vancouver international airport", "What time is it?", "Call Tom", "Set a timer for five minutes",
        "Wake me up at 6:30",
    )

    "Nano copying an example's own details is thrown away" {
        SkillEvaluatorImpl.copiesAnExample(
            "Navigate to Vancouver International Airport.", "kya mujhe thoda aur chalna chahie", examples,
        ) shouldBe true
        SkillEvaluatorImpl.copiesAnExample("call tom", "kisi ko phone lagao", examples) shouldBe true
        SkillEvaluatorImpl.copiesAnExample("wake me up at 6:30", "subah uthana", examples) shouldBe true
    }

    "a rewrite that happens to be an example is kept when it adds nothing the person didn't say" {
        SkillEvaluatorImpl.copiesAnExample("What time is it?", "kitne baje hain", examples) shouldBe false
        SkillEvaluatorImpl.copiesAnExample("Call Tom", "tom ko phone lagao", examples) shouldBe false
        SkillEvaluatorImpl.copiesAnExample("Set a timer for five minutes", "paanch minute ka timer", examples) shouldBe false
    }

    "a real rewrite with the person's own details is kept" {
        SkillEvaluatorImpl.copiesAnExample("Navigate to Connaught Place", "connaught place le chalo", examples) shouldBe false
        SkillEvaluatorImpl.copiesAnExample("Call Papa", "papa ko phone lagao", examples) shouldBe false
    }
})
