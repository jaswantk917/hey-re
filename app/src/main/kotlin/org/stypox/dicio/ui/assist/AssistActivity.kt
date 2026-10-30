package org.stypox.dicio.ui.assist

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import org.stypox.dicio.MainActivity
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.util.BaseActivity
import java.time.Instant
import javax.inject.Inject

/**
 * Handles the assist gesture (long-pressing home, the assistant button) by showing [AssistSheet]
 * over the current app, translucent, instead of opening the full app.
 */
@AndroidEntryPoint
class AssistActivity : BaseActivity() {

    @Inject
    lateinit var sttInputDevice: SttInputDeviceWrapper

    @Inject
    lateinit var skillEvaluator: SkillEvaluator

    private var nextAssistAllowed = Instant.MIN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the sheet animates itself; the system's own slide would carry the dimmed scrim with it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        if (MainActivity.isInForeground > 0) {
            // Hey re is already on screen: listen right there instead of opening a sheet over it
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_ASSIST)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
            finish()
            return
        }
        if (savedInstanceState == null) {
            startListening()
        }

        composeSetContent {
            AssistSheet(
                onDismiss = ::finish,
                onOpenApp = {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )
                    finish()
                },
            )
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // the gesture again while the sheet is open: listen again
        startListening()
    }

    /**
     * Android sometimes delivers the assist intent twice in a row, hence the short backoff.
     */
    private fun startListening() {
        val now = Instant.now()
        if (nextAssistAllowed < now) {
            nextAssistAllowed = now.plusMillis(INTENT_BACKOFF_MILLIS)
            sttInputDevice.tryLoad(skillEvaluator::processInputEvent)
        } else {
            Log.w(TAG, "Ignoring duplicate assist intent")
        }
    }

    companion object {
        private const val INTENT_BACKOFF_MILLIS = 100L
        private val TAG = AssistActivity::class.simpleName
    }
}
