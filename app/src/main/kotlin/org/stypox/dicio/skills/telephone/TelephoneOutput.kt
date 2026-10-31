package org.stypox.dicio.skills.telephone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.dicio.skill.skill.Skill
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.io.graphical.LocalAsk
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.ui.home.groupedItemShape
import org.stypox.dicio.ui.util.Haptic
import org.stypox.dicio.ui.util.rememberHaptics
import org.stypox.dicio.util.getString

class TelephoneOutput(
    private val contacts: List<Pair<String, List<String>>>,
    private val yesNoData: StandardRecognizerData<Sentences.UtilYesNo>,
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = if (contacts.isEmpty()) {
        ctx.getString(R.string.skill_telephone_unknown_contact)
    } else if (contacts.size == 1) {
        ctx.getString(R.string.skill_telephone_which_number, contacts[0].first, contacts[0].second.size)
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
            return
        }
        val ask = LocalAsk.current
        val haptics = rememberHaptics()
        val choices = contacts.sumOf { it.second.size }
        Column {
            Headline(text = getSpeechOutput(ctx))
            // numbered in the order ContactChooserIndex counts them, so a tap is the same as saying it
            var index = 0
            for ((name, numbers) in contacts) {
                Spacer(modifier = Modifier.height(8.dp))
                // the number only finds the photo; the rows below list every number
                ContactHeader(name = name, number = numbers.first(), avatarSize = 44.dp, showNumber = false)
                for (number in numbers) {
                    val choice = ++index
                    val spoken = stringResource(R.string.skill_telephone_choose_number, choice)
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        onClick = {
                            haptics.play(Haptic.Tap)
                            ask(spoken)
                        },
                        shape = groupedItemShape(choice - 1, choices),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        NumberRow(choice, number)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NumberRow(choice: Int, number: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie6Sided.toShape()),
        ) {
            Text(
                text = choice.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = number,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
