package org.stypox.dicio.skills.sound

import android.app.NotificationManager
import android.media.AudioManager
import android.util.Log
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.SoundMode

/** Silent, vibrate, ring and Do Not Disturb, as the quick settings tiles set them. */
class SoundModeSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<SoundMode>) :
    StandardRecognizerSkill<SoundMode>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: SoundMode): SkillOutput {
        val audio = ctx.android.getSystemService(AudioManager::class.java)
        val notifications = ctx.android.getSystemService(NotificationManager::class.java)
        val mode = when (inputData) {
            SoundMode.Silent -> Mode.Silent
            SoundMode.Vibrate -> Mode.Vibrate
            SoundMode.Ring -> Mode.Ring
            SoundMode.DndOn -> Mode.DndOn
            SoundMode.DndOff -> Mode.DndOff
        }
        // vibrate needs nothing; silent and Do Not Disturb need Do Not Disturb access
        if (mode != Mode.Vibrate && mode != Mode.Ring && !notifications.isNotificationPolicyAccessGranted) {
            return SoundModeOutput.NeedsAccess(mode)
        }
        return try {
            when (mode) {
                Mode.Silent -> audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                Mode.Vibrate -> audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                Mode.Ring -> audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
                Mode.DndOn -> notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                Mode.DndOff -> notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            SoundModeOutput.Set(mode)
        } catch (e: SecurityException) {
            // leaving silent can mean leaving Do Not Disturb, which needs the access too
            Log.w(TAG, "Could not set $mode", e)
            SoundModeOutput.NeedsAccess(mode)
        }
    }

    enum class Mode { Silent, Vibrate, Ring, DndOn, DndOff }

    companion object {
        private val TAG = SoundModeSkill::class.simpleName
    }
}
