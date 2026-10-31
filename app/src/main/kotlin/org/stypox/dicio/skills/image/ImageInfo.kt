package org.stypox.dicio.skills.image

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R

/** Labels answers about an image the person showed; it's never in the list of selectable skills. */
object ImageInfo : SkillInfo("image") {
    override fun name(context: Context) = context.getString(R.string.skill_name_image)

    override fun sentenceExample(context: Context) = ""

    @Composable
    override fun icon() = rememberVectorPainter(Icons.Default.Image)

    override fun build(ctx: SkillContext): Skill<*>? = null
}
