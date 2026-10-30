package org.stypox.dicio.skills.message

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.util.Log
import androidx.annotation.StringRes
import org.stypox.dicio.R
import java.util.Locale

enum class MessageApp(@StringRes val label: Int, @StringRes val ready: Int) {
    WhatsApp(R.string.skill_message_via_whatsapp, R.string.skill_message_ready_whatsapp),
    Sms(R.string.skill_message_via_sms, R.string.skill_message_ready_sms),
    Slack(R.string.skill_message_via_slack, R.string.skill_message_ready_slack),
}

/** [address] is the number to write to; null for Slack, where the person is picked in Slack. */
data class Recipient(
    val name: String,
    val address: String?,
    val app: MessageApp,
    val confident: Boolean,
) {
    /** WhatsApp gives bare digits ("919876543210"); show them as the dialer would. */
    fun shownNumber(): String? = when {
        address == null -> null
        app == MessageApp.WhatsApp ->
            PhoneNumberUtils.formatNumber("+$address", Locale.getDefault().country) ?: "+$address"
        else -> address
    }
}

/**
 * Opens the chat with the text already typed, leaving the final send to the user: neither WhatsApp
 * nor Slack let another app send for them, and SMS goes the same way so all three feel alike.
 */
object Messenger {
    const val WHATSAPP = "com.whatsapp"
    private const val SLACK = "com.Slack"
    private val TAG = Messenger::class.simpleName

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun open(context: Context, recipient: Recipient, text: String): Boolean {
        val intent = when (recipient.app) {
            MessageApp.WhatsApp -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://api.whatsapp.com/send?phone=${recipient.address}&text=${Uri.encode(text)}"),
            ).setPackage(WHATSAPP)
            MessageApp.Sms -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(recipient.address)}"))
                .putExtra("sms_body", text)
            MessageApp.Slack -> Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
                .setPackage(SLACK)
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Could not open ${recipient.app}", e)
            false
        }
    }
}
