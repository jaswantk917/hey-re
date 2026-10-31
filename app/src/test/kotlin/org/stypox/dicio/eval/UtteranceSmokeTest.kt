package org.stypox.dicio.eval

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import org.dicio.numbers.ParserFormatter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.context.SpeechOutputDevice
import org.dicio.skill.skill.AlwaysWorstScore
import org.dicio.skill.skill.Score
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.util.MatchHelper
import org.stypox.dicio.mocked
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.skills.home.HomeTarget
import org.stypox.dicio.skills.home.HomeTargets
import org.stypox.dicio.skills.home.what
import org.stypox.dicio.util.Hinglish
import org.stypox.dicio.util.HinglishNumbers
import java.io.File
import java.util.Locale

/**
 * Every sentence in smoke/utterances.tsv through the same ranker and Hinglish handling the app
 * uses, grammar only (Gemini Nano is the phone's part). Writes the whole table to
 * build/reports/smoke.txt and fails on any sentence that lands somewhere it shouldn't.
 */
class UtteranceSmokeTest : StringSpec({
    "what gets said reaches the right skill" {
        val rows = javaClass.classLoader!!.getResource("smoke/utterances.tsv")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val (expected, sentence) = line.split("\t", limit = 2)
                Row(expected.removePrefix("?"), sentence, knownGap = expected.startsWith("?"))
            }
        val results = rows.map { it to (route(it.sentence) ?: "-") }

        File("build/reports").mkdirs()
        File("build/reports/smoke.txt").writeText(
            results.joinToString("\n") { (row, got) ->
                val mark = when {
                    got == row.expected -> "ok  "
                    row.knownGap -> "gap "
                    else -> "FAIL"
                }
                "$mark  ${row.sentence.padEnd(48)} -> $got" +
                        if (got != row.expected) "   (wanted ${row.expected})" else ""
            } + "\n"
        )

        results.filter { (row, got) -> got != row.expected && !row.knownGap }
            .map { (row, got) -> "\"${row.sentence}\" went to $got, wanted ${row.expected}" }
            .shouldBeEmpty()
    }
})

private data class Row(val expected: String, val sentence: String, val knownGap: Boolean)

private val parserFormatter = ParserFormatter(Locale.ENGLISH)

// the owner's Google Home, so "cooler band karo" can be a device and "timer band karo" can't
private val home = listOf(
    HomeTarget("1", "Light", "Hall", true),
    HomeTarget("2", "Cooler", "Bedroom", false),
)

private val ranker = SkillRanker(
    listOf(
        grammar("weather", Sentences.Weather["en"]!!),
        grammar("search", Sentences.Search["en"]!!),
        grammar("lyrics", Sentences.Lyrics["en"]!!),
        grammar("open", Sentences.Open["en"]!!),
        grammar("calculator", Sentences.Calculator["en"]!!),
        grammar("navigation", Sentences.Navigation["en"]!!),
        grammar("nearby", Sentences.Nearby["en"]!!),
        grammar("telephone", Sentences.Telephone["en"]!!),
        grammar("message", Sentences.Message["en"]!!),
        grammar("smart_home", Sentences.SmartHome["en"]!!) { result ->
            HomeTargets.mayBeDevice((result as Sentences.SmartHome).what.orEmpty(), home)
        },
        grammar("sound_mode", Sentences.SoundMode["en"]!!),
        grammar("phone_toggle", Sentences.PhoneToggle["en"]!!),
        grammar("check_in", Sentences.CheckIn["en"]!!),
        grammar("help", Sentences.Help["en"]!!),
        grammar("timer", Sentences.Timer["en"]!!),
        grammar("alarm", Sentences.Alarm["en"]!!),
        grammar("current_time", Sentences.CurrentTime["en"]!!),
        grammar("media", Sentences.Media["en"]!!),
        grammar("Joke", Sentences.Joke["en"]!!),
        grammar("listening", Sentences.Listening["en"]!!),
        grammar("notify", Sentences.Notify["en"]!!),
        grammar("flashlight", Sentences.Flashlight["en"]!!),
        grammar("translation", Sentences.Translation["en"]!!),
    ),
    grammar("text", Sentences.Joke["en"]!!),
)

/** The skill id the app's grammar layer picks, null when none does (and Nano would be asked). */
private fun route(said: String): String? {
    // as SkillEvaluatorImpl.findBestMatch does
    val normalized = HinglishNumbers.normalize(Hinglish.romanize(said))
    return ranker.getBest(ScoringContext(normalized), normalized)?.skill?.correspondingSkillInfo?.id
}

private class ScoringContext(input: String) : SkillContext {
    override val android: Context get() = mocked()
    override val locale: Locale get() = Locale.ENGLISH
    override val sentencesLanguage: String get() = "en"
    override val parserFormatter: ParserFormatter get() = org.stypox.dicio.eval.parserFormatter
    override val speechOutputDevice: SpeechOutputDevice get() = mocked()
    override val previousOutput: SkillOutput? get() = null
    override val standardMatchHelper: MatchHelper = MatchHelper(parserFormatter, input)
}

private class IdOnly(id: String) : SkillInfo(id) {
    override fun name(context: Context): String = mocked()
    override fun sentenceExample(context: Context): String = mocked()
    @Composable override fun icon(): Painter = mocked()
    override fun build(ctx: SkillContext) = mocked()
}

/** A skill that only scores, with an extra check a real skill adds on top of its grammar. */
private fun <T> grammar(
    id: String,
    data: StandardRecognizerData<T>,
    accepts: (T) -> Boolean = { true },
): Skill<T> = object : Skill<T>(IdOnly(id), data.specificity) {
    override fun score(ctx: SkillContext, input: String): Pair<Score, T> {
        val (score, result) = data.score(ctx, input)
        return if (accepts(result)) Pair(score, result) else Pair(AlwaysWorstScore, result)
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: T): SkillOutput = mocked()
}
