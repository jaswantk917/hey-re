package org.stypox.dicio.skills.checkin

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Open tasks from Google Tasks, read with the user's consent through Google's own sign-in sheet.
 * Uses the same Cloud project and Android OAuth client as Google Home.
 */
object GoogleTasks {
    private const val SCOPE = "https://www.googleapis.com/auth/tasks.readonly"
    private const val API = "https://tasks.googleapis.com/tasks/v1"

    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .build()

    /** The consent sheet to show when Hey re may not read tasks yet; null once it may. */
    suspend fun consent(context: Context): PendingIntent? = try {
        authorize(context).takeIf { it.hasResolution() }?.pendingIntent
    } catch (e: Exception) {
        Log.w(TAG, "Tasks authorization failed", e)
        null
    }

    suspend fun digest(context: Context): Section<TaskDigest> {
        val token = try {
            val result = authorize(context)
            if (result.hasResolution()) return Section.NeedsAccess
            result.accessToken ?: return Section.Unavailable
        } catch (e: Exception) {
            Log.w(TAG, "Tasks authorization failed", e)
            return Section.Unavailable
        }
        return try {
            withContext(Dispatchers.IO) {
                val tasks = get("$API/users/@me/lists?maxResults=20", token).optJSONArray("items")
                    .objects().flatMap { list ->
                        val id = list.getString("id")
                        get("$API/lists/$id/tasks?showCompleted=false&maxResults=100", token)
                            .optJSONArray("items").objects()
                            .map { OpenTask(it.optString("title"), it.optString("due").ifEmpty { null }) }
                    }
                Section.Ready(digestOf(tasks, LocalDate.now()))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read tasks", e)
            Section.Unavailable
        }
    }

    data class OpenTask(val title: String, val due: String?)

    /** Tasks due today or overdue, soonest first; Tasks keeps due dates as midnight UTC. */
    fun digestOf(tasks: List<OpenTask>, today: LocalDate): TaskDigest {
        val named = tasks.filter { it.title.isNotBlank() }
        val due = named
            .mapNotNull { task -> task.due?.take(10)?.let { LocalDate.parse(it) }?.let { task.title to it } }
            .filter { !it.second.isAfter(today) }
            .sortedBy { it.second }
            .map { it.first }
        return TaskDigest(due = due, open = named.size)
    }

    private suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request()).await()

    private fun get(url: String, token: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 4_000
        connection.readTimeout = 4_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val error = connection.errorStream?.bufferedReader()?.readText()
                throw IllegalStateException("Tasks API ${connection.responseCode}: $error")
            }
            return JSONObject(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }

    private fun org.json.JSONArray?.objects(): List<JSONObject> =
        if (this == null) listOf() else (0 until length()).map { getJSONObject(it) }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }

    private val TAG = GoogleTasks::class.simpleName
}
