package org.stypox.dicio.skills.toggle

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
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
        }
        return ToggleOutput(toggle, on, flip(toggle, on))
    }

    companion object {
        suspend fun flip(toggle: Toggle, on: Boolean): ToggleOutput.Result {
            val result = Shell.run(toggle.command(on)) ?: return ToggleOutput.Result.NeedsShizuku
            return if (result.ok) ToggleOutput.Result.Done else ToggleOutput.Result.Failed(result.output)
        }
    }
}
