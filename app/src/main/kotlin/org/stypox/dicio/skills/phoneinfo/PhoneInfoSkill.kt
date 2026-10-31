package org.stypox.dicio.skills.phoneinfo

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.io.shell.AppFunctions.Outcome
import org.stypox.dicio.io.shell.DeviceState
import org.stypox.dicio.sentences.Sentences.PhoneInfo
import org.stypox.dicio.util.HinglishNumbers

/** "Battery kitni hai", "storage kitna bacha", "brightness kam karo", read and set through Settings. */
class PhoneInfoSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<PhoneInfo>) :
    StandardRecognizerSkill<PhoneInfo>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: PhoneInfo): SkillOutput = when (inputData) {
        PhoneInfo.Battery -> read(DeviceState.BATTERY_LEVEL, DeviceState.BATTERY_STATUS) { (level, status) ->
            PhoneInfoOutput.Battery(level.toIntOrNull() ?: return@read null, status == CHARGING)
        }
        PhoneInfo.Storage -> read(DeviceState.STORAGE_USED, DeviceState.STORAGE_TOTAL) { (used, total) ->
            PhoneInfoOutput.Storage(amount(used), amount(total))
        }
        PhoneInfo.BrightnessUp -> brightness { (it + STEP).coerceAtMost(100) }
        PhoneInfo.BrightnessDown -> brightness { (it - STEP).coerceAtLeast(MIN_BRIGHTNESS) }
        is PhoneInfo.BrightnessSet -> level(inputData.level)?.let { wanted -> brightness { wanted } }
            ?: PhoneInfoOutput.Failed
    }

    private suspend fun read(vararg keys: String, answer: (List<String>) -> SkillOutput?): SkillOutput {
        val values = keys.map { key ->
            when (val outcome = DeviceState.get(key)) {
                is Outcome.Done -> outcome.value as String
                Outcome.NoShizuku -> return PhoneInfoOutput.NeedsShizuku
                is Outcome.Failed -> return PhoneInfoOutput.Failed
            }
        }
        return answer(values) ?: PhoneInfoOutput.Failed
    }

    private suspend fun brightness(change: (Int) -> Int): SkillOutput {
        val now = when (val outcome = DeviceState.get(DeviceState.BRIGHTNESS)) {
            is Outcome.Done -> (outcome.value as String).toIntOrNull() ?: return PhoneInfoOutput.Failed
            Outcome.NoShizuku -> return PhoneInfoOutput.NeedsShizuku
            is Outcome.Failed -> return PhoneInfoOutput.Failed
        }
        val wanted = change(now).coerceIn(0, 100)
        return when (DeviceState.set(DeviceState.BRIGHTNESS, wanted.toString())) {
            is Outcome.Done -> PhoneInfoOutput.Brightness(from = now, to = wanted)
            Outcome.NoShizuku -> PhoneInfoOutput.NeedsShizuku
            is Outcome.Failed -> PhoneInfoOutput.Failed
        }
    }

    companion object {
        // BatteryManager.BATTERY_STATUS_CHARGING, which Settings reports as is
        private const val CHARGING = "2"
        private const val STEP = 20
        // fully dark is never what "kam karo" means
        private const val MIN_BRIGHTNESS = 5

        /** "50", "pachaas", "fifty percent" -> 50. */
        internal fun level(said: String?): Int? = said
            ?.let { HinglishNumbers.normalize(it) }
            ?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
            ?.takeIf { it in 0..100 }

        /** "190 GB used" -> "190 GB". */
        internal fun amount(text: String) = text.replace(Regex("\\s+(used|total)$", RegexOption.IGNORE_CASE), "")
    }
}
