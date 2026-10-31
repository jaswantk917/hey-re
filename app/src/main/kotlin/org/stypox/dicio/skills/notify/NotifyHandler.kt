package org.stypox.dicio.skills.notify

import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

open class NotifyHandler: NotificationListenerService() {
    companion object Companion {
        private const val TAG: String = "NotifyHandler"
        var Instance: NotifyHandler? = null
    }

    override fun onCreate() {
        super.onCreate()
        Instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        Instance = null
    }

    // silent ones (promotions, "Silent" in the shade, chats the user muted) aren't worth telling about
    private fun alerts(sbn: StatusBarNotification): Boolean {
        val ranking = NotificationListenerService.Ranking()
        if (!currentRanking.getRanking(sbn.key, ranking)) return true
        return isAlerting(ranking.importance)
    }

    fun getActiveNotificationsList(): List<Notification> {
        // getActiveNotifications() can only be run in NotificationListenerService() class
        return getActiveNotifications().filter(::alerts).mapNotNull { notification ->
            val title = notification.notification.extras.getString("android.title")
            val message = notification.notification.extras.getString("android.text")
            if (title.isNullOrBlank() && message.isNullOrBlank()) {
                return@mapNotNull null // skip empty notifications (like from android_system)
            }
            Log.e(TAG, notification.notification.extras.toString())

            val appName = try {
                val appInfo = packageManager.getApplicationInfo(notification.packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Could not get app name", e)
                ""
            }

            return@mapNotNull Notification(appName, title ?: "", message ?: "")
        }
    }

    /** How many notifications each app has up, skipping silent and ongoing ones and group summaries. */
    fun appCounts(): Map<String, Int> = activeNotifications
        .filter { sbn ->
            alerts(sbn) && !sbn.isOngoing && sbn.packageName != packageName &&
                    sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0 &&
                    sbn.notification.extras.let {
                        !it.getCharSequence("android.title").isNullOrBlank() ||
                                !it.getCharSequence("android.text").isNullOrBlank()
                    }
        }
        .groupingBy { sbn ->
            try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            } catch (e: Exception) {
                sbn.packageName
            }
        }
        .eachCount()
}

internal fun isAlerting(importance: Int): Boolean = importance >= NotificationManager.IMPORTANCE_DEFAULT

/**
 * Either [message] or [title] will be non-empty
 */
data class Notification(
    val appName: String,
    val title: String,
    val message: String,
)