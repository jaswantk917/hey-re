package org.stypox.dicio.skills.open

import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.BuildConfig
import org.stypox.dicio.R
import org.stypox.dicio.di.SkillContextImpl
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.ui.home.SkillAnswerCard
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.util.rememberFirstShow
import org.stypox.dicio.util.getString

private val TAG = OpenOutput::class.simpleName

class OpenOutput(
    private val appName: String?,
    private val packageName: String?,
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = if (appName == null) {
        ctx.getString(R.string.skill_open_could_not_understand)
    } else if (packageName == null) {
        ctx.getString(R.string.skill_open_unknown_app, appName)
    } else {
        ctx.getString(R.string.skill_open_opening, appName)
    }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        if (appName == null || packageName == null) {
            Headline(text = getSpeechOutput(ctx))

        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val context = LocalContext.current
                val icon = remember {
                    try {
                        context.packageManager.getApplicationIcon(packageName)
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.e(TAG, "Could not load icon for $packageName", e)
                        null
                    }
                }

                if (icon != null) {
                    val firstShow = rememberFirstShow(this@OpenOutput)
                    val pop = remember { Animatable(if (firstShow) 0.6f else 1f) }
                    LaunchedEffect(Unit) {
                        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                    }
                    Image(
                        painter = rememberDrawablePainter(icon),
                        contentDescription = appName,
                        modifier = Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                scaleX = pop.value
                                scaleY = pop.value
                            },
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Headline(text = getSpeechOutput(ctx))
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun OpenOutputPreview() {
    AppTheme {
        SkillAnswerCard {
            OpenOutput(appName = "Some app name", packageName = BuildConfig.APPLICATION_ID)
                .GraphicalOutput(SkillContextImpl.newForPreviews(LocalContext.current))
        }
    }
}
