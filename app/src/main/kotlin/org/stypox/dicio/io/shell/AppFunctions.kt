package org.stypox.dicio.io.shell

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Android's app functions (tools apps publish for assistants, like Find Hub's "where are my keys"),
 * run through the shell over Shizuku: calling them directly needs a permission only Google's own
 * assistant holds, but `cmd app_function` is open to the shell.
 */
object AppFunctions {
    sealed interface Outcome {
        data class Done(val value: Any?) : Outcome
        data object NoShizuku : Outcome
        data class Failed(val reason: String) : Outcome
    }

    /** Runs [function] of [packageName]; a function the app ships switched off is switched on first. */
    suspend fun call(packageName: String, function: String, parameters: JsonObject = JsonObject(emptyMap())): Outcome {
        val first = execute(packageName, function, parameters) ?: return Outcome.NoShizuku
        if (first !is Outcome.Failed || DISABLED !in first.reason) return first
        Shell.run(
            "cmd app_function set-enabled --package ${quote(packageName)} --function ${quote(function)} --state enable"
        ) ?: return Outcome.NoShizuku
        return execute(packageName, function, parameters) ?: Outcome.NoShizuku
    }

    private suspend fun execute(packageName: String, function: String, parameters: JsonObject): Outcome? {
        val result = Shell.run(
            "cmd app_function execute-app-function --package ${quote(packageName)} " +
                    "--function ${quote(function)} --parameters ${quote(parameters.toString())}",
            timeoutMillis = TIMEOUT_MILLIS,
        ) ?: return null
        return parse(result.output)
    }

    internal fun parse(output: String): Outcome {
        val json = output.substringAfter('{', "").let { if (it.isEmpty()) null else "{$it" }
            ?: return Outcome.Failed(output.lineSequence().firstOrNull().orEmpty())
        return try {
            val result = Json.parseToJsonElement(json) as? JsonObject
                ?: return Outcome.Failed(output.lineSequence().first())
            // "{}": the function ran and had nothing to give, like a device with no known location
            if (result.isEmpty()) return Outcome.Done(null)
            if (RETURN_VALUE !in result) return Outcome.Failed(output.lineSequence().first())
            Outcome.Done(unwrap(result[RETURN_VALUE]))
        } catch (e: Exception) {
            Outcome.Failed(output.lineSequence().firstOrNull().orEmpty())
        }
    }

    /**
     * The shell prints every property as a list, even single values ("location": ["at home"]);
     * lists of one become the value itself, objects keep their keys.
     */
    internal fun unwrap(value: JsonElement?): Any? = when (value) {
        null, JsonNull -> null
        is JsonArray -> {
            val items = value.map { unwrap(it) }
            if (items.size == 1) items[0] else items
        }
        is JsonObject -> value.mapValues { unwrap(it.value) }
        is JsonPrimitive -> if (value.isString) value.content
        else value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull ?: value.content
    }

    /** [unwrap] can't tell a list of one from a single value; this gives back the list. */
    fun asList(value: Any?): List<Any?> = when (value) {
        null -> emptyList()
        is List<*> -> value
        else -> kotlin.collections.listOf(value)
    }

    /** Single quotes for `sh -c`, so what the person said can't be read as shell. */
    internal fun quote(text: String) = "'" + text.replace("'", "'\\''") + "'"

    private const val RETURN_VALUE = "androidAppfunctionsReturnValue"
    private const val DISABLED = "code 1002"
    private const val TIMEOUT_MILLIS = 20_000L
}
