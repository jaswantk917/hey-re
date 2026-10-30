package org.stypox.dicio.skills.home

import android.util.Log
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.AlwaysWorstScore
import org.dicio.skill.skill.Score
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.stypox.dicio.sentences.Sentences.SmartHome

/**
 * "Cooler band karo", "turn on the bedroom light", "sab band": straight away, no confirmation, as
 * a light is easy to switch back. "1 ghanta baad cooler band kar dena" schedules it instead.
 */
class HomeSkill(
    correspondingSkillInfo: SkillInfo,
    private val data: StandardRecognizerData<SmartHome>,
    private val home: HomeControl,
    private val scheduler: HomeScheduler,
) : Skill<SmartHome>(correspondingSkillInfo, data.specificity) {

    override fun score(ctx: SkillContext, input: String): Pair<Score, SmartHome> {
        val (score, result) = data.score(ctx, input)
        // "timer band karo" has the same shape; only something that can be a device counts
        return if (HomeTargets.mayBeDevice(result.what.orEmpty(), home.known)) Pair(score, result)
        else Pair(AlwaysWorstScore, result)
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: SmartHome): SkillOutput {
        val on = inputData is SmartHome.TurnOn || inputData is SmartHome.TurnOnLater
        val delay = when (inputData) {
            is SmartHome.TurnOnLater -> inputData.delay
            is SmartHome.TurnOffLater -> inputData.delay
            else -> null
        }?.toJavaDuration()
        val what = inputData.what.orEmpty().trim()
        if (!home.connect()) return HomeOutput.NotConnected(home)
        val devices = try {
            home.switchables()
        } catch (e: Exception) {
            Log.w(TAG, "Could not list Google Home devices", e)
            return HomeOutput.Unreachable
        }
        return when (val match = HomeTargets.resolve(what, devices.map { it.target })) {
            is HomeTargets.Match.Found -> {
                val chosen = devices.filter { it.target in match.targets }
                if (delay == null) {
                    switch(chosen, on)
                } else {
                    val at = System.currentTimeMillis() + delay.toMillis()
                    HomeOutput.Scheduled(scheduler.schedule(match.targets, on, at), chosen, scheduler)
                }
            }
            is HomeTargets.Match.Ambiguous ->
                if (delay == null) HomeOutput.Choose(devices.filter { it.target in match.candidates }, on, home)
                else HomeOutput.SayWhich(match.candidates)
            HomeTargets.Match.None -> HomeOutput.NoDevice(what)
        }
    }

    private suspend fun switch(devices: List<Switchable>, on: Boolean): SkillOutput {
        val failed = home.set(devices, on)
        return HomeOutput.Switched(devices, on, failed.map { it.target.id }.toSet(), home)
    }

    companion object {
        private val TAG = HomeSkill::class.simpleName
    }
}

private val SmartHome.what: String?
    get() = when (this) {
        is SmartHome.TurnOn -> what
        is SmartHome.TurnOff -> what
        is SmartHome.TurnOnLater -> what
        is SmartHome.TurnOffLater -> what
    }
