package org.stypox.dicio.io.shell

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.stypox.dicio.io.shell.AppFunctions.Outcome

/**
 * The Settings app's own switches and readings ("display_settings_screen/brightness"), through the
 * app functions it publishes. It answers only while the phone is unlocked.
 */
object DeviceState {
    const val BATTERY_LEVEL = "api_power_usage_summary_screen/battery_level"
    const val BATTERY_STATUS = "api_power_usage_summary_screen/battery_status"
    const val STORAGE_USED = "storage_dashboard_fragment/storage_summary_used"
    const val STORAGE_TOTAL = "storage_dashboard_fragment/storage_summary_total"
    const val BRIGHTNESS = "display_settings_screen/brightness"
    const val HOTSPOT = "wifi_tether/use_wifi_hotspot"
    const val NIGHT_LIGHT = "api_night_display/night_display_activated"
    const val AUTO_ROTATE = "smart_auto_rotate_pref_screen/auto_rotate"

    /** The setting's value as Settings shows it ("81", "190 GB used", "true"). */
    suspend fun get(key: String): Outcome = AppFunctions.call(
        SETTINGS, "getDeviceStateItem",
        buildJsonObject { putJsonObject("getDeviceStateItemParams") { put("key", key) } },
    ).let { outcome ->
        if (outcome !is Outcome.Done) return@let outcome
        val item = (outcome.value as? Map<*, *>)?.get("deviceStateItem") as? Map<*, *>
        item?.get("jsonValue")?.let { Outcome.Done(it.toString()) } ?: Outcome.Failed("no value for $key")
    }

    suspend fun set(key: String, value: String): Outcome = AppFunctions.call(
        SETTINGS, "setDeviceStateItem",
        // Settings wants every value as text, "false" and not false
        buildJsonObject {
            putJsonObject("setDeviceStateItemParams") {
                put("key", key)
                put("value", value)
            }
        },
    ).let { outcome ->
        if (outcome !is Outcome.Done) return@let outcome
        val result = outcome.value as? Map<*, *>
        if (result?.get("isSuccessful") == true) Outcome.Done(result["currentValue"]?.toString())
        else Outcome.Failed(result?.get("currentValue")?.toString() ?: "not set")
    }

    private const val SETTINGS = "com.android.settings"
}
