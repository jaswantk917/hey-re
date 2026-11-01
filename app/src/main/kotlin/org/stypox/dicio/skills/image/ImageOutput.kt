package org.stypox.dicio.skills.image

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.AnswerText
import org.stypox.dicio.io.graphical.LocalAsk
import org.stypox.dicio.io.graphical.Subtitle
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberFirstShow
import org.stypox.dicio.ui.util.rememberHaptics
import org.stypox.dicio.util.getString

/** The image, what Nano makes of it, and the text read from it, to copy or ask more about. */
class ImageOutput(
    val image: Bitmap,
    private val description: String?,
    private val text: String?,
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = description
        ?: if (text != null) ctx.getString(R.string.skill_image_read_text) else ctx.getString(R.string.skill_image_nothing)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        val context = LocalContext.current
        val ask = LocalAsk.current
        val haptics = rememberHaptics()
        Column {
            ImagePreview(image, description, rememberFirstShow(this@ImageOutput))
            Spacer(modifier = Modifier.height(12.dp))
            AnswerText(text = getSpeechOutput(ctx))
            if (text != null) {
                var expanded by rememberSaveable { mutableStateOf(false) }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(14.dp)
                            .animateContentSize(),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            haptics.play(Haptic.Tap)
                            context.getSystemService<ClipboardManager>()
                                ?.setPrimaryClip(ClipData.newPlainText(null, text))
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.skill_image_copy_text))
                    }
                    val translate = stringResource(R.string.skill_image_translate_command)
                    OutlinedButton(onClick = { ask(translate) }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.skill_image_translate))
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Subtitle(text = stringResource(R.string.skill_image_ask_hint))
        }
    }
}

private const val COLLAPSED_LINES = 6
