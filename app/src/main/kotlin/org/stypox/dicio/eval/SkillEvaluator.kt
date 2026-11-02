package org.stypox.dicio.eval

import android.util.Log
import android.graphics.Bitmap
import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.Permission
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.di.SkillContextInternal
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.history.ChatHistory
import org.stypox.dicio.memory.Memory
import org.stypox.dicio.memory.MemoryLearner
import org.stypox.dicio.memory.MemoryInfo
import org.stypox.dicio.memory.MemoryOutput
import org.stypox.dicio.memory.MemoryRequest
import org.stypox.dicio.history.RestoredAnswer
import org.stypox.dicio.history.SavedChat
import org.stypox.dicio.history.SavedTurn
import org.stypox.dicio.io.graphical.ErrorSkillOutput
import org.stypox.dicio.io.graphical.MissingPermissionsSkillOutput
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.skills.brief.BriefInfo
import org.stypox.dicio.skills.image.ImageInfo
import org.stypox.dicio.skills.image.ImageOutput
import org.stypox.dicio.skills.image.TextReader
import org.stypox.dicio.skills.brief.BriefSkill
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

    /** Summoned: if the person then says nothing, show what needs them instead (the brief). */
    fun expectBrief()

    /** The app was opened: if the person does nothing for a moment, show the brief quietly. */
    fun briefIfIdle()

    /** An image the person showed (camera, gallery, pasted): read it, say what it is, keep it to ask about. */
    fun processImage(image: Bitmap)

    /** The saved chat being added to, or 0 before anything worth saving was said. */
    val chatId: StateFlow<Long>

    /** Clears the screen for a new conversation; the last one is in the history if it was worth keeping. */
    fun newChat()

    /** Brings back an earlier conversation, to read or carry on. */
    fun openChat(chat: SavedChat)

    fun deleteChat(id: Long)

    /** Back after a while away: the old conversation gives way to a fresh screen. */
    fun startFreshIfStale()

    /**
     * Chat mode: everything said goes to the on-device model as conversation, never guessed as a
     * command. Lasts for the current chat.
     */
    val chatMode: StateFlow<Boolean>

    fun setChatMode(on: Boolean)
}

