package org.stypox.dicio.settings

import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssistantDirection
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import org.stypox.dicio.R
import org.stypox.dicio.settings.ui.SettingsItem
import org.stypox.dicio.skills.home.HomeInfo

/**
 * What Hey re needs from Android and Google to do everything it can, each with where to fix it:
 * being the assistant (for the gesture and the screen), Google Home, and Do Not Disturb access.
 * Re-checked whenever Hey re comes back from Settings.
 */
@Composable
fun accessRows(): List<@Composable () -> Unit> {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val isAssistant = remember(refresh) { isDefaultAssistant(context) }
    val hasDndAccess = remember(refresh) {
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    }
    val home = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, HomeInfo.HomeEntryPoint::class.java)
            .homeControl()
    }
    val homeConnected by produceState<Boolean?>(null, refresh) {
        value = runCatching { home.isConnected() }.getOrDefault(false)
    }
    val scope = rememberCoroutineScope()
    var connecting by remember { mutableStateOf(false) }

    return listOf(
        {
            AccessRow(
                title = stringResource(R.string.pref_access_assistant),
                icon = Icons.Default.AssistantDirection,
                ok = isAssistant,
                okText = stringResource(R.string.pref_access_assistant_ok),
                fixText = stringResource(R.string.pref_access_assistant_fix),
                onClick = { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).newTask()) },
            )
        },
        {
            AccessRow(
                title = stringResource(R.string.skill_name_smart_home),
                icon = Icons.Default.Lightbulb,
                ok = homeConnected == true,
                okText = stringResource(R.string.pref_access_home_ok),
                fixText = stringResource(
                    if (connecting) R.string.pref_access_home_connecting else R.string.pref_access_home_fix
                ),
                onClick = {
                    if (homeConnected != true && !connecting) scope.launch {
                        connecting = true
                        runCatching { home.connect() }
                        connecting = false
                        refresh++
                    }
                },
            )
        },
        {
            AccessRow(
                title = stringResource(R.string.pref_access_dnd),
                icon = Icons.Default.DoNotDisturbOn,
                ok = hasDndAccess,
                okText = stringResource(R.string.pref_access_dnd_ok),
                fixText = stringResource(R.string.pref_access_dnd_fix),
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).newTask()) },
            )
        },
    )
}

@Composable
private fun AccessRow(
    title: String,
    icon: ImageVector,
    ok: Boolean,
    okText: String,
    fixText: String,
    onClick: () -> Unit,
) {
    SettingsItem(
        title = title,
        icon = icon,
        description = if (ok) okText else fixText,
        modifier = Modifier.clickable(onClick = onClick),
        content = {
            AnimatedContent(
                targetState = ok,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "accessState",
            ) { isOk ->
                Icon(
                    imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

private fun isDefaultAssistant(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
    } else {
        Settings.Secure.getString(context.contentResolver, "assistant")?.startsWith(context.packageName) == true
    }

private fun Intent.newTask() = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
