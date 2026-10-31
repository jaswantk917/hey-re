package org.stypox.dicio.skills.findhub

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.stypox.dicio.io.shell.AppFunctions
import org.stypox.dicio.io.shell.AppFunctions.Outcome

/** Google's Find Hub, through the app functions it publishes: your devices and where you put things. */
object FindHub {
    data class Device(
        val id: String,
        val name: String,
        val type: String?,
        val ringable: Boolean,
        val isThisPhone: Boolean,
    )

    data class Location(val place: String?, val lastSeen: String?)

    suspend fun devices(): Outcome = AppFunctions.call(PACKAGE, function("listDevices")).map { value ->
        AppFunctions.asList(value).mapNotNull { (it as? Map<*, *>)?.let(::device) }
    }

    suspend fun locate(device: Device): Outcome =
        AppFunctions.call(PACKAGE, function("locateDevice"), buildJsonObject { put("deviceId", device.id) })
            .map { (it as? Map<*, *>)?.let(::location) }

    suspend fun ring(device: Device, ring: Boolean): Outcome = AppFunctions.call(
        PACKAGE, function("ringDevice"),
        buildJsonObject {
            put("deviceId", device.id)
            put("makeItRing", ring)
        },
    )

    /** What Find Hub remembers about [item], or null when it knows nothing. */
    suspend fun recall(item: String): Outcome =
        AppFunctions.call(PACKAGE, function("recallLocation"), buildJsonObject { put("personalItem", item) })
            .map { value -> memo(value as? Map<*, *>) }

    suspend fun remember(item: String, place: String): Outcome = AppFunctions.call(
        PACKAGE, function("rememberAboutPersonalItem"),
        buildJsonObject {
            put("personalItem", item)
            put("location", place)
        },
    )

    /** The device [said] names ("watch", "Pixel 9", "mera phone"), if any. */
    fun match(said: String, devices: List<Device>): Device? {
        val words = said.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }.toSet()
        if (words.isEmpty()) return null
        val byName = devices.filter { device -> device.name.lowercase().split(' ').any { it in words } }
        if (byName.size == 1) return byName[0]
        val byType = devices.filter { device -> TYPE_WORDS[device.type?.lowercase()].orEmpty().any { it in words } }
        return byType.singleOrNull() ?: byName.firstOrNull { !it.isThisPhone }
    }

    internal fun device(fields: Map<*, *>): Device? {
        val id = fields["deviceId"] as? String ?: return null
        return Device(
            id = id,
            name = fields["deviceName"] as? String ?: return null,
            // WEAR, PHONE, BEACON...; deviceType only says ANDROID_DEVICE or FASTPAIR_DEVICE
            type = (fields["deviceCategory"] ?: fields["deviceType"]) as? String,
            ringable = fields["isRingable"] as? Boolean ?: false,
            isThisPhone = fields["isCurrentDevice"] as? Boolean ?: false,
        )
    }

    internal fun location(fields: Map<*, *>) = Location(
        place = (fields["address"] ?: fields["semanticLocation"]) as? String,
        lastSeen = fields["lastSeenRelativeTime"] as? String,
    )

    // Find Hub answers "No information available about keys" rather than nothing
    internal fun memo(fields: Map<*, *>?): String? =
        (fields?.get("location") as? String)?.takeUnless { it.startsWith("No information available") }

    private fun Outcome.map(transform: (Any?) -> Any?): Outcome =
        if (this is Outcome.Done) Outcome.Done(transform(value)) else this

    private fun function(name: String) = "com.google.android.apps.adm.memory.BaseMemoryAppFunctionService#$name"

    private const val PACKAGE = "com.google.android.apps.adm"
    const val APP_PACKAGE = PACKAGE

    private val TYPE_WORDS = mapOf(
        "wear" to setOf("watch", "ghadi"),
        "phone" to setOf("phone", "mobile"),
        "tablet" to setOf("tablet", "tab"),
        "beacon" to setOf("buds", "earbuds", "headphones", "speaker", "tag", "tracker"),
    )
}
