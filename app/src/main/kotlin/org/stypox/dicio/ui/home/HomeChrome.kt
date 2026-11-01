package org.stypox.dicio.ui.home

import android.Manifest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.shreyaspatil.permissionflow.compose.rememberPermissionFlowRequestLauncher
import dev.shreyaspatil.permissionflow.compose.rememberPermissionState
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.util.loadingProgressString

@Composable
fun HomeTopBar(
    showTitle: Boolean,
    chatMode: Boolean,
    onChatModeChange: (Boolean) -> Unit,
    canStartNewChat: Boolean,
    onNewChat: () -> Unit,
    hasHistory: Boolean,
    onShowHistory: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(TopBarHeight)
            .padding(horizontal = 8.dp),
    ) {
        AnimatedVisibility(
            visible = showTitle,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 },
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it / 2 },
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.brand_name),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        ChatModeButton(chatMode, onChatModeChange)
        TopBarAction(visible = canStartNewChat) {
            IconButton(onClick = onNewChat, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Outlined.AddComment, contentDescription = stringResource(R.string.history_new_chat))
            }
        }
        TopBarAction(visible = hasHistory) {
            IconButton(onClick = onShowHistory, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.history_open))
            }
        }
        ProfileButton(onSettingsClick)
    }
}

/**
 * Chat mode on or off: filled while everything said goes to the model as conversation rather than
 * being taken as a command.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatModeButton(on: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    FilledIconToggleButton(
        checked = on,
        onCheckedChange = {
            haptics.play(if (it) Haptic.ToggleOn else Haptic.ToggleOff)
            onChange(it)
        },
        shapes = IconButtonDefaults.toggleableShapes(),
        colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = Color.Transparent),
    ) {
        Icon(
            if (on) Icons.Filled.Forum else Icons.Outlined.Forum,
            contentDescription = stringResource(if (on) R.string.chat_mode_off else R.string.chat_mode_on),
        )
    }
}

/** A top bar button that pops in when it has something to do and out when it doesn't. */
@Composable
private fun TopBarAction(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) { content() }
}

/**
 * The phone owner's photo (or initial), which pops in once read and opens Settings; a gear
 * instead while there's no profile to show.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileButton(onSettingsClick: () -> Unit) {
    val profile = rememberPhoneProfile()
    if (profile == null) {
        IconButton(onClick = onSettingsClick, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
        }
        return
    }
    val haptics = rememberHaptics()
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    IconButton(
        onClick = {
            haptics.play(Haptic.Tap)
            onSettingsClick()
        },
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
            alpha = pop.value.coerceIn(0f, 1f)
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(
                text = profile.firstName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (profile.photo != null) {
                AsyncImage(
                    model = profile.photo,
                    contentDescription = stringResource(R.string.settings),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

/**
 * The bottom of the home screen. While talking it is the orb (the microphone) between a keyboard
 * button and a skills button; while typing it turns into a composer pill.
 */
@Composable
fun HomeDock(
    sttState: SttState?,
    mood: OrbMood,
    typedText: String?,
    onTypedTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onSttClick: () -> Unit,
    onOrbLongClick: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onPasteImage: (Uri) -> Unit,
    onToggleTyping: () -> Unit,
    modifier: Modifier = Modifier,
    chatMode: Boolean = false,
) {
    val microphonePermission by rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val launcher = rememberPermissionFlowRequestLauncher()
    // without the microphone permission the orb asks for it, but only once there is a voice model
    // to listen with; until then the download is what matters
    val needsMicPermission = !microphonePermission.isGranted && sttState.hasVoiceModel()
    val effectiveState = if (needsMicPermission && sttState != null) SttState.NoMicrophonePermission else sttState
    val onOrbClick = if (needsMicPermission) {
        { launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
    } else {
        onSttClick
    }

    // typing is the only way in when voice input is switched off
    val typing = typedText != null || sttState == null
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = typing,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) +
                    scaleIn(motion.defaultSpatialSpec(), initialScale = 0.85f))
                .togetherWith(
                    fadeOut(motion.fastEffectsSpec()) +
                            scaleOut(motion.fastSpatialSpec(), targetScale = 0.9f)
                )
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.BottomCenter,
        label = "dock",
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) { isTyping ->
        if (isTyping) {
            Composer(
                text = typedText ?: "",
                onTextChange = onTypedTextChange,
                onSend = onSend,
                onPasteImage = onPasteImage,
                onSpeakInstead = if (sttState != null) onToggleTyping else null,
                chatMode = chatMode,
            )
        } else {
            VoiceDock(
                sttState = effectiveState,
                mood = mood,
                onOrbClick = onOrbClick,
                onOrbLongClick = onOrbLongClick,
                onTakePhoto = onTakePhoto,
                onChoosePhoto = onChoosePhoto,
                onTypeInstead = onToggleTyping,
                chatMode = chatMode,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VoiceDock(
    sttState: SttState?,
    mood: OrbMood,
    onOrbClick: () -> Unit,
    onOrbLongClick: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onTypeInstead: () -> Unit,
    chatMode: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DockStatus(sttState, chatMode)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            FilledTonalIconButton(
                onClick = onTypeInstead,
                shapes = IconButtonDefaults.shapes(
                    shape = MaterialShapes.Cookie6Sided.toShape(),
                    pressedShape = MaterialShapes.Cookie4Sided.toShape(),
                ),
                modifier = Modifier.size(SideButtonSize),
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = stringResource(R.string.type_instead))
            }
            AssistantOrb(
                mood = mood,
                onClick = onOrbClick,
                onLongClick = onOrbLongClick,
                contentDescription = stringResource(R.string.orb_description),
                size = 96.dp,
            )
            ImageButton(onTakePhoto = onTakePhoto, onChoosePhoto = onChoosePhoto)
        }
    }
}

