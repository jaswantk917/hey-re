package org.stypox.dicio.skills.toggle

import android.content.Intent
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.io.shell.AppFunctions
import org.stypox.dicio.io.shell.DeviceState
import org.stypox.dicio.io.shell.Shell
import org.stypox.dicio.sentences.Sentences.PhoneToggle

/** "Wifi band karo", "hotspot on": phone switches, flipped through Shizuku. */
class ToggleSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<PhoneToggle>) :
    StandardRecognizerSkill<PhoneToggle>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: PhoneToggle): SkillOutput {
        val (toggle, on) = when (inputData) {
            PhoneToggle.WifiOn -> Toggle.Wifi to true
            PhoneToggle.WifiOff -> Toggle.Wifi to false
            PhoneToggle.BluetoothOn -> Toggle.Bluetooth to true
            PhoneToggle.BluetoothOff -> Toggle.Bluetooth to false
            PhoneToggle.MobileDataOn -> Toggle.MobileData to true
            PhoneToggle.MobileDataOff -> Toggle.MobileData to false
            PhoneToggle.HotspotOn -> Toggle.Hotspot to true
            PhoneToggle.HotspotOff -> Toggle.Hotspot to false
            PhoneToggle.AirplaneOn -> Toggle.Airplane to true
            PhoneToggle.AirplaneOff -> Toggle.Airplane to false
            PhoneToggle.BatterySaverOn -> Toggle.BatterySaver to true
            PhoneToggle.BatterySaverOff -> Toggle.BatterySaver to false
            PhoneToggle.DarkModeOn -> Toggle.DarkMode to true
            PhoneToggle.DarkModeOff -> Toggle.DarkMode to false
            PhoneToggle.LocationOn -> Toggle.Location to true
            PhoneToggle.LocationOff -> Toggle.Location to false
            PhoneToggle.NightLightOn -> Toggle.NightLight to true
            PhoneToggle.NightLightOff -> Toggle.NightLight to false
            PhoneToggle.AutoRotateOn -> Toggle.AutoRotate to true
            PhoneToggle.AutoRotateOff -> Toggle.AutoRotate to false
        }
        if (toggle.command(on) == null && toggle.stateKey == null) {
            // Android has no switch apps may flip for this one: show its own
            val opened = runCatching { ctx.android.startActivity(toggle.settingsIntent()) }.isSuccess ||
                    runCatching { ctx.android.startActivity(Intent(toggle.settings).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            return ToggleOutput(toggle, on, if (opened) ToggleOutput.Result.ByHand else ToggleOutput.Result.Failed(""))
        }
        return ToggleOutput(toggle, on, flip(toggle, on))
    }

    companion object {
        suspend fun flip(toggle: Toggle, on: Boolean): ToggleOutput.Result {
            // no shell switch flips the hotspot or night light, but Settings' own app functions do
            if (toggle.stateKey != null) {
                return when (val outcome = DeviceState.set(toggle.stateKey, on.toString())) {
                    is AppFunctions.Outcome.Done -> ToggleOutput.Result.Done
                    AppFunctions.Outcome.NoShizuku -> ToggleOutput.Result.NeedsShizuku
                    is AppFunctions.Outcome.Failed -> ToggleOutput.Result.Failed(outcome.reason)
                }
            }
            val command = toggle.command(on) ?: return ToggleOutput.Result.ByHand
            val result = Shell.run(command) ?: return ToggleOutput.Result.NeedsShizuku
            return if (result.ok) ToggleOutput.Result.Done else ToggleOutput.Result.Failed(result.output)
        }
    }
}
