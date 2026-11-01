package org.stypox.dicio.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.stypox.dicio.R
import org.stypox.dicio.memory.Fact
import org.stypox.dicio.memory.Memory
import org.stypox.dicio.settings.ui.SettingsItem
import org.stypox.dicio.ui.home.groupedItemShape
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics

/**
 * What Hey re remembers about the person: each fact to correct with a tap or swipe away, one to
 * add by hand, and the chats not yet read or a long list to tidy, when there are any.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MemorySetting(viewModel: MainSettingsViewModel) {
    val facts by viewModel.facts.collectAsState()
    val pendingChats by viewModel.pendingChats.collectAsState()
    val work by viewModel.memoryWork.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }
    // the fact being edited, or a blank one being added
    var editing by remember { mutableStateOf<Fact?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var importing by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()

    SettingsItem(
        title = stringResource(R.string.pref_memory_title),
        icon = Icons.Outlined.Psychology,
        description = if (facts.isEmpty()) stringResource(R.string.pref_memory_empty)
        else pluralStringResource(R.plurals.pref_memory_count, facts.size, facts.size),
        modifier = Modifier.clickable {
            haptics.play(Haptic.Tap)
            open = true
        },
    )
    if (!open) return

    ModalBottomSheet(
        onDismissRequest = { open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = stringResource(R.string.pref_memory_title),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.pref_memory_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            if (pendingChats > 0) {
                FilledTonalButton(
                    onClick = {
                        haptics.play(Haptic.Tap)
                        viewModel.learnFromChats()
                    },
                    enabled = !work.isRunning,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Outlined.School, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(pluralStringResource(R.plurals.pref_memory_learn, pendingChats, pendingChats))
                }
            }
            if (facts.size >= Memory.COMPACT_OFFER_FROM) {
                OutlinedButton(
                    onClick = {
                        haptics.play(Haptic.Tap)
                        viewModel.compactMemory()
                    },
                    enabled = !work.isRunning,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(stringResource(R.string.pref_memory_compact))
                }
            }
            OutlinedButton(
                onClick = {
                    haptics.play(Haptic.Tap)
                    adding = true
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text(stringResource(R.string.pref_memory_add))
            }
            OutlinedButton(
                onClick = {
                    haptics.play(Haptic.Tap)
                    importing = true
                },
                enabled = !work.isRunning,
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text(stringResource(R.string.pref_memory_import))
            }
        }
        WorkStatus(work)

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
                .navigationBarsPadding(),
        ) {
            itemsIndexed(facts, key = { _, fact -> fact.savedAt to fact.text }) { index, fact ->
                FactRow(
                    fact = fact,
                    index = index,
                    count = facts.size,
                    onEdit = { editing = fact },
                    onForget = viewModel::forget,
                    modifier = Modifier.animateItem(),
                )
            }
            if (facts.isNotEmpty()) {
                item(key = "forgetAll") {
                    TextButton(
                        onClick = {
                            haptics.play(Haptic.ToggleOff)
                            viewModel.forgetAll()
                        },
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.pref_memory_forget_all), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    editing?.let { fact ->
        FactDialog(
            initial = fact.text,
            onSave = {
                viewModel.editFact(fact, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    if (importing) {
        ImportDialog(
            onImport = {
                viewModel.importMemory(it)
                importing = false
            },
            onDismiss = { importing = false },
        )
    }
    if (adding) {
        FactDialog(
            initial = "",
            onSave = {
                viewModel.addFact(it)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

/** A wavy line while Nano works, then a line on what it did, sliding in and out. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorkStatus(work: MemoryWork) {
    val haptics = rememberHaptics()
    LaunchedEffect(work) {
        if (work is MemoryWork.Learned || work is MemoryWork.Compacted || work is MemoryWork.Imported) {
            haptics.play(Haptic.Success)
        }
    }
    AnimatedVisibility(
        visible = work != MemoryWork.Idle,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(top = 10.dp)) {
            if (work.isRunning) LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
            Text(
                text = when (work) {
                    MemoryWork.Learning -> stringResource(R.string.pref_memory_learning)
                    MemoryWork.Compacting -> stringResource(R.string.pref_memory_compacting)
                    is MemoryWork.Learned -> if (work.facts == 0) stringResource(R.string.pref_memory_learned_nothing)
                    else pluralStringResource(R.plurals.pref_memory_learned, work.facts, work.facts)
                    is MemoryWork.Compacted -> stringResource(R.string.pref_memory_compacted, work.before, work.after)
                    is MemoryWork.Importing -> stringResource(R.string.pref_memory_importing, work.done + 1, work.total)
                    is MemoryWork.Imported -> if (work.facts == 0) stringResource(R.string.pref_memory_learned_nothing)
                    else pluralStringResource(R.plurals.pref_memory_learned, work.facts, work.facts)
                    MemoryWork.NotNow -> stringResource(R.string.pref_memory_not_now)
                    MemoryWork.Idle -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FactRow(
    fact: Fact,
    index: Int,
    count: Int,
    onEdit: () -> Unit,
    onForget: (Fact) -> Unit,
    modifier: Modifier,
) {
    val haptics = rememberHaptics()
    val shape = groupedItemShape(index, count)
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        enableDismissFromStartToEnd = false,
        onDismiss = {
            haptics.play(Haptic.ToggleOff)
            onForget(fact)
        },
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.pref_memory_forget),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        modifier = modifier,
    ) {
        Surface(
            onClick = {
                haptics.play(Haptic.Tap)
                onEdit()
            },
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = fact.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

/** A big box to paste notes about yourself, or another assistant's memory, into. */
@Composable
private fun ImportDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pref_memory_import)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.pref_memory_import_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.pref_memory_import_hint)) },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.pref_memory_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun FactDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial.isEmpty()) R.string.pref_memory_add else R.string.pref_memory_edit))
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.pref_memory_add_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.pref_memory_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
