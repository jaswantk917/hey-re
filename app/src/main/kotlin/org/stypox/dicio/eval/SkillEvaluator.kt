package org.stypox.dicio.eval

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.Permission
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.di.SkillContextInternal
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.io.graphical.ErrorSkillOutput
import org.stypox.dicio.io.graphical.MissingPermissionsSkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.assist.ScreenContext
import org.stypox.dicio.io.assist.isLocked
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.util.getString
import org.stypox.dicio.io.assist.ScreenSummary
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.skills.localbrain.LocalBrain
import org.stypox.dicio.skills.localbrain.LocalBrainInfo
import org.stypox.dicio.skills.localbrain.LocalBrainOutput
import org.stypox.dicio.skills.localbrain.LocalBrainSkill
import org.stypox.dicio.ui.home.Interaction
import org.stypox.dicio.ui.home.InteractionLog
import org.stypox.dicio.ui.home.PendingQuestion
import org.stypox.dicio.ui.home.QuestionAnswer
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.util.Hinglish
import org.stypox.dicio.util.HinglishNumbers
import org.stypox.dicio.util.RecognizeEverythingSkill
import org.dicio.skill.context.SkillContext
import javax.inject.Singleton
import org.dicio.skill.standard.util.MatchHelper

interface SkillEvaluator {
    val state: StateFlow<InteractionLog>

    var permissionRequester: suspend (List<Permission>) -> Boolean

    /** Asks for the phone to be unlocked, from whichever activity is showing; true once it is. */
    var unlockRequester: suspend () -> Boolean

    fun processInputEvent(event: InputEvent)
}

