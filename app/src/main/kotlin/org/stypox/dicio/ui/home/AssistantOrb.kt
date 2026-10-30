package org.stypox.dicio.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.stypox.dicio.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** What the orb, the little character that is also the microphone button, is feeling right now. */
enum class OrbMood {
    /** ready, blinking now and then */
    Idle,
    /** the microphone is open */
    Listening,
    /** working out an answer */
    Thinking,
    /** an answer just arrived */
    Happy,
    /** something failed */
    Sad,
    /** the voice model is not ready yet */
    Sleepy,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun moodShape(mood: OrbMood): RoundedPolygon = when (mood) {
    OrbMood.Idle -> MaterialShapes.Cookie9Sided
    OrbMood.Listening -> MaterialShapes.SoftBurst
    OrbMood.Thinking -> MaterialShapes.Clover8Leaf
    OrbMood.Happy -> MaterialShapes.Sunny
    OrbMood.Sad -> MaterialShapes.Ghostish
    OrbMood.Sleepy -> MaterialShapes.Puffy
}

// long-pressing the orb cycles through these; the heart only shows up once in a while
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val playShapes = listOf(
    MaterialShapes.Flower,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.SoftBoom,
    MaterialShapes.Pentagon,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Gem,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val rareShape = MaterialShapes.Heart

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val restingShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Puffy,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Flower,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Clover4Leaf,
)

private const val IDLE_MORPH_MILLIS = 3200L

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AssistantOrb(
    mood: OrbMood,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        targetValue = when (mood) {
            OrbMood.Listening -> colors.tertiary
            OrbMood.Sad -> colors.error
            OrbMood.Sleepy -> colors.primaryContainer
            else -> colors.primary
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "orbFill",
    )
    val face by animateColorAsState(
        targetValue = when (mood) {
            OrbMood.Listening -> colors.onTertiary
            OrbMood.Sad -> colors.onError
            OrbMood.Sleepy -> colors.onPrimaryContainer
            else -> colors.onPrimary
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "orbFace",
    )

    // the shape morphs from whatever it was showing to the new target with a bouncy spring
    var fromShape by remember { mutableStateOf(moodShape(mood)) }
    var toShape by remember { mutableStateOf(moodShape(mood)) }
    val morphProgress = remember { Animatable(1f) }
    var playIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun morphTo(target: RoundedPolygon) {
        if (target === toShape) return
        fromShape = toShape
        toShape = target
        scope.launch {
            morphProgress.snapTo(0f)
            morphProgress.animateTo(
                1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }
    LaunchedEffect(mood) {
        morphTo(moodShape(mood))
        // at rest the orb keeps drifting between calm shapes, so it always looks alive
        if (mood == OrbMood.Idle || mood == OrbMood.Sleepy) {
            var i = 0
            while (true) {
                delay(IDLE_MORPH_MILLIS)
                i += 1
                morphTo(restingShapes[i % restingShapes.size])
            }
        }
    }

    // a quick squish under the finger, released with a spring
    val squish = remember { Animatable(1f) }
    val spin = remember { Animatable(0f) }

    val infinite = rememberInfiniteTransition(label = "orb")
    val breath by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "breath",
    )
    val drift by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (mood == OrbMood.Thinking) 2200 else 24000, easing = LinearEasing)),
        label = "drift",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "pulse",
    )
    val glance by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "glance",
    )

    // blink at random intervals, never while listening (it is all ears then)
    val blink = remember { Animatable(1f) }
    LaunchedEffect(mood) {
        while (mood == OrbMood.Idle || mood == OrbMood.Thinking) {
            delay(Random.nextLong(2200, 5200))
            blink.animateTo(0.08f, tween(70))
            blink.animateTo(1f, tween(110))
            if (Random.nextInt(4) == 0) {
                delay(140)
                blink.animateTo(0.08f, tween(70))
                blink.animateTo(1f, tween(110))
            }
        }
        blink.snapTo(1f)
    }

    val eyeOpen by animateFloatAsState(
        targetValue = when (mood) {
            OrbMood.Listening -> 1.25f
            OrbMood.Sleepy -> 0f
            else -> 1f
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "eyeOpen",
    )

    val morph = remember(fromShape, toShape) { Morph(fromShape, toShape) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                onClick { onClick(); true }
                onLongClick { onLongClick(); true }
            }
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    onPress = {
                        scope.launch {
                            squish.animateTo(0.86f, spring(stiffness = Spring.StiffnessHigh))
                        }
                        tryAwaitRelease()
                        scope.launch {
                            squish.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                        }
                    },
                    onTap = { onClick() },
                    onLongPress = {
                        playIndex += 1
                        morphTo(if (playIndex % 13 == 0) rareShape else playShapes[playIndex % playShapes.size])
                        scope.launch {
                            spin.animateTo(spin.value + 240f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
                        }
                        onLongClick()
                    },
                )
            }
    ) {
        if (mood == OrbMood.Listening) {
            // two halos ripple out while the microphone is open
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (offset in listOf(0f, 0.5f)) {
                    val t = (pulse + offset) % 1f
                    drawCircle(
                        color = fill.copy(alpha = 0.35f * (1f - t)),
                        radius = this.size.minDimension / 2f * (0.85f + 0.55f * t),
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val breathScale = if (mood == OrbMood.Listening) {
                        1f + 0.06f * sin(pulse * 2f * PI.toFloat())
                    } else {
                        1f + 0.03f * breath
                    }
                    scaleX = squish.value * breathScale
                    scaleY = squish.value * breathScale
                }
        ) {
            val side = this.size.minDimension
            path.rewind()
            morph.toPath(morphProgress.value, path)
            matrix.reset()
            matrix.scale(side, side)
            path.transform(matrix)
            rotate(degrees = drift + spin.value, pivot = Offset(side / 2f, side / 2f)) {
                drawPath(path, fill)
            }
            drawFace(mood, face, side, eyeOpen * blink.value, glance)
        }
    }
}

