package org.stypox.dicio.skills.nearby

import android.content.Intent
import android.net.Uri
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.sentences.Sentences.Nearby as NearbySentences
import org.stypox.dicio.util.getString

/** "Khane ki jagah dhundho aaspaas": Maps searching around where the user is. */
class NearbySkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<NearbySentences>) :
    StandardRecognizerSkill<NearbySentences>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: NearbySentences): SkillOutput {
        val (what, hinglish) = when (inputData) {
            is NearbySentences.English -> inputData.what to false
            is NearbySentences.Hinglish -> inputData.what to true
        }
        val query = Nearby.query(what.orEmpty())
        // a geo query without a place is searched around the phone's location
        ctx.android.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return object : HeadlineSpeechSkillOutput {
            override fun getSpeechOutput(ctx: SkillContext) =
                ctx.getString(if (hinglish) R.string.skill_nearby_showing_hi else R.string.skill_nearby_showing, query)
        }
    }
}
