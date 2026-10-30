package org.stypox.dicio.io.shell

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

/**
 * Shell commands through Shizuku, with the rights of `adb shell`: the switches Android keeps from
 * apps (Wi-Fi, mobile data, airplane mode) and tapping inside other apps.
 */
object Shell {
    enum class Status { Ready, NeedsPermission, NotRunning }

    data class Result(val exitCode: Int, val output: String) {
        val ok get() = exitCode == 0
    }

    private val _status = MutableStateFlow(Status.NotRunning)
    val status: StateFlow<Status> = _status

    init {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener { refresh() }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
    }

    fun refresh() {
        _status.value = when {
            !Shizuku.pingBinder() -> Status.NotRunning
            Shizuku.isPreV11() -> Status.NotRunning
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status.Ready
            else -> Status.NeedsPermission
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(REQUEST_CODE)
    }

    /** Runs [command] with `sh -c`; null when Shizuku isn't there to run it. */
    suspend fun run(command: String, timeoutMillis: Long = 8_000): Result? {
        refresh()
        if (status.value != Status.Ready) return null
        return withContext(Dispatchers.IO) {
            try {
                val process = newProcess.invoke(null, arrayOf("sh", "-c", command), null, null) as ShizukuRemoteProcess
                withTimeoutOrNull(timeoutMillis) {
                    val output = process.inputStream.bufferedReader().readText() +
                            process.errorStream.bufferedReader().readText()
                    Result(process.waitFor(), output.trim())
                } ?: run {
                    process.destroy()
                    Result(-1, "timed out")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not run $command", e)
                Result(-1, e.toString())
            }
        }
    }

    // private in the API, which prefers bound services; one-off commands don't need one
    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        ).apply { isAccessible = true }
    }

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4121
    private val TAG = Shell::class.simpleName
}
