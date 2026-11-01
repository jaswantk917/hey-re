package org.stypox.dicio.screenshot

import kotlinx.coroutines.flow.MutableStateFlow
import org.dicio.skill.skill.Permission
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.ui.home.InteractionLog

class FakeSkillEvaluator : SkillEvaluator {
    override val state: MutableStateFlow<InteractionLog> = MutableStateFlow(
        InteractionLog(
            interactions = listOf(),
            pendingQuestion = null,
        )
    )

    override var permissionRequester: suspend (List<Permission>) -> Boolean = { true }
    override var unlockRequester: suspend () -> Boolean = { true }

    override fun processInputEvent(event: InputEvent) {
    }

    override fun expectBrief() {}

    override fun briefIfIdle() {}

    override fun processImage(image: android.graphics.Bitmap) {}
    override val chatId = kotlinx.coroutines.flow.MutableStateFlow(0L)
    override fun newChat() {}
    override fun openChat(chat: org.stypox.dicio.history.SavedChat) {}
    override fun deleteChat(id: Long) {}
    override fun startFreshIfStale() {}
}
