package org.stypox.dicio.skills.alarm

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.io.graphical.Subtitle
import org.stypox.dicio.util.getString
import java.time.LocalTime
import java.time.format.DateTimeFormatter

sealed interface AlarmOutput : SkillOutput {
    class Set(private val time: LocalTime, private val reminder: String? = null) : AlarmOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            if (reminder == null) ctx.getString(R.string.skill_alarm_set, format(ctx, time))
            else ctx.getString(R.string.skill_alarm_reminder_set, format(ctx, time), reminder)

        // the time big, like the Clock app shows it
        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Column {
                Text(
                    text = format(ctx, time),
                    style = MaterialTheme.typography.displayMediumEmphasized,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                reminder?.let { Headline(text = it) }
                Subtitle(text = ctx.getString(R.string.skill_alarm_in_clock))
            }
        }
    }

    data object Shown : AlarmOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(R.string.skill_alarm_shown)
    }

    data object NoTime : AlarmOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(R.string.skill_alarm_no_time)
    }

    data object NoClockApp : AlarmOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = ctx.getString(R.string.skill_alarm_no_clock)
    }
}

private fun format(ctx: SkillContext, time: LocalTime): String = time.format(
    DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx.android)) "HH:mm" else "h:mm a", ctx.locale)
)
