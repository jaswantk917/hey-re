@file:OptIn(ExperimentalMaterial3Api::class)

package org.stypox.dicio.ui.about

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.stypox.dicio.settings.ui.SettingsGroup
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.stypox.dicio.BuildConfig
import org.stypox.dicio.R
import org.stypox.dicio.error.ErrorActivity
import org.stypox.dicio.settings.ui.SettingsItem
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.util.ShareUtils

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    navigationIcon: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val seen = remember { mutableSetOf<Any>() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = navigationIcon,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.padding(paddingValues)
        ) {
            item { AboutHero() }

            item {
                SettingsGroup(
                    title = stringResource(R.string.app_name),
                    seen = seen,
                    index = 1,
                    rows = listOf(
                        {
                            AboutItem(
                                title = R.string.about_repository_title,
                                icon = Icons.Default.Code,
                                description = R.string.about_repository_description,
                                link = R.string.about_repository_link
                            )
                        },
                        {
                            AboutItem(
                                title = R.string.about_issues_title,
                                icon = Icons.Default.BugReport,
                                description = R.string.about_issues_description,
                                link = R.string.about_issues_link
                            )
                        },
                        {
                            AboutItem(
                                title = R.string.about_privacy_title,
                                icon = Icons.Default.Policy,
                                description = R.string.about_privacy_description,
                                link = R.string.about_privacy_link
                            )
                        },
                    ),
                )
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.about_dicio_group),
                    seen = seen,
                    index = 2,
                    rows = listOf(
                        {
                            AboutItem(
                                title = R.string.about_wiki_title,
                                icon = Icons.Default.CollectionsBookmark,
                                description = R.string.about_wiki_description,
                                link = R.string.about_wiki_link
                            )
                        },
                        {
                            AboutItem(
                                title = R.string.about_contributing_title,
                                icon = Icons.Default.Group,
                                description = R.string.about_contributing_description,
                                link = R.string.about_contributing_link
                            )
                        },
                    ),
                )
            }
        }
    }
}

/** The app's mark on a slowly turning cookie, which pops in once and spins a beat when tapped. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutHero() {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val pop = remember { Animatable(0.6f) }
    val spin = remember { Animatable(0f) }
    val drift by rememberInfiniteTransition(label = "about cookie").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "about cookie drift",
    )
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.app_name)) {
                    haptics.play(Haptic.Spin)
                    scope.launch {
                        spin.animateTo(spin.value + 360f, spring(stiffness = Spring.StiffnessVeryLow))
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = drift + spin.value }
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
            )
            // the orb's face, as on the launcher icon
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(width = 11.dp, height = 22.dp)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer, CircleShape),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        AssistChip(
            onClick = {
                haptics.play(Haptic.Tap)
                ShareUtils.copyToClipboard(context, getVersionInfoString())
                copied = true
            },
            label = {
                AnimatedContent(
                    targetState = copied,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() },
                    label = "version chip",
                ) { isCopied ->
                    Text(
                        if (isCopied) stringResource(R.string.about_version_copied)
                        else stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                    )
                }
            },
            leadingIcon = {
                AnimatedContent(targetState = copied, label = "version chip icon") { isCopied ->
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.about_version_copy),
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                }
            },
        )
    }
}

@Composable
fun AboutItem(
    @StringRes title: Int,
    icon: ImageVector,
    @StringRes description: Int,
    @StringRes link: Int
) {
    val context = LocalContext.current
    SettingsItem(
        title = stringResource(title),
        icon = icon,
        description = stringResource(description),
        modifier = Modifier.clickable {
            ShareUtils.openUrlInBrowser(context, context.getString(link))
        },
    )
}

private fun getVersionInfoString(): String {
    return "${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (" +
            "${BuildConfig.VERSION_CODE}) running on ${ErrorActivity.getOsInfo()}"
}

@Preview
@Composable
private fun AboutScreenPreview() {
    AppTheme {
        AboutScreen(
            navigationIcon = {},
        )
    }
}
