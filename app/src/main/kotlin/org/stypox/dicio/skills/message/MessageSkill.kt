package org.stypox.dicio.skills.message

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.sentences.Sentences.Message
import org.stypox.dicio.skills.telephone.Contact
import org.stypox.dicio.skills.telephone.ContactMatcher

/** WhatsApp when the contact has it, else SMS; Slack only when asked for by name. */
class MessageSkill(
    correspondingSkillInfo: SkillInfo,
    data: StandardRecognizerData<Message>,
    private val yesNoData: StandardRecognizerData<Sentences.UtilYesNo>,
) : StandardRecognizerSkill<Message>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Message): SkillOutput {
        val (who, text) = when (inputData) {
            is Message.Send -> Pair(inputData.who, inputData.message)
            is Message.Ask -> Pair(inputData.who, null)
            is Message.Slack -> Pair(inputData.who, inputData.message)
        }
        val name = who?.trim().orEmpty()
        val message = text?.trim()?.takeIf { it.isNotEmpty() }

        val recipient = if (inputData is Message.Slack) {
            // Slack people aren't phone contacts, so Slack's own picker chooses them
            Recipient(name, null, MessageApp.Slack, confident = true)
        } else {
            findRecipient(ctx, name) ?: return MessageOutput.NoContact(name)
        }
        return if (message == null) {
            MessageOutput.AskText(recipient, yesNoData)
        } else {
            SendMessageOutput(recipient, message, yesNoData)
        }
    }

    private fun findRecipient(ctx: SkillContext, name: String): Recipient? {
        val resolver = ctx.android.contentResolver
        val hasWhatsApp = Messenger.isInstalled(ctx.android, Messenger.WHATSAPP)
        val contacts = Contact.getFilteredSortedContacts(resolver, name)
        for ((i, contact) in contacts.take(MAX_CANDIDATES).withIndex()) {
            val whatsApp = if (hasWhatsApp) contact.getWhatsAppNumber(resolver) else null
            val numbers = if (whatsApp == null) contact.getNumbers(resolver) else listOf()
            if (whatsApp == null && numbers.isEmpty()) continue

            val next = contacts.getOrNull(i + 1)
            // same bar as calling directly; anything looser waits for a tap
            val confident = contact.score >= ContactMatcher.CLEAR_MATCH
                    && (next == null || contact.score - next.score >= ContactMatcher.CLEAR_LEAD)
                    && (whatsApp != null || numbers.size == 1)
            return Recipient(
                name = contact.name,
                address = whatsApp ?: numbers[0],
                app = if (whatsApp != null) MessageApp.WhatsApp else MessageApp.Sms,
                confident = confident,
            )
        }
        return null
    }

    companion object {
        private const val MAX_CANDIDATES = 5
    }
}
