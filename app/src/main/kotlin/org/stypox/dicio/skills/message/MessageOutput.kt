package org.stypox.dicio.skills.message

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Countdown
import org.stypox.dicio.io.graphical.CountdownBar
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.skills.telephone.ContactHeader
import org.stypox.dicio.util.RecognizeEverythingSkill
import org.stypox.dicio.util.RecognizeYesNoSkill
import org.stypox.dicio.util.getString

/**
 * "Sending to Maa in 4" over the contact and the message as a chat bubble; once the countdown ends
 * the chat opens with the text typed in. A loose contact match waits for a tap instead.
 */
class SendMessageOutput(
    private val recipient: Recipient,
    private val text: String,
    private val yesNoData: StandardRecognizerData<Sentences.UtilYesNo>,
) : SkillOutput {
    private val countdown = Countdown(auto = recipient.confident)
    private val opened = mutableStateOf(true)

    override fun getSpeechOutput(ctx: SkillContext): String = when {
        countdown.state == Countdown.State.Waiting ->
            ctx.getString(R.string.skill_message_confirm, recipient.name)
        recipient.app == MessageApp.Slack -> ""
        else -> ctx.getString(R.string.skill_message_sending, recipient.name, ctx.getString(recipient.app.label))
    }

    override fun getInteractionPlan(ctx: SkillContext): InteractionPlan {
        countdown.start(ctx.android) { opened.value = Messenger.open(ctx.android, recipient, text) }
        if (recipient.app == MessageApp.Slack) {
            // nothing to count down to: the person is still picked in Slack
            countdown.confirm(ctx.android)
            return InteractionPlan.FinishInteraction
        }

        // listen meanwhile, so "nahi" stops it and "haan" skips the wait
        val confirmYesNoSkill = object : RecognizeYesNoSkill(MessageInfo, yesNoData) {
            override suspend fun generateOutput(ctx: SkillContext, inputData: Boolean): SkillOutput {
                return if (inputData) {
                    countdown.confirm(ctx.android)
                    MessageOutput.Opened(recipient.app)
                } else {
                    countdown.cancel(ctx.android)
                    MessageOutput.NotSending
                }
            }
        }
        return InteractionPlan.ReplaceSubInteraction(
            reopenMicrophone = true,
            nextSkills = listOf(confirmYesNoSkill),
        )
    }

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        val state = countdown.state
        Column {
            when (state) {
                Countdown.State.Done -> Headline(
                    text = if (opened.value) stringResource(recipient.app.ready, recipient.name)
                    else stringResource(R.string.skill_message_no_app, stringResource(recipient.app.label))
                )
                Countdown.State.Cancelled -> Headline(text = stringResource(R.string.skill_message_not_sending))
                else -> {}
            }
            CountdownBar(
                countdown = countdown,
                confirmLabel = stringResource(R.string.skill_message_send_button),
            ) { secondsLeft ->
                Headline(
                    text = if (state == Countdown.State.Waiting)
                        stringResource(R.string.skill_message_confirm, recipient.name)
                    else stringResource(R.string.skill_message_sending_in, recipient.name, secondsLeft)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ContactHeader(name = recipient.name, number = recipient.address)
                Spacer(modifier = Modifier.height(4.dp))
                MessageBubble(text = text, app = recipient.app)
            }
            if (state == Countdown.State.Done) {
                Spacer(modifier = Modifier.height(8.dp))
                MessageBubble(text = text, app = recipient.app)
            }
        }
    }
}

sealed interface MessageOutput : SkillOutput {
    class NoContact(private val name: String) : MessageOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_message_no_contact, name)
    }

    data object NotSending : MessageOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_message_not_sending)
    }

    class Opened(private val app: MessageApp) : MessageOutput {
        // nothing to say, the chat has just opened
        override fun getSpeechOutput(ctx: SkillContext): String = ""

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Headline(text = stringResource(R.string.skill_message_opening, stringResource(app.label)))
        }
    }

    /** "What should I say to Maa?": whatever comes next is the message. */
    class AskText(
        private val recipient: Recipient,
        private val yesNoData: StandardRecognizerData<Sentences.UtilYesNo>,
    ) : MessageOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_message_what_to_say, recipient.name)

        override fun getInteractionPlan(ctx: SkillContext): InteractionPlan {
            val textSkill = object : RecognizeEverythingSkill(MessageInfo) {
                override suspend fun generateOutput(ctx: SkillContext, inputData: String): SkillOutput =
                    if (inputData.trim().lowercase() in GIVE_UP) NotSending
                    else SendMessageOutput(recipient, inputData.trim(), yesNoData)
            }
            return InteractionPlan.StartSubInteraction(
                reopenMicrophone = true,
                nextSkills = listOf(textSkill),
            )
        }

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Column {
                Headline(text = getSpeechOutput(ctx))
                Spacer(modifier = Modifier.height(8.dp))
                if (recipient.app != MessageApp.Slack) {
                    ContactHeader(name = recipient.name, number = recipient.address)
                }
            }
        }

        companion object {
            private val GIVE_UP = setOf(
                "cancel", "nothing", "never mind", "nevermind", "nahi", "kuch nahi", "rehne do",
                "chhodo", "chodo", "mat bhejo",
            )
        }
    }
}

/** Pops in from its tail, like a message just sent. */
@Composable
private fun MessageBubble(text: String, app: MessageApp) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
    }
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .padding(start = 48.dp, end = 4.dp)
                .graphicsLayer {
                    scaleX = appear.value
                    scaleY = appear.value
                    alpha = appear.value.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(1f, 1f)
                },
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        Text(
            text = stringResource(app.label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, end = 12.dp),
        )
    }
}
