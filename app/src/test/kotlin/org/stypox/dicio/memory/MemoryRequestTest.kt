package org.stypox.dicio.memory

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private fun remembers(said: String) = (MemoryRequest.parse(said) as? MemoryRequest.Parsed.Remember)?.fact

class MemoryRequestTest : StringSpec({
    "a fact about the person's life goes to memory" {
        remembers("yaad rakhna ki meri sister Pune mein rehti hai") shouldBe "Meri sister Pune mein rehti hai"
        remembers("remember that I prefer tea to coffee") shouldBe "I prefer tea to coffee"
        remembers("mera office Baner mein hai yaad rakhna") shouldBe "Mera office Baner mein hai"
    }

    "where a thing was put is remembered too" {
        remembers("yaad rakhna ki passport almari mein hai") shouldBe "Passport almari mein hai"
        remembers("remember that my keys are in the drawer") shouldBe "My keys are in the drawer"
    }

    "Devanagari is remembered in Roman letters" {
        remembers("याद रखना कि पासपोर्ट अलमारी में है")?.endsWith("hai") shouldBe true
    }

    "asking where something is" {
        MemoryRequest.parse("meri chabi kahan hai") shouldBe MemoryRequest.Parsed.Where("chabi")
        MemoryRequest.parse("where did i put my passport") shouldBe MemoryRequest.Parsed.Where("passport")
        MemoryRequest.parse("car ki chabi kidhar rakhi hai") shouldBe MemoryRequest.Parsed.Where("car ki chabi")
    }

    "asking what it remembers" {
        MemoryRequest.parse("what do you remember about me") shouldBe MemoryRequest.Parsed.Recall
        MemoryRequest.parse("mere baare mein kya yaad hai") shouldBe MemoryRequest.Parsed.Recall
        MemoryRequest.parse("tumhe mere bare me kya pata hai?") shouldBe MemoryRequest.Parsed.Recall
    }

    "ordinary questions aren't memory" {
        MemoryRequest.parse("remember me to call maa") shouldBe null
        MemoryRequest.parse("kya tumhe yaad hai kal kya hua") shouldBe null
        MemoryRequest.parse("2 ghante mein yaad dilana ki kapde lane hain") shouldBe null
    }
})