class SkillEvaluatorImpl(
    private val skillContext: SkillContextInternal,
    private val skillHandler: SkillHandler,
    private val sttInputDevice: SttInputDeviceWrapper,
    private val localBrain: LocalBrain,
    private val textReader: TextReader,
    private val chatHistory: ChatHistory,
    private val memory: Memory,
    private val memoryLearner: MemoryLearner,
) : SkillEvaluator {

    private val scope = CoroutineScope(Dispatchers.Default)
    // what was said and shown, one at a time and in order: a late partial or the next question
    // never rewrites the question still being answered
    private val inputs = Channel<suspend () -> Unit>(Channel.UNLIMITED).also { inputs ->
        scope.launch {
            for (input in inputs) {
                try {
                    input()
                } catch (e: CancellationException) {
                    throw e
                } catch (throwable: Throwable) {
                    // never stop listening to what comes next because one input failed
                    Log.e(TAG, "Could not handle input", throwable)
                }
            }
        }
    }
    // one at a time and in order, so an older copy of the chat never overwrites a newer one
    private val historyScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))

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

    private val _chatMode = MutableStateFlow(false)
    override val chatMode: StateFlow<Boolean> = _chatMode

    override fun setChatMode(on: Boolean) {
        _chatMode.value = on
        sttInputDevice.setPatient(on)
    }

    private val _chatId = MutableStateFlow(0L)
    override val chatId: StateFlow<Long> = _chatId
    @Volatile
    private var lastAnswerAt = 0L

    init {
        // an update or a restart mid-conversation picks it up where it was
        scope.launch {
            val latest = chatHistory.load().firstOrNull() ?: return@launch
            val log = _state.value
            if (log.interactions.isEmpty() && log.pendingQuestion == null &&
                System.currentTimeMillis() - latest.updatedAt < SESSION_GAP_MILLIS
            ) openChat(latest)
        }
    }

    override fun newChat() {
        if (_state.value.pendingQuestion != null) return
        skillRanker.removeAllBatches()
        ScreenContext.forgetImage()
        _state.value = InteractionLog(interactions = listOf(), pendingQuestion = null)
        _chatId.value = 0L
        setChatMode(false)
        learnFromFinishedChats()
    }

    override fun openChat(chat: SavedChat) {
        scope.launch {
            val skills = (skillHandler.allSkillInfoList + LocalBrainInfo + ImageInfo).associateBy { it.id }
            val interactions = chat.turns.map { turn ->
                val image = turn.image?.let { chatHistory.image(it) }
                Interaction(
                    skill = skills[turn.skillId],
                    questionsAnswers = listOf(QuestionAnswer(turn.question, RestoredAnswer(turn, image))),
                )
            }
            if (_state.value.pendingQuestion != null) return@launch
            skillRanker.removeAllBatches()
            ScreenContext.forgetImage()
            _state.value = InteractionLog(interactions = interactions, pendingQuestion = null)
            _chatId.value = chat.id
            lastAnswerAt = System.currentTimeMillis()
        }
    }

    override fun deleteChat(id: Long) {
        historyScope.launch { chatHistory.delete(id) }
        if (id == _chatId.value) newChat()
    }

    override fun startFreshIfStale() {
        val log = _state.value
        if (log.pendingQuestion == null && log.interactions.isNotEmpty() && lastAnswerAt != 0L &&
            System.currentTimeMillis() - lastAnswerAt > SESSION_GAP_MILLIS
        ) newChat() else learnFromFinishedChats()
    }

    /** Reads the chats that ended for facts worth remembering, giving way to anything the person asks. */
    private var learning: Job? = null

    private fun learnFromFinishedChats() {
        learning?.cancel()
        learning = scope.launch {
            delay(LEARN_DELAY_MILLIS)
            if (skillContext.android.isLocked()) return@launch
            memoryLearner.learn(_chatId.value) { _state.value.pendingQuestion != null }
        }
    }

    /** Nano takes one request at a time: the person's question goes first, the chat is read another time. */
    private fun stopLearning() {
        learning?.cancel()
    }

    /** Saves the conversation so far, as words and images: the cards themselves can't outlive the app. */
    private fun rememberChat() {
        val now = System.currentTimeMillis()
        lastAnswerAt = now
        if (_chatId.value == 0L) _chatId.value = now
        val id = _chatId.value
        val turns = mutableListOf<SavedTurn>()
        val images = mutableMapOf<Int, Bitmap>()
        for (interaction in _state.value.interactions) {
            for (qa in interaction.questionsAnswers) {
                val answer = qa.answer
                if (answer is RestoredAnswer) {
                    turns += answer.turn
                    continue
                }
                if (answer is ErrorSkillOutput || answer is MissingPermissionsSkillOutput ||
                    interaction.skill?.id == BriefInfo.id
                ) continue
                (answer as? ImageOutput)?.let { images[turns.size] = it.image }
                val text = runCatching { answer.getSpeechOutput(skillContext) }.getOrDefault("")
                turns += SavedTurn(qa.question, text, interaction.skill?.id)
            }
        }
        historyScope.launch { chatHistory.save(SavedChat(id, now, turns), images) }
    }

    // set on summon, dropped as soon as anything is heard: only a quiet summon gets the brief
    @Volatile
    private var briefOnSilence = false

    override fun expectBrief() {
        briefOnSilence = true
    }

    private var lastIdleBrief = 0L

    override fun briefIfIdle() {
        scope.launch {
            val interactionsBefore = _state.value.interactions.size
            delay(IDLE_BRIEF_DELAY_MILLIS)
            val log = _state.value
            val stt = sttInputDevice.uiState.value
            // summoned (the quiet-summon brief handles it), asked something, or listening already
            if (briefOnSilence || log.pendingQuestion != null || log.interactions.size != interactionsBefore ||
                stt == SttState.Listening || stt == SttState.WaitingForResult
            ) return@launch
            val now = SystemClock.elapsedRealtime()
            if (lastIdleBrief != 0L && now - lastIdleBrief < IDLE_BRIEF_EVERY_MILLIS) return@launch
            lastIdleBrief = now
            showBrief(speak = false, onlyIfSomething = true)
        }
    }

    override fun processImage(image: Bitmap) {
        briefOnSilence = false
        stopLearning()
        inputs.trySend input@{
            _state.value = _state.value.copy(
                pendingQuestion = PendingQuestion(
                    userInput = skillContext.android.getString(R.string.skill_image_question),
                    continuesLastInteraction = false,
                    skillBeingEvaluated = ImageInfo,
                )
            )
            val output = try {
                val text = textReader.read(image)
                // follow-ups ("isme kya likha hai", "translate this") are about this image now
                ScreenContext.showImage(image, text)
                val description = if (localBrain.isReady()) localBrain.describeImage(image, text) else null
                ImageOutput(image, description, text)
            } catch (e: Exception) {
                addErrorInteractionFromPending(e)
                return@input
            }
            addInteractionFromPending(output)
            output.getSpeechOutput(skillContext).takeIf { it.isNotBlank() }?.let {
                withContext(Dispatchers.Main) { skillContext.speechOutputDevice.speak(it) }
            }
        }
    }

    override fun processInputEvent(event: InputEvent) {
        if (event is InputEvent.Final || event is InputEvent.Partial) {
            briefOnSilence = false
            stopLearning()
        }
        inputs.trySend { suspendProcessInputEvent(event) }
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
                if (briefOnSilence) {
                    briefOnSilence = false
                    showBrief()
                }
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
            askMemory(utterances[0])
                ?: askAboutScreen(utterances[0])
                ?: chatWithLocalBrain(utterances[0])
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

    /**
     * The brief, as if asked for, without a question bubble; never over the lock screen. Opening
     * the app shows it only when something needs the person, and silently.
     */
    private suspend fun showBrief(speak: Boolean = true, onlyIfSomething: Boolean = false) {
        if (skillContext.android.isLocked()) return
        if (skillHandler.enabledSkillsInfo.value?.any { it.id == BriefInfo.id } != true) return
        val output = try {
            BriefSkill.brief(skillContext)
        } catch (e: Exception) {
            Log.w(TAG, "Could not gather the brief", e)
            return
        }
        if (onlyIfSomething && output.isEmpty) return
        lastAnswerAt = System.currentTimeMillis()
        val log = _state.value
        _state.value = log.copy(
            interactions = log.interactions + Interaction(
                skill = BriefInfo,
                questionsAnswers = listOf(QuestionAnswer(null, output)),
            ),
        )
        if (!speak) return
        output.getSpeechOutput(skillContext).takeIf { it.isNotBlank() }?.let {
            withContext(Dispatchers.Main) { skillContext.speechOutputDevice.speak(it) }
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
            ?.takeUnless { flipsOnOff(it, Hinglish.romanize(input)) }
        val match = rewritten?.let { findBestMatch(listOf(it)) }?.second
            ?.takeUnless { it.skill.correspondingSkillInfo.id == "flashlight" && !mentionsLight(Hinglish.romanize(input)) }
            ?.takeUnless { it.skill.correspondingSkillInfo.id == "media" && !mentionsMediaControl(Hinglish.romanize(input)) }
        val yesNo = Sentences.UtilYesNo[skillContext.sentencesLanguage]
        if (rewritten != null && match != null && yesNo != null &&
            match.skill.correspondingSkillInfo.id in ACTING_SKILLS
        ) {
            return Pair(input, confirmGuess(rewritten, match, yesNo))
        }
        val talk = if (skillContext.android.isLocked()) listOf() else recentTurns(talkOnly = true)
        val skill = LocalBrainSkill(localBrain, talk, ScreenContext.snapshot.value) { question ->
            if (skillContext.android.isLocked()) listOf() else memory.relevantTo(question, System.currentTimeMillis())
        }
        return Pair(input, match ?: skill.scoreAndWrapResult(skillContext, input))
    }

    /** In chat mode, straight to the model: no grammar match and no guessing a command. */
    private suspend fun chatWithLocalBrain(input: String): Pair<String, SkillWithResult<*>>? {
        if (!_chatMode.value || !localBrain.isReady()) return null
        _state.value = _state.value.copy(
            pendingQuestion = _state.value.pendingQuestion?.copy(skillBeingEvaluated = LocalBrainInfo)
        )
        val locked = skillContext.android.isLocked()
        val skill = LocalBrainSkill(localBrain, if (locked) listOf() else recentTurns(talkOnly = true)) { question ->
            if (locked) listOf() else memory.relevantTo(question, System.currentTimeMillis())
        }
        return Pair(input, skill.scoreAndWrapResult(skillContext, input))
    }

    /** "yaad rakhna ki…", "what do you remember about me": straight to the memory, no model in between. */
    private suspend fun askMemory(input: String): Pair<String, SkillWithResult<*>>? {
        val request = MemoryRequest.parse(input) ?: return null
        val found = if (request is MemoryRequest.Parsed.Where) {
            memory.load()
            Memory.mentioning(memory.facts.value, request.item).takeIf { it.isNotEmpty() } ?: return null
        } else listOf()
        val skill = object : RecognizeEverythingSkill(MemoryInfo) {
            override suspend fun generateOutput(ctx: SkillContext, inputData: String): SkillOutput = when (request) {
                is MemoryRequest.Parsed.Remember -> {
                    memory.add(request.fact, System.currentTimeMillis())
                    MemoryOutput.Remembered(request.fact)
                }
                MemoryRequest.Parsed.Recall -> {
                    memory.load()
                    MemoryOutput.Recalled(memory.facts.value.map { it.text })
                }
                is MemoryRequest.Parsed.Where -> MemoryOutput.Found(found)
            }
        }
        return Pair(input, skill.scoreAndWrapResult(skillContext, input))
    }

    /**
     * "Summarize this", "yeh kya hai" while summoned over an app: about the screen, so it skips
     * the skills (search would take "what is this"). The summary made on summon answers right away.
     */
    private suspend fun askAboutScreen(input: String): Pair<String, SkillWithResult<*>>? {
        val screen = ScreenContext.snapshot.value ?: return null
        val roman = Hinglish.romanize(input)
        val article = (ScreenContext.article.value as? ScreenSummary.Ready)?.text
        if (article != null && ARTICLE_REQUEST.containsMatchIn(roman)) {
            val skill = object : RecognizeEverythingSkill(LocalBrainInfo) {
                override suspend fun generateOutput(ctx: SkillContext, inputData: String) = LocalBrainOutput(article)
            }
            return Pair(input, skill.scoreAndWrapResult(skillContext, input))
        }
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
            LocalBrainSkill(localBrain, recentTurns(talkOnly = true), screen)
        } else {
            return null
        }
        return Pair(input, skill.scoreAndWrapResult(skillContext, input))
    }

    /**
     * The last few exchanges; [talkOnly] leaves out commands' results, which Nano otherwise
     * repeats as its own answer ("I could not calculate your request" to "suggest places").
     */
    private fun recentTurns(talkOnly: Boolean = false): List<LocalBrain.Turn> = _state.value.interactions
        .filter { !talkOnly || ChatHistory.isConversation(it.skill?.id) }
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
        rememberChat()
    }

    companion object {
        val TAG = SkillEvaluator::class.simpleName
        // enough for "should I call Maa?" -> "haan karo", short enough for Nano's small context
        private const val RECENT_TURNS = 3
        private const val IDLE_BRIEF_DELAY_MILLIS = 1_500L
        // half an hour away and it's a new conversation
        private const val SESSION_GAP_MILLIS = 30 * 60_000L
        // only once the person seems done for now; anything they say cancels it (stopLearning)
        private const val LEARN_DELAY_MILLIS = 10_000L
        // reopening the app a minute later shouldn't stack up the same brief
        private const val IDLE_BRIEF_EVERY_MILLIS = 10 * 60_000L
        // the time, a timer or the torch is fine from the lock screen; calls, messages, switches
        // and anything showing the owner's day wait for an unlock
        internal val LOCK_SCREEN_SKILLS = setOf(
            "current_time", "timer", "alarm", "weather", "calculator", "flashlight", "Joke", "media",
            "help", "listening", "lyrics", "translation", "sound_mode", "local_brain", "text",
        )

        // skills that change the phone or reach someone: a guess at them asks first
        private val ACTING_SKILLS = setOf(
            "telephone", "message", "phone_toggle", "smart_home", "sound_mode", "alarm", "timer",
            "navigation", "nearby", "find_hub", "phone_info",
        )

        /**
         * Nano sometimes answers with an example command word for word ("should I walk more?" ->
         * "Navigate to Vancouver international airport"). It's a copy, not a translation, when it
         * carries the example's own details (a place, a name, a number) the person never said;
         * "kitne baje hain" -> "What time is it?" has none and is a fine answer.
         */
        internal fun copiesAnExample(rewritten: String, input: String, examples: List<String>): Boolean {
            val example = examples.firstOrNull { words(it) == words(rewritten) } ?: return false
            // "Paanch minute ka timer lagao" carries a 5 just as "Wake me up at 6:30" carries a 6
            val details = EXAMPLE_DETAIL.findAll(HinglishNumbers.normalize(example)).map { it.value.lowercase() }
            val said = words(HinglishNumbers.normalize(input)).toSet()
            return details.any { it !in said }
        }

        /**
         * Nano has turned "cooler chala do" into "Cooler band karo": a guess that switches the
         * other way from what the person said is worse than no guess.
         */
        internal fun flipsOnOff(rewritten: String, input: String): Boolean {
            fun direction(text: String) = words(text).toSet().let { said ->
                (if (said.any { it in ON_WORDS }) 1 else 0) - (if (said.any { it in OFF_WORDS }) 1 else 0)
            }
            val asked = direction(input)
            return asked != 0 && direction(rewritten) == -asked
        }

        /** Nano has heard "ek selfie lo" as "Torch jala do"; the torch is only for asking about a light. */
        internal fun mentionsLight(input: String) = words(input).any { it in LIGHT_WORDS }

        /** Nano has heard "which movie should I watch" as "play the next movie"; media is for playback words. */
        internal fun mentionsMediaControl(input: String) = words(input).any { it in MEDIA_CONTROL_WORDS }

        private val MEDIA_CONTROL_WORDS = setOf(
            "play", "pause", "resume", "unpause", "next", "skip", "previous", "replay", "rewind", "stop",
            "chalao", "chala", "bajao", "baja", "lagao", "laga", "rok", "roko", "ruk", "agla", "aglaa",
            "pichla", "pichhla", "band",
        )
        private val LIGHT_WORDS = setOf("torch", "flash", "flashlight", "light", "lite", "roshni", "batti", "andhera")
        private val ON_WORDS = setOf("on", "chalu", "chala", "chalao", "jala", "jalao", "start")
        private val OFF_WORDS = setOf("off", "band", "bujha", "bujhao", "stop")

        private fun words(text: String) =
            text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

        // names and places are capitalized past the first word; numbers count only in digits,
        // since "paanch minute" rightly becomes "five minutes"
        private val EXAMPLE_DETAIL = Regex("(?<=\\s)\\p{Lu}\\p{L}*|\\p{N}+")

        internal val SUMMARY_REQUEST = Regex(
            "summari[sz]e|sum (it|this) up|what'?s (this|on (my|the) screen)|what is (this|on (my|the) screen)|" +
                    "(ye|yeh|yah|ya) kya\\b|isme kya|(screen|page) (pe|par|per|pr|mein|me|main)\\b",
            RegexOption.IGNORE_CASE,
        )
        // "poora article batao", "summarise the whole page": the article behind the link, not the screen
        internal val ARTICLE_REQUEST = Regex(
            "\\b(article|(whole|full|entire|poora|pura|puri|poori) (page|post|story|link|cheez)|(the|is|ye|yeh) link)\\b",
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
        textReader: TextReader,
        chatHistory: ChatHistory,
        memory: Memory,
        memoryLearner: MemoryLearner,
    ): SkillEvaluator {
        return SkillEvaluatorImpl(
            skillContext, skillHandler, sttInputDevice, localBrain, textReader, chatHistory, memory, memoryLearner
        )
    }
}
