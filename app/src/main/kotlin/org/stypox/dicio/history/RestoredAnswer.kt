package org.stypox.dicio.history

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.io.graphical.AnswerText
import org.stypox.dicio.skills.image.ImagePreview
import org.stypox.dicio.ui.util.rememberFirstShow

/** An answer from an earlier session: its words and its image, the live card being long gone. */
class RestoredAnswer(val turn: SavedTurn, private val image: Bitmap?) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext) = turn.answer

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        Column {
            if (image != null) {
                ImagePreview(image, turn.answer, rememberFirstShow(this@RestoredAnswer))
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (turn.answer.isNotBlank()) AnswerText(text = turn.answer)
        }
    }
}
