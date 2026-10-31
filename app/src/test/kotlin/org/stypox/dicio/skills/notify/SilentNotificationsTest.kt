package org.stypox.dicio.skills.notify

import android.app.NotificationManager
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SilentNotificationsTest : StringSpec({
    "silent notifications don't count, ones that make a sound or pop up do" {
        isAlerting(NotificationManager.IMPORTANCE_MIN) shouldBe false
        isAlerting(NotificationManager.IMPORTANCE_LOW) shouldBe false
        isAlerting(NotificationManager.IMPORTANCE_DEFAULT) shouldBe true
        isAlerting(NotificationManager.IMPORTANCE_HIGH) shouldBe true
    }
})
