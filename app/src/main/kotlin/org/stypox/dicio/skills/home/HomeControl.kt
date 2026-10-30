package org.stypox.dicio.skills.home

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.home.DeviceTypeFactory
import com.google.home.FactoryRegistry
import com.google.home.Home
import com.google.home.HomeClient
import com.google.home.HomeConfig
import com.google.home.HomeDevice
import com.google.home.PermissionsResultStatus
import com.google.home.PermissionsState
import com.google.home.google.GoogleAirCoolerDevice
import com.google.home.google.GooglePowerControlDevice
import com.google.home.google.GoogleVentilatorDevice
import com.google.home.google.OtherDeviceDevice
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.DimmablePlugInUnitDevice
import com.google.home.matter.standard.DimmerSwitchDevice
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.MountedOnOffControlDevice
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.OnOffLightSwitchDevice
import com.google.home.matter.standard.OnOffPluginUnitDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** A device with its on/off switch, as fetched just now. */
class Switchable(val target: HomeTarget, val onOff: OnOff) {
    val isOn: Boolean? get() = onOff.onOff
}

/**
 * The user's Google Home, through the Home APIs: anything with an on/off switch (lights, smart
 * plugs and switches). The first use asks for consent with Google's own screens.
 */
@Singleton
class HomeControl @Inject constructor(@ApplicationContext private val context: Context) {
    private val client: HomeClient by lazy {
        Home.getClient(
            context,
            HomeConfig(
                coroutineContext = Dispatchers.IO,
                factoryRegistry = FactoryRegistry(traits = listOf(OnOff), types = SWITCHABLE_TYPES + LIGHT_TYPES),
            ),
        )
    }
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Device names from the last fetch, so the skill knows "cooler" is a device before asking Google. */
    @Volatile
    var known: List<HomeTarget> = readKnown()
        private set

    /** Google's consent screens need an activity to start from; call in onCreate. */
    fun register(activity: ComponentActivity) {
        client.registerActivityResultCallerForPermissions(activity)
    }

    suspend fun isConnected(): Boolean =
        client.hasPermissions().first { it != PermissionsState.PERMISSIONS_STATE_UNINITIALIZED } ==
                PermissionsState.GRANTED

    /** Whether Hey re may control the home, asking the user the first time. */
    suspend fun connect(): Boolean {
        val state = client.hasPermissions().first { it != PermissionsState.PERMISSIONS_STATE_UNINITIALIZED }
        if (state == PermissionsState.GRANTED) return true
        val result = withContext(Dispatchers.Main) { client.requestPermissions() }
        if (result.status != PermissionsResultStatus.SUCCESS) {
            Log.w(TAG, "Home permissions ${result.status}: ${result.errorMessage}")
        }
        return result.status == PermissionsResultStatus.SUCCESS
    }

    suspend fun switchables(): List<Switchable> {
        val result = client.devices().list().mapNotNull { device -> switchable(device) }
            .sortedWith(compareBy({ it.target.room.orEmpty() }, { it.target.name }))
        known = result.map { it.target }
        writeKnown(known)
        return result
    }

    private suspend fun switchable(device: HomeDevice): Switchable? {
        val types = device.types().first()
        val onOff = types.firstNotNullOfOrNull { type -> runCatching { type.trait(OnOff) }.getOrNull() }
            ?: return null
        val room = runCatching { device.room()?.name }.getOrNull()
        val isLight = types.any { it.factory in LIGHT_TYPES }
        Log.d(TAG, "Found ${device.name} in $room: ${types.map { it.factory }}")
        return Switchable(HomeTarget(device.id.id, device.name, room, isLight), onOff)
    }

    /** Switches [devices] on or off; returns the ones it couldn't reach. */
    suspend fun set(devices: List<Switchable>, on: Boolean): List<Switchable> = devices.filter { device ->
        try {
            if (on) device.onOff.on() else device.onOff.off()
            false
        } catch (e: Exception) {
            Log.w(TAG, "Could not switch ${device.target.name}", e)
            true
        }
    }

    private fun readKnown(): List<HomeTarget> = try {
        val array = JSONArray(prefs.getString(KNOWN, "[]"))
        (0 until array.length()).map { i ->
            array.getJSONObject(i).run {
                HomeTarget(getString("id"), getString("name"), optString("room").ifEmpty { null }, getBoolean("light"))
            }
        }
    } catch (e: Exception) {
        listOf()
    }

    private fun writeKnown(targets: List<HomeTarget>) {
        val array = JSONArray()
        targets.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("room", it.room ?: "").put("light", it.isLight))
        }
        prefs.edit().putString(KNOWN, array.toString()).apply()
    }

    companion object {
        private val TAG = HomeControl::class.simpleName
        private const val PREFS = "google_home"
        private const val KNOWN = "known_devices"

        private val LIGHT_TYPES: List<DeviceTypeFactory<*>> = listOf(
            OnOffLightDevice, DimmableLightDevice, ColorTemperatureLightDevice, ExtendedColorLightDevice,
        )

        // a smart switch may come through as a plug, a wall switch or by what it powers
        private val SWITCHABLE_TYPES: List<DeviceTypeFactory<*>> = listOf(
            OnOffPluginUnitDevice, DimmablePlugInUnitDevice, OnOffLightSwitchDevice, DimmerSwitchDevice,
            MountedOnOffControlDevice, GooglePowerControlDevice, GoogleAirCoolerDevice,
            GoogleVentilatorDevice, OtherDeviceDevice,
        )
    }
}
