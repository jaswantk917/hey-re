package org.stypox.dicio.skills.findhub

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class FindHubTest : StringSpec({
    // as Find Hub lists them on the owner's phone
    val phone = FindHub.Device("p", "Pixel 9", "PHONE", ringable = true, isThisPhone = true)
    val watch = FindHub.Device("w", "Galaxy Watch8", "WEAR", ringable = true, isThisPhone = false)
    val tag = FindHub.Device("t", "Keys tag", "BEACON", ringable = true, isThisPhone = false)
    val devices = listOf(phone, watch, tag)

    "what Find Hub doesn't know is no memo" {
        FindHub.memo(mapOf("location" to "No information available about keys")) shouldBe null
        FindHub.memo(mapOf("location" to "in the top drawer")) shouldBe "in the top drawer"
    }

    "devices are matched by the name or the kind of device said" {
        FindHub.match("watch", devices) shouldBe watch
        FindHub.match("galaxy watch", devices) shouldBe watch
        FindHub.match("ghadi", devices) shouldBe watch
        FindHub.match("phone", devices) shouldBe phone
        FindHub.match("keys", devices) shouldBe tag
    }

    "things that aren't devices are left to what Find Hub remembers" {
        FindHub.match("passport", devices) shouldBe null
        FindHub.match("chabi", devices) shouldBe null
    }

    "a device as the shell lists it" {
        FindHub.device(
            mapOf(
                "deviceId" to "w", "deviceName" to "Galaxy Watch8", "deviceType" to "ANDROID_DEVICE",
                "deviceCategory" to "WEAR", "isRingable" to true, "isCurrentDevice" to false,
            )
        ) shouldBe FindHub.Device("w", "Galaxy Watch8", "WEAR", ringable = true, isThisPhone = false)
        FindHub.device(mapOf("deviceName" to "no id")) shouldBe null
    }
})
