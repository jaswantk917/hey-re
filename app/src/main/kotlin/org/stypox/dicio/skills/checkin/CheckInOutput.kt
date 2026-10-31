package org.stypox.dicio.skills.checkin

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Resources
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.ui.home.groupedItemShape
import org.stypox.dicio.ui.util.rememberFirstShow
import java.text.NumberFormat
import java.time.LocalDateTime

class CheckInOutput(
    private val name: String?,
    private val hinglish: Boolean,
    // "kitne steps hue": the steps alone, without the greeting
    private val stepsOnly: Boolean,
    private val initial: CheckIn,
) : SkillOutput {

    override fun getSpeechOutput(ctx: SkillContext): String {
        if (stepsOnly) return stepsSpeech(ctx.android.resources, initial.steps)
        val res = ctx.android.resources
        val parts = mutableListOf(greeting(ctx))
        (initial.steps as? Section.Ready)?.value?.let { parts += stepsSpeech(res, Section.Ready(it)) }
        (initial.tasks as? Section.Ready)?.value?.let { tasks ->
            if (tasks.due.isNotEmpty()) {
                parts += res.getQuantityString(
                    if (hinglish) R.plurals.skill_check_in_tasks_due_hi else R.plurals.skill_check_in_tasks_due,
                    tasks.due.size, tasks.due.size, tasks.due.take(2).joinToString(", "),
                )
            }
        }
        (initial.notifications as? Section.Ready)?.value?.takeIf { it.isNotEmpty() }?.let { apps ->
            val total = apps.sumOf { it.count }
            parts += res.getQuantityString(
                if (hinglish) R.plurals.skill_check_in_notifications_hi else R.plurals.skill_check_in_notifications,
                total, total, apps.first().app,
            )
        }
        return parts.joinToString(" ")
    }

    private fun stepsSpeech(res: Resources, steps: Section<Long>): String = when (steps) {
        is Section.Ready -> {
            val count = NumberFormat.getInstance().format(steps.value)
            listOfNotNull(
                res.getString(if (hinglish) R.string.skill_check_in_steps_hi else R.string.skill_check_in_steps, count),
                Routine.nudge(steps.value, LocalDateTime.now())?.let { res.getString(it.spoken(hinglish)) },
            ).joinToString(" ")
        }
        Section.NeedsAccess -> res.getString(
            if (hinglish) R.string.skill_check_in_steps_need_access_hi else R.string.skill_check_in_steps_need_access
        )
        Section.Unavailable -> res.getString(
            if (hinglish) R.string.skill_check_in_steps_unavailable_hi else R.string.skill_check_in_steps_unavailable
        )
    }

    private fun greeting(ctx: SkillContext): String {
        val res = ctx.android.resources
        return when {
            name == null && hinglish -> res.getString(R.string.skill_check_in_hello_hi)
            name == null -> res.getString(R.string.skill_check_in_hello)
            hinglish -> res.getString(R.string.skill_check_in_hello_named_hi, name)
            else -> res.getString(R.string.skill_check_in_hello_named, name)
        }
    }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val firstShow = rememberFirstShow(this)
        var checkIn by remember { mutableStateOf(initial) }
        val refresh: () -> Unit = {
            scope.launch { checkIn = if (stepsOnly) CheckIn.gatherSteps(context) else CheckIn.gather(context) }
        }

        // notification access is granted in Settings, so look again on the way back
        LifecycleResumeEffect(Unit) {
            if (checkIn.notifications == Section.NeedsAccess) refresh()
            onPauseOrDispose {}
        }
        val healthLauncher = rememberLauncherForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { refresh() }
        val tasksLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { refresh() }

        val rows = buildList<@Composable () -> Unit> {
            when (val steps = checkIn.steps) {
                is Section.Ready -> add { StepsRow(steps.value, firstShow) }
                Section.NeedsAccess -> add {
                    ConnectRow(Icons.AutoMirrored.Filled.DirectionsWalk, R.string.skill_check_in_steps_connect) {
                        healthLauncher.launch(HealthSteps.permissionsToRequest(context))
                    }
                }
                Section.Unavailable -> {}
            }
            when (val tasks = checkIn.tasks) {
                is Section.Ready -> add { TasksRow(tasks.value) }
                Section.NeedsAccess -> add {
                    ConnectRow(Icons.Default.TaskAlt, R.string.skill_check_in_tasks_connect) {
                        scope.launch {
                            GoogleTasks.consent(context)?.let {
                                tasksLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
                            } ?: refresh()
                        }
                    }
                }
                Section.Unavailable -> {}
            }
            when (val notifications = checkIn.notifications) {
                is Section.Ready -> add { NotificationsRow(notifications.value) }
                Section.NeedsAccess -> add {
                    ConnectRow(Icons.Default.Notifications, R.string.skill_check_in_notifications_connect) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (e: ActivityNotFoundException) {
                            Log.w("CheckInOutput", "No notification access settings", e)
                        }
                    }
                }
                Section.Unavailable -> {}
            }
        }

        Column {
            Headline(text = if (stepsOnly) stepsSpeech(context.resources, checkIn.steps) else greeting(ctx))
            if (rows.isNotEmpty()) Spacer(modifier = Modifier.height(12.dp))
            rows.forEachIndexed { index, row ->
                if (index > 0) Spacer(modifier = Modifier.height(2.dp))
                Arrive(index, firstShow) {
                    Surface(
                        shape = groupedItemShape(index, rows.size),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { row() }
                    }
                }
            }
        }
    }
}

