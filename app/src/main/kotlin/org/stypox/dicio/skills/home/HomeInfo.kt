package org.stypox.dicio.skills.home

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences

object HomeInfo : SkillInfo("smart_home") {
    override fun name(context: Context) =
        context.getString(R.string.skill_name_smart_home)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_smart_home)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.Lightbulb)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.SmartHome[ctx.sentencesLanguage] ?: return null
        val entryPoint = EntryPointAccessors.fromApplication(ctx.android.applicationContext, HomeEntryPoint::class.java)
        return HomeSkill(HomeInfo, data, entryPoint.homeControl(), entryPoint.homeScheduler())
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HomeEntryPoint {
        fun homeControl(): HomeControl
        fun homeScheduler(): HomeScheduler
    }
}
