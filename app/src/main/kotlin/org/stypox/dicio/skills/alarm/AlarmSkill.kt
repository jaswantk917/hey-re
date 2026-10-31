package org.stypox.dicio.skills.alarm

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.Alarm

/** Alarms live in the phone's Clock app, where they ring, snooze and survive reboots. */
class AlarmSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Alarm>) :
    StandardRecognizerSkill<Alarm>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Alarm): SkillOutput {
        return when (inputData) {
            is Alarm.Set -> {
                val time = inputData.time?.let { AlarmTime.parse(it) }
                    ?: return AlarmOutput.NoTime
                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, time.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                val reminder = inputData.name?.trim()?.takeIf { it.isNotEmpty() }
                reminder?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                if (start(ctx, intent)) AlarmOutput.Set(time, reminder) else AlarmOutput.NoClockApp
            }
            is Alarm.Show -> {
                if (start(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))) AlarmOutput.Shown
                else AlarmOutput.NoClockApp
            }
        }
    }

    private fun start(ctx: SkillContext, intent: Intent): Boolean = try {
        ctx.android.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No clock app for ${intent.action}", e)
        false
    }

    companion object {
        private val TAG = AlarmSkill::class.simpleName
    }
}
