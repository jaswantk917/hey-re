package org.stypox.dicio.io.assist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import org.stypox.dicio.ui.assist.AssistActivity

/**
 * Being the phone's assistant service (rather than only an ACTION_ASSIST activity) is what lets
 * Android hand over the text and screenshot of the app in front when the gesture summons Hey re.
 */
class HeyReVoiceService : VoiceInteractionService() {
    // the lock screen's assistant shortcut: the sheet shows over the lock screen
    override fun onLaunchVoiceAssistFromKeyguard() {
        startActivity(
            Intent(this, AssistActivity::class.java)
                .setAction(Intent.ACTION_ASSIST)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

class HeyReSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = HeyReSession(this)
}

/** Has no window of its own: it collects the screen and opens [AssistActivity] over the app. */
class HeyReSession(context: Context) : VoiceInteractionSession(context) {
    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        // an empty session window above the sheet would swallow taps meant for it
        setUiEnabled(false)
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.setEmpty()
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        ScreenContext.begin()
        ScreenContext.hideSession = { hide() }
        startAssistantActivity(Intent(context, AssistActivity::class.java))
    }

    override fun onHandleAssist(state: AssistState) {
        if (!state.isFocused) return
        val structure = state.assistStructure ?: return
        val packageName = structure.activityComponent?.packageName
        if (packageName == context.packageName) return
        state.assistContent?.webUri?.takeIf { it.scheme == "https" || it.scheme == "http" }
            ?.let { ScreenContext.setLink(it.toString()) }
        ScreenContext.setText(packageName, appName(packageName), ScreenContext.textOf(structure))
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        if (screenshot != null) ScreenContext.setScreenshot(screenshot)
    }

    private fun appName(packageName: String?): String? = packageName?.let {
        try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(it, 0)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