/** Rows rise in one after the other, so the check-in reads top to bottom. */
@Composable
private fun Arrive(index: Int, firstShow: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (firstShow) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!firstShow) return@LaunchedEffect
        delay(120L + index * 90L)
        progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 24.dp.toPx()
        }
    ) { content() }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepsRow(steps: Long, firstShow: Boolean) {
    val walk = remember(steps) { Routine.nudge(steps, LocalDateTime.now()) }
    var shown by remember { mutableIntStateOf(if (firstShow) 0 else steps.toInt()) }
    LaunchedEffect(steps) { delay(200); shown = steps.toInt() }
    val counted by animateIntAsState(shown, tween(900), label = "steps count")
    val ring by animateFloatAsState(
        (shown.toFloat() / STEP_GOAL).coerceIn(0f, 1f), tween(900), label = "steps ring",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
            CircularWavyProgressIndicator(progress = { ring }, modifier = Modifier.size(52.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = pluralStringResource(
                    R.plurals.skill_check_in_steps_count, counted, NumberFormat.getInstance().format(counted)
                ),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Text(
                text = if (walk != null) stringResource(walk.shown)
                else stringResource(R.string.skill_check_in_steps_goal, NumberFormat.getInstance().format(STEP_GOAL)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (walk != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TasksRow(tasks: TaskDigest) {
    Row(verticalAlignment = Alignment.Top) {
        RowIcon(Icons.Default.TaskAlt)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = when {
                    tasks.due.isNotEmpty() -> pluralStringResource(
                        R.plurals.skill_check_in_tasks_due_title, tasks.due.size, tasks.due.size
                    )
                    tasks.open > 0 -> pluralStringResource(
                        R.plurals.skill_check_in_tasks_open, tasks.open, tasks.open
                    )
                    else -> stringResource(R.string.skill_check_in_tasks_none)
                },
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            tasks.due.take(4).forEach { title ->
                Text(
                    text = "• $title",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationsRow(apps: List<AppCount>) {
    val total = apps.sumOf { it.count }
    Row(verticalAlignment = Alignment.Top) {
        RowIcon(Icons.Default.Notifications)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = if (total == 0) stringResource(R.string.skill_check_in_notifications_none)
                else pluralStringResource(R.plurals.skill_check_in_notifications_title, total, total),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            if (apps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    apps.take(6).forEach { app ->
                        Text(
                            text = "${app.app} ${app.count}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectRow(icon: ImageVector, label: Int, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Spacer(modifier = Modifier.width(16.dp))
        FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(label))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowIcon(icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialShapes.Cookie6Sided.toShape()),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun WalkNudge.spoken(hinglish: Boolean) = when (this) {
    WalkNudge.AfterLunch -> if (hinglish) R.string.skill_check_in_walk_after_lunch_hi else R.string.skill_check_in_walk_after_lunch
    WalkNudge.Now -> if (hinglish) R.string.skill_check_in_walk_now_hi else R.string.skill_check_in_walk_now
    WalkNudge.AtTea -> if (hinglish) R.string.skill_check_in_walk_at_tea_hi else R.string.skill_check_in_walk_at_tea
    WalkNudge.OnTheWayHome -> if (hinglish) R.string.skill_check_in_walk_home_hi else R.string.skill_check_in_walk_home
    WalkNudge.Anytime -> if (hinglish) R.string.skill_check_in_walk_anytime_hi else R.string.skill_check_in_walk_anytime
}

private val WalkNudge.shown
    get() = when (this) {
        WalkNudge.AfterLunch -> R.string.skill_check_in_walk_after_lunch
        WalkNudge.Now -> R.string.skill_check_in_walk_now
        WalkNudge.AtTea -> R.string.skill_check_in_walk_at_tea
        WalkNudge.OnTheWayHome -> R.string.skill_check_in_walk_home
        WalkNudge.Anytime -> R.string.skill_check_in_walk_anytime
    }