class SkillEvaluatorImpl(
    private val skillContext: SkillContextInternal,
    private val skillHandler: SkillHandler,
    private val sttInputDevice: SttInputDeviceWrapper,
    private val localBrain: LocalBrain,
) : SkillEvaluator {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val skillRanker: SkillRanker
        get() = skillHandler.skillRanker.value

    private val _state = MutableStateFlow(
        InteractionLog(
            interactions = listOf(),
            pendingQuestion = null,
        )
    )
    override val state: StateFlow<InteractionLog> = _state

    // must be kept up to date even when the activity is recreated, for this reason it is `var`
    override var permissionRequester: suspend (List<Permission>) -> Boolean = { false }

    override var unlockRequester: suspend () -> Boolean = { !skillContext.android.isLocked() }

    override fun processInputEvent(event: InputEvent) {
        scope.launch {
            suspendProcessInputEvent(event)
        }
    }

    private suspend fun suspendProcessInputEvent(event: InputEvent) {
        when (event) {
            is InputEvent.Error -> {
                addErrorInteractionFromPending(event.throwable)
            }
            is InputEvent.Final -> {
                _state.value = _state.value.copy(
                    pendingQuestion = PendingQuestion(
                        userInput = event.utterances[0].first,
                        continuesLastInteraction = skillRanker.hasAnyBatches(),
                        skillBeingEvaluated = null,
                    )
                )
                evaluateMatchingSkill(event.utterances.map { it.first })
            }
            InputEvent.None -> {
                _state.value = _state.value.copy(pendingQuestion = null)
            }
            is InputEvent.Partial -> {
                _state.value = _state.value.copy(
                    pendingQuestion = PendingQuestion(
                        userInput = event.utterance,
                        // the next input can be a continuation of the last interaction only if the
                        // last skill invocation provided some skill batches (which are the only way
                        // to continue an interaction/conversation)
                        continuesLastInteraction = skillRanker.hasAnyBatches(),
                        skillBeingEvaluated = null,
                    )
                )
            }
        }
    }

    private suspend fun evaluateMatchingSkill(utterances: List<String>) {
        val (chosenInput, chosenSkill) = try {
            askAboutScreen(utterances[0])
                ?: findBestMatch(utterances)
                ?: askLocalBrain(utterances[0])
                ?: Pair(utterances[0], skillRanker.getFallbackSkill(skillContext, utterances[0]))
        } catch (throwable: Throwable) {
            addErrorInteractionFromPending(throwable)
            return
        } finally {
            // standardMatchHelper only needs to be set while calling score() on skills, so once
            // all matching and scoring is done, free up the memory it uses (which may be
            // significant since the purpose of MatchHelper is to cache information about the input)
            skillContext.standardMatchHelper = null
        }
        val skillInfo = chosenSkill.skill.correspondingSkillInfo

        _state.value = _state.value.copy(
            pendingQuestion = PendingQuestion(
                userInput = chosenInput,
                // the skill ranker would have discarded all batches, if the chosen skill was not
                // the continuation of the last interaction (since continuing an
                // interaction/conversation is done through the stack of batches)
                continuesLastInteraction = skillRanker.hasAnyBatches(),
                skillBeingEvaluated = skillInfo,
            )
        )

        try {
            val permissions = skillInfo.neededPermissions
            if (permissions.isNotEmpty() && !permissionRequester(permissions)) {
                // permissions were not granted, show message
                addInteractionFromPending(MissingPermissionsSkillOutput(skillInfo))
                return
            }
            // over the lock screen only what's harmless and not personal runs before unlocking
            if (skillInfo.id !in LOCK_SCREEN_SKILLS && skillContext.android.isLocked() && !unlockRequester()) {
                addInteractionFromPending(object : HeadlineSpeechSkillOutput {
                    override fun getSpeechOutput(ctx: SkillContext) = ctx.getString(R.string.unlock_first)
                })
                return
            }

            skillContext.previousOutput =
                _state.value.interactions.lastOrNull()?.questionsAnswers?.lastOrNull()?.answer
            val output = chosenSkill.generateOutput(skillContext)

            val interactionPlan = output.getInteractionPlan(skillContext)
            addInteractionFromPending(output)
            output.getSpeechOutput(skillContext).let {
                if (it.isNotBlank()) {
                    withContext (Dispatchers.Main) {
                        skillContext.speechOutputDevice.speak(it)
                    }
                }
            }

            when (interactionPlan) {
                InteractionPlan.FinishInteraction -> {
                    // current conversation has ended, reset to the default batch of skills
                    skillRanker.removeAllBatches()
                }
                is InteractionPlan.FinishSubInteraction -> {
                    skillRanker.removeTopBatch()
                }
                is InteractionPlan.Continue -> {
                    // nothing to do, just continue with current batches
                }
                is InteractionPlan.StartSubInteraction -> {
                    skillRanker.addBatchToTop(interactionPlan.nextSkills)
                }
                is InteractionPlan.ReplaceSubInteraction -> {
                    skillRanker.removeTopBatch()
                    skillRanker.addBatchToTop(interactionPlan.nextSkills)
                }
            }

            if (interactionPlan.reopenMicrophone) {
                skillContext.speechOutputDevice.runWhenFinishedSpeaking {
                    sttInputDevice.tryLoad(this::processInputEvent)
                }
            }

        } catch (throwable: Throwable) {
            addErrorInteractionFromPending(throwable)
            return
        }
    }

    private fun findBestMatch(utterances: List<String>): Pair<String, SkillWithResult<*>>? =
        utterances.firstNotNullOfOrNull { input: String ->
            // "बंद करो" -> "band karo" for the grammars and "paanch minute" -> "5 minute" for the
            // English number parser; the user still sees exactly what they said
            val normalized = HinglishNumbers.normalize(Hinglish.romanize(input))
            skillContext.standardMatchHelper = MatchHelper(skillContext.parserFormatter, normalized)
            skillRanker.getBest(skillContext, normalized)?.let { skillWithResult ->
                Pair(input, skillWithResult)
            }
        }

    /**
     * Nothing matched: let the on-device model restate the request as a command some skill knows
     * (the user still sees what they actually said), and if that doesn't land, answer it itself.
     */
    private suspend fun askLocalBrain(input: String): Pair<String, SkillWithResult<*>>? {
        if (!localBrain.isReady()) return null
        _state.value = _state.value.copy(
            pendingQuestion = _state.value.pendingQuestion?.copy(skillBeingEvaluated = LocalBrainInfo)
        )
        val commands = skillHandler.enabledSkillsInfo.value.orEmpty()
            .map { it.sentenceExample(skillContext.android) }
            .filter { it.isNotBlank() }
        // the conversation from before the phone was locked isn't for whoever holds it now
        val recent = if (skillContext.android.isLocked()) listOf() else recentTurns()
        val rewritten = localBrain.rewriteAsCommand(input, commands, recent)
            ?.takeUnless { copiesAnExample(it, Hinglish.romanize(input), commands) }
        val match = rewritten?.let { findBestMatch(listOf(it)) }?.second
        val yesNo = Sentences.UtilYesNo[skillContext.sentencesLanguage]
        if (rewritten != null && match != null && yesNo != null &&
            match.skill.correspondingSkillInfo.id in ACTING_SKILLS
        ) {
            return Pair(input, confirmGuess(rewritten, match, yesNo))
        }
        val skill = LocalBrainSkill(localBrain, recent, ScreenContext.snapshot.value)
        return Pair(input, match ?: skill.scoreAndWrapResult(skillContext, input))
    }

    /**
     * "Summarize this", "yeh kya hai" while summoned over an app: about the screen, so it skips
     * the skills (search would take "what is this"). The summary made on summon answers right away.
     */
    private suspend fun askAboutScreen(input: String): Pair<String, SkillWithResult<*>>? {
        val screen = ScreenContext.snapshot.value ?: return null
        val roman = Hinglish.romanize(input)
        if (!SCREEN_QUESTION.containsMatchIn(roman)) return null
        val ready = (ScreenContext.summary.value as? ScreenSummary.Ready)?.text
        val skill = if (ready != null && SUMMARY_REQUEST.containsMatchIn(roman)) {
            object : RecognizeEverythingSkill(LocalBrainInfo) {
                override suspend fun generateOutput(ctx: SkillContext, inputData: String) = LocalBrainOutput(ready)
            }
        } else if (localBrain.isReady()) {
            _state.value = _state.value.copy(
                pendingQuestion = _state.value.pendingQuestion?.copy(skillBeingEvaluated = LocalBrainInfo)
            )
            LocalBrainSkill(localBrain, recentTurns(), screen)
        } else {
            return null
        }
        return Pair(input, skill.scoreAndWrapResult(skillContext, input))
    }

    private fun recentTurns(): List<LocalBrain.Turn> = _state.value.interactions
        .flatMap { it.questionsAnswers }
        .takeLast(RECENT_TURNS)
        .mapNotNull { qa ->
            val question = qa.question ?: return@mapNotNull null
            val answer = runCatching { qa.answer.getSpeechOutput(skillContext) }.getOrNull()
            answer?.takeIf { it.isNotBlank() }?.let { LocalBrain.Turn(question, it) }
        }

    private fun addErrorInteractionFromPending(throwable: Throwable) {
        Log.e(TAG, "Error while evaluating skills", throwable)
        addInteractionFromPending(ErrorSkillOutput(throwable, true))
    }

    private fun addInteractionFromPending(skillOutput: SkillOutput) {
        val log = _state.value
        val pendingUserInput = log.pendingQuestion?.userInput
        val pendingContinuesLastInteraction = log.pendingQuestion?.continuesLastInteraction
            ?: skillRanker.hasAnyBatches()
        val pendingSkill = log.pendingQuestion?.skillBeingEvaluated
        val questionAnswer = QuestionAnswer(pendingUserInput, skillOutput)

        _state.value = log.copy(
            interactions = log.interactions.toMutableList().also { inters ->
                if (pendingContinuesLastInteraction && inters.isNotEmpty()) {
                    inters[inters.size - 1] = inters[inters.size - 1].let { i -> i.copy(
                        questionsAnswers = i.questionsAnswers.toMutableList()
                            .apply { add(questionAnswer) }
                    ) }
                } else {
                    inters.add(
                        Interaction(
                            skill = pendingSkill,
                            questionsAnswers = listOf(questionAnswer)
                        )
                    )
                }
            },
            pendingQuestion = null,
        )
    }

    companion object {
        val TAG = SkillEvaluator::class.simpleName
        // enough for "should I call Maa?" -> "haan karo", short enough for Nano's small context
        private const val RECENT_TURNS = 3
        // the time, a timer or the torch is fine from the lock screen; calls, messages, switches
        // and anything showing the owner's day wait for an unlock
        internal val LOCK_SCREEN_SKILLS = setOf(
            "current_time", "timer", "alarm", "weather", "calculator", "flashlight", "Joke", "media",
            "help", "listening", "lyrics", "translation", "sound_mode", "local_brain", "text",
        )

        // skills that change the phone or reach someone: a guess at them asks first
        private val ACTING_SKILLS = setOf(
            "telephone", "message", "phone_toggle", "smart_home", "sound_mode", "alarm", "timer",
            "navigation", "nearby",
        )

        /**
         * Nano sometimes answers with an example command word for word ("should I walk more?" ->
         * "Navigate to Vancouver international airport"). It's a copy, not a translation, when it
         * carries the example's own details (a place, a name, a number) the person never said;
         * "kitne baje hain" -> "What time is it?" has none and is a fine answer.
         */
        internal fun copiesAnExample(rewritten: String, input: String, examples: List<String>): Boolean {
            val example = examples.firstOrNull { words(it) == words(rewritten) } ?: return false
            val details = EXAMPLE_DETAIL.findAll(example).map { it.value.lowercase() }
            val said = words(input).toSet()
            return details.any { it !in said }
        }

        private fun words(text: String) =
            text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

        // names and places are capitalized past the first word; numbers count only in digits,
        // since "paanch minute" rightly becomes "five minutes"
        private val EXAMPLE_DETAIL = Regex("(?<=\\s)\\p{Lu}\\p{L}*|\\p{N}+")

        internal val SUMMARY_REQUEST = Regex(
            "summari[sz]e|sum (it|this) up|what'?s (this|on (my|the) screen)|what is (this|on (my|the) screen)|" +
                    "(ye|yeh) kya hai|isme kya|screen (pe|par) kya",
            RegexOption.IGNORE_CASE,
        )
        internal val SCREEN_QUESTION = Regex(
            "\\b(${SUMMARY_REQUEST.pattern}|explain (this|it)|read (this|it)|translate (this|it)|" +
                    "this (page|screen|message|post|article|email|chat)|is (screen|page|message)|" +
                    "samjhao|samjha do|kya likha|iska matlab|isko|isme|ismein)\\b",
            RegexOption.IGNORE_CASE,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
class SkillEvaluatorModule {
    @Provides
    @Singleton
    fun provideSkillEvaluator(
        skillContext: SkillContextInternal,
        skillHandler: SkillHandler,
        sttInputDevice: SttInputDeviceWrapper,
        localBrain: LocalBrain,
    ): SkillEvaluator {
        return SkillEvaluatorImpl(skillContext, skillHandler, sttInputDevice, localBrain)
    }
}
