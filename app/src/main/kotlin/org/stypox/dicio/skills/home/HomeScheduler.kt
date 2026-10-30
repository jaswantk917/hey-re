package org.stypox.dicio.skills.home

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
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.stypox.dicio.R
import javax.inject.Inject
import javax.inject.Singleton

/** "Cooler band, an hour from now": what to switch and when, kept until it has run or is cancelled. */
data class ScheduledSwitch(
    val id: Int,
    val targetIds: List<String>,
    val names: List<String>,
    val on: Boolean,
    val atMillis: Long,
)

/**
 * Runs Google Home switches later, like a sleep timer for the cooler. The phone's alarm clock wakes
 * [HomeSwitchReceiver] at the time even in Doze; Google Home itself isn't asked to schedule anything.
 */
@Singleton
class HomeScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun schedule(targets: List<HomeTarget>, on: Boolean, atMillis: Long): ScheduledSwitch {
        val switch = ScheduledSwitch(
            id = (atMillis / 1000).toInt() xor targets.hashCode(),
            targetIds = targets.map { it.id },
            names = targets.map { it.name },
            on = on,
            atMillis = atMillis,
        )
        write(read().filter { it.id != switch.id } + switch)
        val intent = pendingIntent(switch.id)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
        return switch
    }

    fun cancel(id: Int) {
        alarms.cancel(pendingIntent(id))
        write(read().filter { it.id != id })
    }

    internal fun take(id: Int): ScheduledSwitch? {
        val switch = read().firstOrNull { it.id == id } ?: return null
        write(read().filter { it.id != id })
        return switch
    }

    private fun pendingIntent(id: Int) = PendingIntent.getBroadcast(
        context, id,
        Intent(context, HomeSwitchReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun read(): List<ScheduledSwitch> = try {
        val array = JSONArray(prefs.getString(SCHEDULED, "[]"))
        (0 until array.length()).map { i ->
            array.getJSONObject(i).run {
                ScheduledSwitch(
                    id = getInt("id"),
                    targetIds = getJSONArray("targets").let { t -> (0 until t.length()).map { t.getString(it) } },
                    names = getJSONArray("names").let { n -> (0 until n.length()).map { n.getString(it) } },
                    on = getBoolean("on"),
                    atMillis = getLong("at"),
                )
            }
        }
    } catch (e: Exception) {
        listOf()
    }

    private fun write(switches: List<ScheduledSwitch>) {
        val array = JSONArray()
        switches.forEach {
            array.put(
                JSONObject().put("id", it.id).put("targets", JSONArray(it.targetIds))
                    .put("names", JSONArray(it.names)).put("on", it.on).put("at", it.atMillis)
            )
        }
        prefs.edit().putString(SCHEDULED, array.toString()).apply()
    }

    companion object {
        internal const val EXTRA_ID = "scheduled_switch_id"
        private const val PREFS = "google_home_schedule"
        private const val SCHEDULED = "scheduled"
    }
}

@AndroidEntryPoint
class HomeSwitchReceiver : BroadcastReceiver() {
    @Inject
    lateinit var scheduler: HomeScheduler

    @Inject
    lateinit var home: HomeControl

    override fun onReceive(context: Context, intent: Intent) {
        val switch = scheduler.take(intent.getIntExtra(HomeScheduler.EXTRA_ID, 0)) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val failed = withTimeoutOrNull(TIMEOUT_MILLIS) {
                    val devices = home.switchables().filter { it.target.id in switch.targetIds }
                    home.set(devices, switch.on).map { it.target.name } +
                            switch.names.filter { name -> devices.none { it.target.name == name } }
                } ?: switch.names
                notify(context, switch, failed)
            } catch (e: Exception) {
                Log.w(TAG, "Scheduled switch failed", e)
                notify(context, switch, switch.names)
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, switch: ScheduledSwitch, failed: List<String>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.skill_name_smart_home), NotificationManager.IMPORTANCE_LOW)
        )
        val names = switch.names.joinToString(", ")
        val text = when {
            failed.isNotEmpty() -> context.getString(R.string.skill_home_scheduled_failed, failed.joinToString(", "))
            switch.on -> context.getString(R.string.skill_home_on_one, names)
            else -> context.getString(R.string.skill_home_off_one, names)
        }
        manager.notify(
            switch.id,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_hearing_white)
                .setContentTitle(text)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        private val TAG = HomeSwitchReceiver::class.simpleName
        private const val CHANNEL = "google_home"
        // a broadcast may run about ten seconds before Android gives up on it
        private const val TIMEOUT_MILLIS = 8_000L
    }
}
