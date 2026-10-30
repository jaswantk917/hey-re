package org.stypox.dicio.settings

import android.app.Application
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import org.stypox.dicio.ui.home.AppearOnce
import org.stypox.dicio.ui.home.groupedItemShape
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.shreyaspatil.permissionflow.compose.rememberPermissionFlowRequestLauncher
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.di.SkillContextImpl
import org.stypox.dicio.eval.SkillHandler
import org.stypox.dicio.settings.datastore.UserSettingsModule.Companion.newDataStoreForPreviews
import org.stypox.dicio.skills.lyrics.LyricsInfo
import org.stypox.dicio.skills.search.SearchInfo
import org.stypox.dicio.skills.weather.WeatherInfo
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.util.SkillInfoPreviews
import org.stypox.dicio.util.ShareUtils
import org.stypox.dicio.util.getNonGrantedPermissions
import org.stypox.dicio.util.commaJoinPermissions
import org.stypox.dicio.util.requestAnyPermission

const val DICIO_NUMBERS_LINK = "https://github.com/Stypox/dicio-numbers"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SkillSettingsScreen(
    navigationIcon: @Composable () -> Unit,
    viewModel: SkillSettingsViewModel = hiltViewModel(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.pref_skills_title)) },
                subtitle = { Text(stringResource(R.string.pref_skills_screen_subtitle)) },
                navigationIcon = navigationIcon,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        }
    ) {
        SkillSettingsScreen(viewModel = viewModel, modifier = Modifier.padding(it))
    }
}

