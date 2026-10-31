package org.stypox.dicio.skills.notify

import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.stypox.dicio.skills.brief.Attention

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

    /**
     * The notifications that might need the person, for the brief: missed calls, chats, live
     * updates (a delivery on its way) and the rest that alert. Media, Hey re's own, and group
     * summaries are left out.
     */
    fun attentionCandidates(): List<Attention.Candidate> = activeNotifications.mapNotNull { sbn ->
        val notification = sbn.notification
        if (sbn.packageName == packageName || notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) {
            return@mapNotNull null
        }
        val extras = notification.extras
        if (extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION)) return@mapNotNull null
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return@mapNotNull null
        val ranking = NotificationListenerService.Ranking()
        val ranked = currentRanking.getRanking(sbn.key, ranking)
        val promoted = Build.VERSION.SDK_INT >= 36 &&
                notification.flags and android.app.Notification.FLAG_PROMOTED_ONGOING != 0
        val kind = when {
            notification.category == android.app.Notification.CATEGORY_MISSED_CALL -> Attention.Kind.MissedCall
            sbn.isOngoing -> if (promoted || notification.category in LIVE_CATEGORIES) Attention.Kind.Live
            else return@mapNotNull null
            !ranked || !isAlerting(ranking.importance) -> return@mapNotNull null
            notification.category == android.app.Notification.CATEGORY_MESSAGE || ranking.isConversation ->
                Attention.Kind.Message
            else -> Attention.Kind.Other
        }
        Attention.Candidate(
            kind = kind,
            app = appName(sbn.packageName),
            title = title,
            text = text,
            postedAtMillis = sbn.postTime,
            count = extras.getParcelableArray(android.app.Notification.EXTRA_MESSAGES)?.size?.coerceAtLeast(1) ?: 1,
            isGroup = extras.getBoolean(android.app.Notification.EXTRA_IS_GROUP_CONVERSATION),
            packageName = sbn.packageName,
            open = notification.contentIntent,
        )
    }

    private fun appName(packageName: String) = try {
        this.packageManager.getApplicationLabel(this.packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        packageName
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

// ongoing notifications worth a mention besides Android 16's promoted "live updates"; plain
// progress (backups, downloads) is not, nor the call the person is already on
private val LIVE_CATEGORIES = setOf(android.app.Notification.CATEGORY_NAVIGATION)

internal fun isAlerting(importance: Int): Boolean = importance >= NotificationManager.IMPORTANCE_DEFAULT

/**
 * Either [message] or [title] will be non-empty
 */
data class Notification(
    val appName: String,
    val title: String,
    val message: String,
)