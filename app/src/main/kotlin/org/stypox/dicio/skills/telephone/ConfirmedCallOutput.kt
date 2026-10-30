package org.stypox.dicio.skills.telephone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.util.getString

/** The answer to "should I call?": [name] and [number] are null when the user said no. */
class ConfirmedCallOutput(
    private val name: String?,
    private val number: String?,
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = if (number == null) {
        ctx.getString(R.string.skill_telephone_not_calling)
    } else {
        "" // do not speak anything since a call has just started
    }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        if (name == null || number == null) {
            Headline(text = stringResource(R.string.skill_telephone_not_calling))
        } else {
            Column {
                Headline(text = stringResource(R.string.skill_telephone_calling, name))
                Spacer(modifier = Modifier.height(8.dp))
                ContactHeader(name = name, number = number, calling = true)
            }
        }
    }
}
