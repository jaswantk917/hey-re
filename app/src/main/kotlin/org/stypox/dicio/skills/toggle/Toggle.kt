package org.stypox.dicio.skills.toggle

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AirplanemodeInactive
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MobiledataOff
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.WifiTetheringOff
import androidx.compose.ui.graphics.vector.ImageVector
import org.stypox.dicio.R

/** A phone switch Hey re flips through Shizuku, with where to flip it by hand otherwise. */
enum class Toggle(
    @StringRes val label: Int,
    val onIcon: ImageVector,
    val offIcon: ImageVector,
    val settings: String,
    private val onCommand: String,
    private val offCommand: String,
) {
    Wifi(R.string.skill_toggle_wifi, Icons.Default.Wifi, Icons.Default.WifiOff, Settings.Panel.ACTION_WIFI,
        "cmd wifi set-wifi-enabled enabled", "cmd wifi set-wifi-enabled disabled"),
    Bluetooth(R.string.skill_toggle_bluetooth, Icons.Default.Bluetooth, Icons.Default.BluetoothDisabled,
        Settings.ACTION_BLUETOOTH_SETTINGS,
        "cmd bluetooth_manager enable", "cmd bluetooth_manager disable"),
    MobileData(R.string.skill_toggle_mobile_data, Icons.Default.SignalCellularAlt, Icons.Default.MobiledataOff,
        Settings.Panel.ACTION_INTERNET_CONNECTIVITY, "svc data enable", "svc data disable"),
    Hotspot(R.string.skill_toggle_hotspot, Icons.Default.WifiTethering, Icons.Default.WifiTetheringOff,
        Settings.ACTION_WIRELESS_SETTINGS, HOTSPOT_ON, HOTSPOT_OFF),
    Airplane(R.string.skill_toggle_airplane, Icons.Default.AirplanemodeActive, Icons.Default.AirplanemodeInactive,
        Settings.ACTION_AIRPLANE_MODE_SETTINGS,
        "cmd connectivity airplane-mode enable", "cmd connectivity airplane-mode disable"),
    BatterySaver(R.string.skill_toggle_battery_saver, Icons.Default.BatterySaver, Icons.Default.BatteryStd,
        Settings.ACTION_BATTERY_SAVER_SETTINGS, "cmd power set-mode 1", "cmd power set-mode 0"),
    DarkMode(R.string.skill_toggle_dark_mode, Icons.Default.DarkMode, Icons.Default.LightMode,
        Settings.ACTION_DISPLAY_SETTINGS, "cmd uimode night yes", "cmd uimode night no"),
    Location(R.string.skill_toggle_location, Icons.Default.LocationOn, Icons.Default.LocationOff,
        Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "cmd location set-location-enabled true", "cmd location set-location-enabled false");

    fun command(on: Boolean) = if (on) onCommand else offCommand

    fun settingsIntent(): Intent = Intent(settings).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

// tethering has no shell switch of its own; the connectivity service starts the one in Settings
private const val HOTSPOT_ON = "cmd connectivity start-tethering wifi"
private const val HOTSPOT_OFF = "cmd connectivity stop-tethering wifi"
