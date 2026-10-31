package org.stypox.dicio.io.assist

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

fun Context.isLocked(): Boolean = getSystemService(KeyguardManager::class.java).isKeyguardLocked

/** Shows Android's unlock screen over [this]; true once the phone is unlocked. */
suspend fun Activity.requestUnlock(): Boolean = withContext(Dispatchers.Main) {
    if (!isLocked()) return@withContext true
    suspendCancellableCoroutine { continuation ->
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(
            this@requestUnlock,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = continuation.resume(true)
                override fun onDismissCancelled() = continuation.resume(false)
                override fun onDismissError() = continuation.resume(false)
            },
        )
    }
}
