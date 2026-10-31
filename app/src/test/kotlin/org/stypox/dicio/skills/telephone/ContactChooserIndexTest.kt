package org.stypox.dicio.skills.telephone

import android.content.Context
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.dicio.numbers.ParserFormatter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.context.SpeechOutputDevice
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.util.MatchHelper
import org.stypox.dicio.mocked
import org.stypox.dicio.sentences.Sentences
import java.util.Locale

private val parserFormatter = ParserFormatter(Locale.ENGLISH)

private object Ctx : SkillContext {
    override val android: Context get() = mocked()
    override val locale: Locale get() = Locale.ENGLISH
    override val sentencesLanguage: String get() = "en"
    override val parserFormatter: ParserFormatter get() = org.stypox.dicio.skills.telephone.parserFormatter
    override val speechOutputDevice: SpeechOutputDevice get() = mocked()
    override val previousOutput: SkillOutput? get() = null
    override val standardMatchHelper: MatchHelper get() = mocked()
}

class ContactChooserIndexTest : StringSpec({
    // the card's rows say "Number 1", "Number 2"... when tapped, so a tap picks the row's number
    "a tapped row picks that number" {
        val chooser = ContactChooserIndex(
            listOf("Papa" to "+91 98765 43210", "Papa" to "+91 98765 43211"),
            Sentences.UtilYesNo["en"]!!,
        )
        chooser.score(Ctx, "Number 1").second shouldBe 1
        chooser.score(Ctx, "Number 2").second shouldBe 2
    }
})
