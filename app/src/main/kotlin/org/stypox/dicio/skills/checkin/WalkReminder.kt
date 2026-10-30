package org.stypox.dicio.skills.checkin

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.stypox.dicio.MainActivity
import org.stypox.dicio.R
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * A nudge to walk at the office breaks, a little after lunch and at tea, on workdays. It stays
 * quiet when the steps are on track; without steps to look at it nudges anyway.
 */
object WalkReminder {
    private val TIMES = listOf(LocalTime.of(14, 20), Routine.TEA)
    private const val CHANNEL = "walk"

    /** Sets the next reminder; safe to call again, as it replaces the one already set. */
    fun scheduleNext(context: Context, after: LocalDateTime = LocalDateTime.now()) {
        val next = generateSequence(after.toLocalDate()) { it.plusDays(1) }
            .take(8)
            .flatMap { day -> TIMES.map { day.atTime(it) } }
            .first { it.isAfter(after) && Routine.isWorkday(it) }
        val alarms = context.getSystemService(AlarmManager::class.java)
        val atMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent(context))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent(context))
        }
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, WalkReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun notify(context: Context, steps: Long?, atLunch: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.walk_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val title = context.getString(if (atLunch) R.string.walk_reminder_lunch else R.string.walk_reminder_tea)
        val text = steps?.let {
            context.getString(R.string.walk_reminder_steps, NumberFormat.getInstance().format(it))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private const val NOTIFICATION_ID = 4120
}

class WalkReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WalkReminder.scheduleNext(context)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = LocalDateTime.now()
                // reading steps here needs the background permission; without it, nudge anyway
                val steps = (withTimeoutOrNull(5_000) { HealthSteps.today(context) } as? Section.Ready)?.value
                if (steps == null || Routine.isBehind(steps, now)) {
                    WalkReminder.notify(context, steps, atLunch = now.toLocalTime() < Routine.TEA)
                }
            } catch (e: Exception) {
                Log.w("WalkReminderReceiver", "Walk reminder failed", e)
            } finally {
                WalkReminder.scheduleNext(context, LocalDateTime.now().plusMinutes(1))
                pending.finish()
            }
        }
    }
}
