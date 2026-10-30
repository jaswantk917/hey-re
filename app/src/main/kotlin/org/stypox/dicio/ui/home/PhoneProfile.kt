package org.stypox.dicio.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The phone owner's own contact card ("Me" in Contacts), read with the contacts permission. */
data class PhoneProfile(val name: String, val photo: Uri?) {
    val firstName: String get() = name.trim().substringBefore(' ')

    companion object {
        // read once per process: the top bar and the greeting both want it
        @Volatile
        private var cached: PhoneProfile? = null

        suspend fun load(context: Context): PhoneProfile? {
            cached?.let { return it }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED
            ) return null
            return withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.query(
                        ContactsContract.Profile.CONTENT_URI,
                        arrayOf(
                            ContactsContract.Profile.DISPLAY_NAME,
                            ContactsContract.Profile.PHOTO_THUMBNAIL_URI,
                        ),
                        null, null, null,
                    )?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use null
                        val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: return@use null
                        PhoneProfile(name, cursor.getString(1)?.let(Uri::parse))
                    }
                } catch (e: Exception) {
                    Log.w("PhoneProfile", "Could not read the profile", e)
                    null
                }
            }?.also { cached = it }
        }

        fun openIntent(): Intent = Intent(Intent.ACTION_VIEW, ContactsContract.Profile.CONTENT_URI)
    }
}

@Composable
fun rememberPhoneProfile(): PhoneProfile? {
    val context = LocalContext.current
    val profile by produceState<PhoneProfile?>(null) { value = PhoneProfile.load(context) }
    return profile
}
