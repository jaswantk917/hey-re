package org.stypox.dicio.skills.telephone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dicio.skill.skill.Skill
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.util.getString

class TelephoneOutput(
    private val contacts: List<Pair<String, List<String>>>,
    private val yesNoData: StandardRecognizerData<Sentences.UtilYesNo>,
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = if (contacts.isEmpty()) {
        ctx.getString(R.string.skill_telephone_unknown_contact)
    } else {
        ctx.getString(R.string.skill_telephone_found_contacts, contacts.size)
    }

    override fun getInteractionPlan(ctx: SkillContext): InteractionPlan {
        val nextSkills = mutableListOf<Skill<*>>(
            ContactChooserName(
                // when saying the name, there is no way to distinguish between
                // different numbers, so just use the first one
                contacts.map { Pair(it.first, it.second[0]) },
                yesNoData,
            )
        )

        if (ctx.parserFormatter != null) {
            nextSkills.add(
                ContactChooserIndex(
                    contacts.flatMap { contact ->
                        contact.second.map { number ->
                            Pair(contact.first, number)
                        }
                    },
                    yesNoData,
                )
            )
        }

        return InteractionPlan.StartSubInteraction(
            reopenMicrophone = true,
            nextSkills = nextSkills,
        )
    }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        if (contacts.isEmpty()) {
            Headline(text = getSpeechOutput(ctx))
        } else {
            Column {
                Headline(text = getSpeechOutput(ctx))
                for ((name, numbers) in contacts) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ContactHeader(name = name, number = numbers.first(), avatarSize = 44.dp)
                    for (number in numbers.drop(1)) {
                        Text(
                            text = number,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 68.dp, bottom = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
