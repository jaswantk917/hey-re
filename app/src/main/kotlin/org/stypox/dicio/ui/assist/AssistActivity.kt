package org.stypox.dicio.ui.assist

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.stypox.dicio.MainActivity
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.skills.home.HomeControl
import org.stypox.dicio.io.assist.Article
import org.stypox.dicio.io.assist.ScreenContext
import org.stypox.dicio.io.assist.requestUnlock
import org.stypox.dicio.io.assist.ScreenSummary
import org.stypox.dicio.io.graphical.Countdown
import org.stypox.dicio.skills.localbrain.LocalBrain
import org.stypox.dicio.util.BaseActivity
import org.stypox.dicio.util.UsePermissionRequester
import java.time.Instant
import javax.inject.Inject

/**
 * Handles the assist gesture (long-pressing home, the assistant button) by showing [AssistSheet]
 * over the current app, translucent, instead of opening the full app. Opened by
 * [org.stypox.dicio.io.assist.HeyReSession], which also passes along what's on screen.
 */
@AndroidEntryPoint
class AssistActivity : BaseActivity() {

    @Inject
    lateinit var sttInputDevice: SttInputDeviceWrapper

    @Inject
    lateinit var skillEvaluator: SkillEvaluator

    @Inject
    lateinit var localBrain: LocalBrain

    @Inject
    lateinit var homeControl: HomeControl

    private var nextAssistAllowed = Instant.MIN
    // false when it only handed the gesture on to the app already open, which is now listening
    private var showsSheet = false

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
            ScreenContext.end()
            finish()
            return
        }
        showsSheet = true
        homeControl.register(this)
        // over the lock screen too; what's personal asks for an unlock (SkillEvaluator)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        if (savedInstanceState == null) {
            startListening()
        }
        summarizeScreen()
        summarizeArticle()

        composeSetContent {
            UsePermissionRequester(skillEvaluator)
            AssistSheet(
                onDismiss = ::finish,
                onOpenApp = {
                    // still listening: carry on in the app instead of cutting the person off
                    showsSheet = false
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )
                    finish()
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        skillEvaluator.unlockRequester = ::requestUnlock
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            // closing the sheet (or leaving it) means not now: no call or message behind the user's back
            Countdown.cancelAll(this)
            if (showsSheet) sttInputDevice.cancelListening()
            ScreenContext.end()
        }
    }

    /**
     * Gets a summary of the screen ready while the user is still speaking, in case they want it;
     * the text and the screenshot arrive separately, so it waits a moment for both.
     */
    private fun summarizeScreen() {
        lifecycleScope.launch {
            ScreenContext.snapshot.filterNotNull()
                .distinctUntilChangedBy { Pair(it.text, it.screenshot != null) }
                .collectLatest { screen ->
                    delay(SCREEN_SETTLE_MILLIS)
                    if (screen.text.isBlank() && screen.screenshot == null) return@collectLatest
                    if (!localBrain.isReady()) {
                        ScreenContext.setSummary(ScreenSummary.Unavailable)
                        return@collectLatest
                    }
                    ScreenContext.setSummary(ScreenSummary.Working)
                    val summary = localBrain.summarizeScreen(screen)
                    ScreenContext.setSummary(summary?.let { ScreenSummary.Ready(it) } ?: ScreenSummary.Unavailable)
                }
        }
    }

    /** The whole page behind a link, alongside the screen, for when what's visible isn't enough. */
    private fun summarizeArticle() {
        lifecycleScope.launch {
            ScreenContext.snapshot.mapNotNull { it?.link }
                .distinctUntilChanged()
                .filter { Article.worthFetching(it) }
                .collectLatest { link ->
                    if (!localBrain.isReady()) return@collectLatest
                    ScreenContext.setArticle(ScreenSummary.Working)
                    val page = withContext(Dispatchers.IO) {
                        try {
                            Article.fetch(link)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not read the article", e)
                            null
                        }
                    }
                    if (page == null) Log.i(TAG, "Nothing to summarise behind the link")
                    val summary = page?.let { localBrain.summarizeArticle(it) }
                    ScreenContext.setArticle(summary?.let { ScreenSummary.Ready(it) } ?: ScreenSummary.Unavailable)
                }
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
        private const val SCREEN_SETTLE_MILLIS = 300L
        private val TAG = AssistActivity::class.simpleName
    }
}
