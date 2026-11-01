package org.stypox.dicio.io.input.ondevice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The words this person is likely to say, for the recognizer to lean towards: the names it
 * otherwise mishears ("Pranav", "Swiggy") and Hey re's own Hinglish. They stay on the phone,
 * going only to the on-device recognizer.
 */
object BiasingWords {
    suspend fun gather(context: Context): ArrayList<String> = withContext(Dispatchers.IO) {
        words(contacts(context), apps(context))
    }

    /** Hey re's words first, then contacts, then apps, each once, up to [MAX_WORDS]. */
    internal fun words(contacts: List<String>, apps: List<String>): ArrayList<String> {
        val seen = mutableSetOf<String>()
        val firstNames = contacts.mapNotNull { it.split(' ').firstOrNull()?.takeIf { first -> first != it } }
        return (COMMAND_WORDS + contacts + firstNames + apps)
            .map { it.trim() }
            .filter { it.length in MIN_CHARS..MAX_CHARS && seen.add(it.lowercase()) }
            .take(MAX_WORDS)
            .toCollection(ArrayList())
    }

    // favourites and the most contacted first, since only so many words fit
    private fun contacts(context: Context): List<String> {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return listOf()
        }
        return try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                null,
                null,
                "${ContactsContract.Contacts.STARRED} DESC, ${ContactsContract.Contacts.LAST_TIME_CONTACTED} DESC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < MAX_CONTACTS) {
                        cursor.getString(0)?.let(::add)
                    }
                }
            }.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read contact names", e)
            listOf()
        }
    }

    private fun apps(context: Context): List<String> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(launcher, 0)
            .map { it.loadLabel(context.packageManager).toString() }
    }

    private const val TAG = "BiasingWords"
    private const val MIN_CHARS = 2
    private const val MAX_CHARS = 40
    private const val MAX_CONTACTS = 80
    private const val MAX_WORDS = 200

    private val COMMAND_WORDS = listOf(
        "Hey re", "chalao", "chalu karo", "band karo", "bajao", "lagao", "rok do", "agla gaana",
        "pichla gaana", "yaad rakhna", "yaad dilana", "kahan hai", "kidhar hai", "kitne baje",
        "call karo", "message karo", "mausam", "torch", "hotspot", "brightness", "battery",
        "timer", "alarm", "reminder", "Maa", "Papa",
    )
}
