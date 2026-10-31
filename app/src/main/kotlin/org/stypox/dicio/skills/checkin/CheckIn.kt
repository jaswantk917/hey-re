package org.stypox.dicio.skills.checkin

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.stypox.dicio.skills.notify.NotifyHandler

/** One part of the check-in: there, waiting for the user to allow it, or not on this phone. */
sealed interface Section<out T> {
    data class Ready<T>(val value: T) : Section<T>
    data object NeedsAccess : Section<Nothing>
    data object Unavailable : Section<Nothing>
}

data class AppCount(val app: String, val count: Int)

data class TaskDigest(val due: List<String>, val open: Int)

/** "Hello, how are you": how the day is going, from steps, tasks and notifications. */
data class CheckIn(
    val steps: Section<Long>,
    val tasks: Section<TaskDigest>,
    val notifications: Section<List<AppCount>>,
) {
    companion object {
        suspend fun gather(context: Context): CheckIn = coroutineScope {
            val steps = async { withTimeoutOrNull(TIMEOUT_MILLIS) { HealthSteps.today(context) } }
            val tasks = async { withTimeoutOrNull(TIMEOUT_MILLIS) { GoogleTasks.digest(context) } }
            CheckIn(
                steps = steps.await() ?: Section.Unavailable,
                tasks = tasks.await() ?: Section.Unavailable,
                notifications = notifications(context),
            )
        }

        /** Only the steps, for "kitne steps hue". */
        suspend fun gatherSteps(context: Context): CheckIn = CheckIn(
            steps = withTimeoutOrNull(TIMEOUT_MILLIS) { HealthSteps.today(context) } ?: Section.Unavailable,
            tasks = Section.Unavailable,
            notifications = Section.Unavailable,
        )

        private fun notifications(context: Context): Section<List<AppCount>> {
            if (context.packageName !in NotificationManagerCompat.getEnabledListenerPackages(context)) {
                return Section.NeedsAccess
            }
            val counts = try {
                NotifyHandler.Instance?.appCounts()
            } catch (e: SecurityException) {
                // the listener is enabled but not connected yet
                null
            } ?: return Section.Unavailable
            return Section.Ready(counts.map { AppCount(it.key, it.value) }.sortedByDescending { it.count })
        }

        private const val TIMEOUT_MILLIS = 6_000L
    }
}
