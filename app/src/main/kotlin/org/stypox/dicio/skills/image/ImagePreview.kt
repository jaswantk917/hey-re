package org.stypox.dicio.skills.image

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics

/**
 * The image across the whole card, cropped to a shape that never turns into a thin strip;
 * a tap opens it whole, to pinch into.
 */
@Composable
fun ImagePreview(image: Bitmap, description: String?, firstShow: Boolean) {
    val haptics = rememberHaptics()
    val bitmap = remember(image) { image.asImageBitmap() }
    var open by remember { mutableStateOf(false) }
    val appear = remember { Animatable(if (firstShow) 0.9f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
    Image(
        bitmap = bitmap,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            // lined up with the answer's text below, and clear of the card's header above
            .padding(start = 6.dp, end = 6.dp, top = 6.dp)
            .fillMaxWidth()
            .aspectRatio((image.width.toFloat() / image.height).coerceIn(MIN_RATIO, MAX_RATIO))
            .graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
                alpha = ((appear.value - 0.9f) * 10f).coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                haptics.play(Haptic.Tap)
                open = true
            },
    )
    if (open) {
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            val grow = remember { Animatable(0.85f) }
            LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
            var zoom by remember { mutableFloatStateOf(1f) }
            var pan by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = ((grow.value - 0.85f) / 0.15f).coerceIn(0f, 1f) }
                    .background(Color.Black)
                    .clickable { open = false },
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = description,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, panBy, zoomBy, _ ->
                                zoom = (zoom * zoomBy).coerceIn(1f, MAX_ZOOM)
                                pan = if (zoom == 1f) Offset.Zero else pan + panBy
                            }
                        }
                        .graphicsLayer {
                            scaleX = grow.value * zoom
                            scaleY = grow.value * zoom
                            translationX = pan.x
                            translationY = pan.y
                        },
                )
            }
        }
    }
}

// a portrait photo shows as a square, a panorama as no thinner than 2:1
private const val MIN_RATIO = 1f
private const val MAX_RATIO = 2f
private const val MAX_ZOOM = 4f
