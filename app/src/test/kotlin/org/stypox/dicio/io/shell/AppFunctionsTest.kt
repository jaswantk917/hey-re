package org.stypox.dicio.io.shell

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.stypox.dicio.io.shell.AppFunctions.Outcome

class AppFunctionsTest : StringSpec({
    "a result's one-item lists become plain values" {
        val outcome = AppFunctions.parse(
            """
            {
              "androidAppfunctionsReturnValue": [
                { "resources": [], "location": [ "No information available about keys" ] }
              ]
            }
            """.trimIndent()
        ).shouldBeInstanceOf<Outcome.Done>()
        val value = outcome.value as Map<*, *>
        value["location"] shouldBe "No information available about keys"
        value["resources"] shouldBe emptyList<Any>()
    }

    "Settings' answer to a change" {
        val outcome = AppFunctions.parse(
            """{"androidAppfunctionsReturnValue":[{"isSuccessful":[true],"currentValue":["false"]}]}"""
        ).shouldBeInstanceOf<Outcome.Done>()
        (outcome.value as Map<*, *>)["isSuccessful"] shouldBe true
    }

    "an empty answer is nothing to give, not an error" {
        AppFunctions.parse("{}") shouldBe Outcome.Done(null)
    }

    "an error from the shell is a failure with its first line" {
        val outcome = AppFunctions.parse(
            "Error executing app function: android.app.appfunctions.AppFunctionException: The app function " +
                    "is disabled (code 1002). See logcat for more details."
        ).shouldBeInstanceOf<Outcome.Failed>()
        outcome.reason.contains("code 1002") shouldBe true
    }

    "an error that prints a request still fails, though it has braces in it" {
        AppFunctions.parse(
            "Error executing app function: Missing value in the params: {\n  properties: {\n  }\n} (code 2000)."
        ).shouldBeInstanceOf<Outcome.Failed>()
    }

    "a list of one stays a list where a list is expected" {
        AppFunctions.asList(mapOf("deviceId" to "a")) shouldBe listOf(mapOf("deviceId" to "a"))
        AppFunctions.asList(null) shouldBe emptyList<Any>()
    }

    "what the person said can't break out of the shell's quotes" {
        AppFunctions.quote("""{"personalItem":"maa's keys"}""") shouldBe """'{"personalItem":"maa'\''s keys"}'"""
        AppFunctions.quote("x'; rm -rf /; echo '") shouldBe """'x'\''; rm -rf /; echo '\'''"""
    }
})
