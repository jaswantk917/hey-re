package org.stypox.dicio.skills.message

import android.util.Log
import kotlinx.coroutines.delay
import org.stypox.dicio.io.shell.Shell

/**
 * Presses WhatsApp's send button once the chat opens with the text typed, which WhatsApp lets no
 * app do through an intent. Finds the button in a UI dump and taps it as the user would.
 */
object WhatsAppSender {
    private const val DUMP = "/data/local/tmp/heyre-ui.xml"
    private val SEND_BUTTON = Regex(
        """resource-id="com\.whatsapp:id/send"[^>]*?bounds="\[(\d+),(\d+)]\[(\d+),(\d+)]""""
    )

    /** True once the message has gone; false if the button never showed up or stayed there. */
    suspend fun pressSend(): Boolean {
        repeat(ATTEMPTS) {
            delay(POLL_MILLIS)
            val bounds = sendButton() ?: return@repeat
            val tap = Shell.run("input tap ${(bounds[0] + bounds[2]) / 2} ${(bounds[1] + bounds[3]) / 2}")
            if (tap?.ok != true) return false
            delay(POLL_MILLIS)
            // the button turns back into the microphone once the text is gone
            return sendButton() == null
        }
        Log.w(TAG, "WhatsApp's send button never showed up")
        return false
    }

    private suspend fun sendButton(): List<Int>? {
        val dump = Shell.run("uiautomator dump $DUMP >/dev/null && cat $DUMP", timeoutMillis = 6_000)
            ?.takeIf { it.ok } ?: return null
        val match = SEND_BUTTON.find(dump.output) ?: return null
        return match.groupValues.drop(1).map { it.toInt() }
    }

    private const val ATTEMPTS = 6
    private const val POLL_MILLIS = 700L
    private val TAG = WhatsAppSender::class.simpleName
}
