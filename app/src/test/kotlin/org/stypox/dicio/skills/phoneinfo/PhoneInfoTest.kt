package org.stypox.dicio.skills.phoneinfo

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PhoneInfoTest : StringSpec({
    "a brightness level as said" {
        PhoneInfoSkill.level("50") shouldBe 50
        PhoneInfoSkill.level("50 percent") shouldBe 50
        PhoneInfoSkill.level("150") shouldBe null
        PhoneInfoSkill.level("full") shouldBe null
    }

    "storage amounts without Settings' trailing word" {
        PhoneInfoSkill.amount("190 GB used") shouldBe "190 GB"
        PhoneInfoSkill.amount("256 GB total") shouldBe "256 GB"
    }
})