@Composable
fun SkillSettingsScreen(
    viewModel: SkillSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val skills = viewModel.skills
    val enabledSkills by viewModel.enabledSkills.collectAsState()
    val seen = remember { mutableSetOf<Any>() }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        if (viewModel.numberLibraryNotAvailable) {
            item {
                val context = LocalContext.current
                Card(
                    modifier = Modifier.padding(bottom = 12.dp),
                    onClick = { ShareUtils.openUrlInBrowser(context, DICIO_NUMBERS_LINK) },
                ) {
                    Text(
                        text = stringResource(R.string.pref_skill_number_library_not_available),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }
        }
        itemsIndexed(skills, key = { _, skill -> skill.id }) { index, skill ->
            // Note: calling build() here is slightly wasteful as it constructs a skill object
            // just to check availability, but it ensures correct results regardless of whether
            // the skill is enabled or disabled by the user.
            AppearOnce(key = skill.id, seen = seen, delayMillis = minOf(index, 8) * 30) {
                SkillSettingsItem(
                    skill = skill,
                    isAvailable = skill.build(viewModel.skillContext) != null,
                    enabled = enabledSkills.getOrDefault(skill.id, true),
                    setEnabled = { enabled -> viewModel.setSkillEnabled(skill.id, enabled) },
                    shape = groupedItemShape(index, skills.size),
                )
            }
        }
    }
}

@Composable
fun SkillSettingsItem(
    skill: SkillInfo,
    isAvailable: Boolean,
    enabled: Boolean,
    setEnabled: (Boolean) -> Unit,
    shape: Shape = MaterialTheme.shapes.large,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SkillSettingsItem(
        skill = skill,
        isAvailable = isAvailable,
        enabled = enabled,
        setEnabled = setEnabled,
        expanded = expanded,
        toggleExpanded = { expanded = !expanded },
        shape = shape,
    )
}

@Composable
fun SkillSettingsItem(
    skill: SkillInfo,
    isAvailable: Boolean,
    enabled: Boolean,
    setEnabled: (Boolean) -> Unit,
    expanded: Boolean,
    toggleExpanded: () -> Unit,
    shape: Shape = MaterialTheme.shapes.large,
) {
    val canExpand = isAvailable && (
        skill.renderSettings != null || skill.neededPermissions.isNotEmpty()
    )

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column {
            SkillSettingsItemHeader(
                expanded = expanded,
                toggleExpanded = if (canExpand) toggleExpanded else null,
                skill = skill,
                enabled = enabled && isAvailable,
                setEnabled = setEnabled,
                isAvailable = isAvailable,
            )

            if (!isAvailable) {
                Text(
                    text = stringResource(R.string.pref_skill_not_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 72.dp, end = 16.dp, bottom = 14.dp),
                )

            } else if (expanded) {
                if (skill.neededPermissions.isNotEmpty()) {
                    SkillSettingsItemPermissionLine(
                        skill = skill,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 72.dp, end = 16.dp, bottom = 14.dp),
                    )
                }

                skill.renderSettings?.invoke()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SkillSettingsItemHeader(
    expanded: Boolean,
    toggleExpanded: (() -> Unit)?,
    skill: SkillInfo,
    enabled: Boolean,
    setEnabled: (Boolean) -> Unit,
    isAvailable: Boolean,
) {
    val haptics = rememberHaptics()
    val turn by animateFloatAsState(
        label = "skill ${skill.id} card expanded",
        targetValue = if (expanded) 180f else 0f
    )
    val iconContainer by animateColorAsState(
        if (enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "skill ${skill.id} icon",
    )
    val toggle = { wanted: Boolean ->
        haptics.play(if (wanted) Haptic.ToggleOn else Haptic.ToggleOff)
        setEnabled(wanted)
    }
    val context = LocalContext.current
    val example = skill.sentenceExample(context).takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier
            .let { it ->
                if (toggleExpanded != null)
                    it.clickable(onClick = toggleExpanded)
                else
                    // still group items together for better accessibility
                    it.semantics(mergeDescendants = true) {}
            }
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(iconContainer, MaterialShapes.Cookie6Sided.toShape()),
        ) {
            Icon(
                painter = skill.icon(),
                // no content description here as it would be duplicate with the text below
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier
                .weight(1.0f)
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = skill.name(context),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isAvailable) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (toggleExpanded != null) {
                    Icon(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(20.dp)
                            .rotate(turn)
                            .testTag("expand_skill_settings_handle"),
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.reduce else R.string.expand
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (example != null) {
                Text(
                    text = "\u201C$example\u201D",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = enabled,
            onCheckedChange = toggle,
            enabled = isAvailable,
            thumbContent = {
                AnimatedVisibility(visible = enabled, enter = scaleIn(), exit = scaleOut()) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            },
        )
    }
}

@Preview
@Composable
fun SkillSettingsItemPermissionLine(
    @PreviewParameter(SkillInfoPreviews::class) skill: SkillInfo,
    modifier: Modifier = Modifier
) {
    val nonGrantedPermissions = if (LocalInspectionMode.current)
        listOf() // can't use PermissionFlow in previews
    else
        getNonGrantedPermissions(skill.neededPermissions)

    val needingPermissionsString = stringResource(
        R.string.pref_skill_missing_permissions,
        commaJoinPermissions(LocalContext.current, skill.neededPermissions)
    )

    if (nonGrantedPermissions.isEmpty()) {
        Text(
            text = needingPermissionsString,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )

    } else {
        val launcher = rememberPermissionFlowRequestLauncher()
        val context = LocalContext.current

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier,
        ) {
            Text(
                text = needingPermissionsString,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
                    .padding(end = 8.dp),
            )

            FilledTonalButton(
                onClick = { requestAnyPermission(launcher, context, nonGrantedPermissions) },
            ) {
                Text(text = stringResource(R.string.pref_skill_grant_permissions))
            }
        }
    }
}

@Preview
@Composable
private fun SkillSettingsItemPreview(@PreviewParameter(SkillInfoPreviews::class) skill: SkillInfo) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    SkillSettingsItem(
        skill = skill,
        isAvailable = true,
        enabled = false,
        setEnabled = {},
        expanded = expanded,
        toggleExpanded = { expanded = !expanded },
    )
}

// this preview is useful to take screenshots
@Preview
@Composable
private fun ThreeSkillSettingsItemsPreview() {
    AppTheme {
        LazyColumn(
            contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
        ) {
            item {
                SkillSettingsItem(
                    skill = WeatherInfo,
                    isAvailable = true,
                    enabled = true,
                    setEnabled = {},
                    expanded = true,
                    toggleExpanded = {},
                )
            }
            item {
                SkillSettingsItem(
                    skill = SearchInfo,
                    isAvailable = true,
                    enabled = false,
                    setEnabled = {},
                    expanded = false,
                    toggleExpanded = {},
                )
            }
            item {
                SkillSettingsItem(
                    skill = LyricsInfo,
                    isAvailable = false,
                    enabled = true,
                    setEnabled = {},
                    expanded = false,
                    toggleExpanded = {},
                )
            }
        }
    }
}

@Preview
@Composable
private fun SkillSettingsScreenPreview() {
    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            SkillSettingsScreen(
                SkillSettingsViewModel(
                    application = Application(),
                    dataStore = newDataStoreForPreviews(),
                    skillContext = SkillContextImpl.newForPreviews(LocalContext.current),
                    skillHandler = SkillHandler.newForPreviews(LocalContext.current),
                )
            )
        }
    }
}

@Preview
@Composable
private fun SkillSettingsScreenWithTopBarPreview() {
    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            SkillSettingsScreen(
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                SkillSettingsViewModel(
                    application = Application(),
                    dataStore = newDataStoreForPreviews(),
                    skillContext = SkillContextImpl.newForPreviews(LocalContext.current),
                    skillHandler = SkillHandler.newForPreviews(LocalContext.current),
                )
            )
        }
    }
}

