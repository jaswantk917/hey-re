package org.stypox.dicio.skills.brief

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.core.content.getSystemService
import kotlinx.coroutines.withTimeoutOrNull
import org.stypox.dicio.skills.checkin.GoogleTasks
import org.stypox.dicio.skills.checkin.Section
import org.stypox.dicio.skills.notify.NotifyHandler

data class NowPlaying(val title: String, val artist: String?, val app: String)

/** Where the brief looks: notifications, Google Tasks, the battery, storage and what's playing. */
object BriefSources {
    suspend fun candidates(context: Context): List<Attention.Candidate> {
        val notifications = try {
            NotifyHandler.Instance?.attentionCandidates().orEmpty()
        } catch (e: SecurityException) {
            listOf() // the listener is enabled but not connected yet
        }
        return notifications + tasks(context) + health(context)
    }

    private suspend fun tasks(context: Context): List<Attention.Candidate> {
        val digest = withTimeoutOrNull(TASKS_TIMEOUT_MILLIS) { GoogleTasks.digest(context) }
        val due = (digest as? Section.Ready)?.value?.due.orEmpty()
        return due.map { Attention.Candidate(Attention.Kind.Task, "Google Tasks", it, "") }
    }

    private fun health(context: Context): List<Attention.Candidate> {
        val battery = context.getSystemService<BatteryManager>()
        val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val lowBattery = battery != null && !battery.isCharging && level in 1 until LOW_BATTERY
        val stat = StatFs(Environment.getDataDirectory().path)
        val freePercent = stat.availableBytes * 100 / stat.totalBytes.coerceAtLeast(1)
        return listOfNotNull(
            Attention.Candidate(Attention.Kind.Battery, "", "$level%", "").takeIf { lowBattery },
            Attention.Candidate(Attention.Kind.Storage, "", "$freePercent%", "").takeIf { freePercent < LOW_STORAGE },
        )
    }

    /** The song or video playing right now, through the notification access the app already has. */
    fun nowPlaying(context: Context): NowPlaying? = try {
        context.getSystemService<MediaSessionManager>()
            ?.getActiveSessions(ComponentName(context, NotifyHandler::class.java))
            ?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?.let { controller ->
                val metadata = controller.metadata ?: return@let null
                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return@let null
                val app = runCatching {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(controller.packageName, 0)
                    ).toString()
                }.getOrDefault(controller.packageName)
                NowPlaying(title, metadata.getString(MediaMetadata.METADATA_KEY_ARTIST), app)
            }
    } catch (e: SecurityException) {
        null // no notification access
    }

    private const val TASKS_TIMEOUT_MILLIS = 4_000L
    private const val LOW_BATTERY = 20
    private const val LOW_STORAGE = 10
}
