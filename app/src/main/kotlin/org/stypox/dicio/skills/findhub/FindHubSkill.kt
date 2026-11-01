package org.stypox.dicio.skills.findhub

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.io.shell.AppFunctions.Outcome
import org.stypox.dicio.sentences.Sentences.FindHub as Said

/**
 * "Mere watch kahan hai", "ring my watch": a device Find Hub knows is located or rung. Where
 * things were put is Hey re's own memory, asked before this (SkillEvaluator.askMemory).
 */
class FindHubSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Said>) :
    StandardRecognizerSkill<Said>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Said): SkillOutput {
        return when (inputData) {
            is Said.Where -> where(clean(inputData.item) ?: return FindHubOutput.NotUnderstood)
            is Said.Ring -> ring(clean(inputData.item) ?: return FindHubOutput.NotUnderstood, true)
            is Said.StopRing -> ring(clean(inputData.item) ?: return FindHubOutput.NotUnderstood, false)
        }
    }

    private suspend fun where(item: String): SkillOutput {
        val devices = when (val outcome = FindHub.devices()) {
            Outcome.NoShizuku -> return FindHubOutput.NeedsShizuku
            is Outcome.Done -> @Suppress("UNCHECKED_CAST") (outcome.value as List<FindHub.Device>)
            // Find Hub can't list devices right now; what it remembers still answers
            is Outcome.Failed -> listOf()
        }
        FindHub.match(item, devices)?.let { device ->
            return when (val outcome = FindHub.locate(device)) {
                is Outcome.Done -> FindHubOutput.Located(device, outcome.value as? FindHub.Location)
                Outcome.NoShizuku -> FindHubOutput.NeedsShizuku
                is Outcome.Failed -> FindHubOutput.Failed
            }
        }
        return when (val outcome = FindHub.recall(item)) {
            is Outcome.Done -> FindHubOutput.Recalled(item, outcome.value as? String)
            Outcome.NoShizuku -> FindHubOutput.NeedsShizuku
            is Outcome.Failed -> FindHubOutput.Failed
        }
    }

    private suspend fun ring(item: String, start: Boolean): SkillOutput {
        val devices = when (val outcome = FindHub.devices()) {
            Outcome.NoShizuku -> return FindHubOutput.NeedsShizuku
            is Outcome.Failed -> return FindHubOutput.Failed
            is Outcome.Done -> @Suppress("UNCHECKED_CAST") (outcome.value as List<FindHub.Device>)
        }
        val device = FindHub.match(item, devices.filter { it.ringable })
            ?: return FindHubOutput.NoSuchDevice(item, devices.filter { it.ringable })
        return when (FindHub.ring(device, start)) {
            is Outcome.Done -> if (start) FindHubOutput.Ringing(device) else FindHubOutput.StoppedRinging(device)
            Outcome.NoShizuku -> FindHubOutput.NeedsShizuku
            is Outcome.Failed -> FindHubOutput.Failed
        }
    }

    // "my keys" and "keys" are the same thing to Find Hub
    private fun clean(item: String?) = item?.trim()
        ?.replace(Regex("^(my|the|mera|meri|mere)\\s+", RegexOption.IGNORE_CASE), "")
        ?.takeIf { it.isNotEmpty() }
}
