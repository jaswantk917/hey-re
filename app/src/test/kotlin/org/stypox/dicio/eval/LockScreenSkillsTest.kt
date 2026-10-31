package org.stypox.dicio.eval

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContainAnyOf

class LockScreenSkillsTest : StringSpec({
    "harmless skills run over the lock screen, personal and acting ones wait for an unlock" {
        SkillEvaluatorImpl.LOCK_SCREEN_SKILLS shouldContainAll listOf("current_time", "timer", "flashlight", "weather")
        SkillEvaluatorImpl.LOCK_SCREEN_SKILLS shouldNotContainAnyOf listOf(
            "telephone", "message", "phone_toggle", "smart_home", "check_in", "notify", "open", "search", "navigation",
        )
    }
})