/** Shows Hey re an image to read and ask about: a new photo or one from the gallery. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImageButton(onTakePhoto: () -> Unit, onChoosePhoto: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Box {
        FilledTonalIconButton(
            onClick = {
                haptics.play(Haptic.Tap)
                menuOpen = true
            },
            shapes = IconButtonDefaults.shapes(
                shape = MaterialShapes.Clover4Leaf.toShape(),
                pressedShape = MaterialShapes.Clover8Leaf.toShape(),
            ),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
            modifier = Modifier.size(SideButtonSize),
        ) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.image_button))
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = RoundedCornerShape(20.dp),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.image_take_photo)) },
                leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onTakePhoto()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.image_choose_photo)) },
                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onChoosePhoto()
                },
            )
        }
    }
}

/** One line above the orb that says what tapping it will do, or how the voice model is coming along. */
@Composable
private fun DockStatus(sttState: SttState?, chatMode: Boolean) {
    val tapTo = stringResource(if (chatMode) R.string.tap_to_chat else R.string.tap_to_talk)
    val context = LocalContext.current
    val text = when (sttState) {
        null -> ""
        SttState.NoMicrophonePermission -> stringResource(R.string.grant_microphone_permission)
        SttState.NotInitialized -> ""
        SttState.NotAvailable -> stringResource(R.string.stt_not_available)
        SttState.NotDownloaded -> stringResource(R.string.stt_download)
        is SttState.Downloading -> loadingProgressString(context, sttState.progress)
            .ifEmpty { stringResource(R.string.download) }
        is SttState.ErrorDownloading -> stringResource(R.string.error_downloading)
        SttState.Downloaded -> stringResource(R.string.stt_unzip)
        is SttState.Unzipping -> stringResource(R.string.unzipping)
        is SttState.ErrorUnzipping -> stringResource(R.string.error_unzipping)
        SttState.NotLoaded, is SttState.Loaded -> tapTo
        is SttState.Loading -> if (sttState.thenStartListening) stringResource(R.string.thinking) else tapTo
        is SttState.ErrorLoading -> stringResource(R.string.error_loading)
        SttState.Listening -> stringResource(R.string.listening)
        SttState.WaitingForResult -> stringResource(R.string.thinking)
        SttState.HeardNothing -> stringResource(R.string.heard_nothing)
    }
    val progress = when (sttState) {
        is SttState.Downloading -> sttState.progress
        is SttState.Unzipping -> sttState.progress
        else -> null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.heightIn(min = 22.dp),
    ) {
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) +
                        slideInVertically(motion.defaultSpatialSpec()) { it })
                    .togetherWith(
                        fadeOut(motion.fastEffectsSpec()) +
                                slideOutVertically(motion.fastSpatialSpec()) { -it }
                    )
            },
            label = "dockStatus",
        ) {
            if (it.isNotEmpty()) {
                // a pill, so it stays readable over whatever is blurring past behind the dock
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (progress != null && progress.totalBytes > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearWavyProgressIndicator(
                progress = { (progress.currentBytes / progress.totalBytes.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.width(160.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onPasteImage: (Uri) -> Unit,
    onSpeakInstead: (() -> Unit)?,
    chatMode: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // the field keeps its own state, the only kind that takes an image from the keyboard; the
    // text stays hoisted so a tapped suggestion can still fill it in
    val state = rememberTextFieldState(text)
    val currentText by rememberUpdatedState(text)
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    LaunchedEffect(text) {
        if (state.text.toString() != text) state.setTextAndPlaceCursorAtEnd(text)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { if (it != currentText) currentOnTextChange(it) }
    }

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(6.dp),
        ) {
            if (onSpeakInstead != null) {
                FilledTonalIconButton(
                    onClick = onSpeakInstead,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.speak_instead))
                }
            }
            BasicTextField(
                state = state,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                onKeyboardAction = { onSend(state.text.toString()) },
                decorator = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.text.isEmpty()) {
                            Text(
                                text = stringResource(if (chatMode) R.string.chat_mode_hint else R.string.text_input_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
                    .focusRequester(focusRequester)
                    // an image pasted or picked from the keyboard's clipboard: show it to Hey re
                    .contentReceiver { content ->
                        if (!content.hasMediaType(MediaType.Image)) return@contentReceiver content
                        content.consume { item -> item.uri?.also(onPasteImage) != null }
                    },
            )
            AnimatedVisibility(
                visible = text.isNotBlank(),
                enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                FilledIconButton(
                    onClick = { onSend(text) },
                    shapes = IconButtonDefaults.shapes(
                        shape = MaterialShapes.Cookie9Sided.toShape(),
                        pressedShape = MaterialShapes.Cookie12Sided.toShape(),
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsSheet(
    skills: List<SkillInfo>,
    onSkillClick: (example: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = stringResource(R.string.what_i_can_do),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            itemsIndexed(skills, key = { _, skill -> skill.id }) { index, skill ->
                val example = skill.sentenceExample(context)
                Surface(
                    onClick = { onSkillClick(example) },
                    shape = groupedItemShape(index, skills.size),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        SkillBadge(icon = skill.icon(), index = index, size = 44.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = skill.name(context),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "“$example”",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

/** Rows of one group touch with small inner corners and big outer ones, like Android 16 settings. */
fun groupedItemShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 28.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val badgeShapes = listOf(
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Pentagon,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Flower,
    MaterialShapes.Pill,
    MaterialShapes.SoftBurst,
)

/** A skill icon sitting on one of the expressive shapes, picked by position so neighbors differ. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SkillBadge(
    icon: Painter,
    index: Int,
    size: Dp,
    containerColor: Color = badgeColors()[index % 4].first,
    contentColor: Color = badgeColors()[index % 4].second,
) {
    Surface(
        shape = badgeShapes[index % badgeShapes.size].toShape(),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painter = icon, contentDescription = null, modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
fun badgeColors(): List<Pair<Color, Color>> {
    val c = MaterialTheme.colorScheme
    return listOf(
        c.primaryContainer to c.onPrimaryContainer,
        c.tertiaryContainer to c.onTertiaryContainer,
        c.secondaryContainer to c.onSecondaryContainer,
        c.primary to c.onPrimary,
    )
}

private fun SttState?.hasVoiceModel() = this == SttState.NotLoaded || this is SttState.Loading ||
        this is SttState.Loaded || this == SttState.Listening || this == SttState.WaitingForResult ||
        this == SttState.HeardNothing ||
        this is SttState.ErrorLoading || this == SttState.NoMicrophonePermission

private val SideButtonSize = 60.dp

@Preview
@Composable
private fun HomeDockPreview() {
    AppTheme {
        Column {
            for (state in listOf(SttState.Loaded, SttState.Listening, SttState.NotDownloaded)) {
                HomeDock(
                    sttState = state,
                    mood = orbMood(state, false, false),
                    typedText = null,
                    onTypedTextChange = {},
                    onSend = {},
                    onSttClick = {},
                    onOrbLongClick = {},
                    onTakePhoto = {},
                    onChoosePhoto = {},
                    onPasteImage = {},
                    onToggleTyping = {},
                )
            }
            HomeDock(
                sttState = SttState.Loaded,
                mood = OrbMood.Idle,
                typedText = "what's the time",
                onTypedTextChange = {},
                onSend = {},
                onSttClick = {},
                onOrbLongClick = {},
                onTakePhoto = {},
                onChoosePhoto = {},
                onPasteImage = {},
                onToggleTyping = {},
            )
        }
    }
}