private fun DrawScope.drawFace(mood: OrbMood, color: Color, side: Float, openness: Float, glance: Float) {
    val eyeGap = side * 0.15f
    val eyeWidth = side * 0.085f
    val eyeHeight = side * 0.17f
    // while thinking, the orb looks around; otherwise it looks straight at you
    val lookX = if (mood == OrbMood.Thinking) sin(glance * 2f * PI.toFloat()) * side * 0.05f else 0f
    val lookY = if (mood == OrbMood.Thinking) -side * 0.04f else 0f
    val center = Offset(side / 2f + lookX, side / 2f + lookY - side * 0.02f)

    for (direction in listOf(-1f, 1f)) {
        val eyeCenter = Offset(center.x + direction * eyeGap, center.y)
        when {
            mood == OrbMood.Happy -> {
                // ^ ^ eyes
                val arc = Path().apply {
                    moveTo(eyeCenter.x - eyeWidth, eyeCenter.y + eyeHeight * 0.15f)
                    quadraticTo(eyeCenter.x, eyeCenter.y - eyeHeight * 0.55f,
                        eyeCenter.x + eyeWidth, eyeCenter.y + eyeHeight * 0.15f)
                }
                drawPath(arc, color, style = Stroke(width = eyeWidth * 0.75f, cap = StrokeCap.Round))
            }
            mood == OrbMood.Sad -> {
                // eyes that droop towards the outside
                val tilt = direction * eyeHeight * 0.18f
                drawLine(
                    color,
                    start = Offset(eyeCenter.x - eyeWidth, eyeCenter.y - tilt),
                    end = Offset(eyeCenter.x + eyeWidth, eyeCenter.y + tilt),
                    strokeWidth = eyeWidth * 0.75f,
                    cap = StrokeCap.Round,
                )
            }
            openness < 0.15f -> {
                drawLine(
                    color,
                    start = Offset(eyeCenter.x - eyeWidth * 0.8f, eyeCenter.y + eyeHeight * 0.2f),
                    end = Offset(eyeCenter.x + eyeWidth * 0.8f, eyeCenter.y + eyeHeight * 0.2f),
                    strokeWidth = eyeWidth * 0.7f,
                    cap = StrokeCap.Round,
                )
            }
            else -> {
                val h = eyeHeight * openness
                drawRoundRect(
                    color,
                    topLeft = Offset(eyeCenter.x - eyeWidth / 2f, eyeCenter.y - h / 2f),
                    size = Size(eyeWidth, h),
                    cornerRadius = CornerRadius(eyeWidth / 2f),
                )
            }
        }
    }
}

@Preview
@Composable
private fun AssistantOrbPreview() {
    AppTheme {
        androidx.compose.foundation.layout.Row {
            for (mood in OrbMood.entries) {
                AssistantOrb(mood = mood, onClick = {}, onLongClick = {}, contentDescription = "", size = 72.dp)
            }
        }
    }
}
