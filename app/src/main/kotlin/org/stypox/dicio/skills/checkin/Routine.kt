package org.stypox.dicio.skills.checkin

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

const val STEP_GOAL = 7_000L

/** When to walk next, as the check-in suggests it. */
enum class WalkNudge { AfterLunch, Now, AtTea, OnTheWayHome, Anytime }

/**
 * The owner's office day: leave home 10:30, at the office by 10:55, lunch at 2, tea at 5:30,
 * leave around 8:30. Walks fit the breaks, so the nudge points at the next one.
 */
object Routine {
    val LUNCH: LocalTime = LocalTime.of(14, 0)
    val TEA: LocalTime = LocalTime.of(17, 30)
    private val LEAVE_HOME = LocalTime.of(10, 30)
    private val AFTER_LUNCH = LocalTime.of(14, 45)
    private val AFTER_TEA = LocalTime.of(18, 0)
    private val LEAVE_OFFICE = LocalTime.of(20, 30)
    private val HOME = LocalTime.of(21, 15)

    private val WORKDAYS = DayOfWeek.MONDAY..DayOfWeek.FRIDAY

    // steps a goal day has by each point: the commute, the lunch walk, the tea walk, home
    private val OFFICE_CURVE = listOf(
        LocalTime.MIN to 0L, LEAVE_HOME to 1_000L, LocalTime.of(10, 55) to 2_500L, LUNCH to 3_000L,
        AFTER_LUNCH to 4_500L, TEA to 5_000L, AFTER_TEA to 6_000L, LEAVE_OFFICE to 6_000L, HOME to STEP_GOAL,
    )
    private val FREE_DAY_CURVE = listOf(
        LocalTime.MIN to 0L, LocalTime.of(9, 0) to 0L, LocalTime.of(21, 0) to STEP_GOAL,
    )

    fun isWorkday(at: LocalDateTime) = at.dayOfWeek in WORKDAYS

    /** Steps a [STEP_GOAL] day would have by [at]. */
    fun expectedSteps(at: LocalDateTime): Long {
        val curve = if (isWorkday(at)) OFFICE_CURVE else FREE_DAY_CURVE
        val time = at.toLocalTime()
        val (from, to) = curve.zipWithNext().firstOrNull { (_, next) -> time < next.first }
            ?: return curve.last().second
        val span = to.first.toSecondOfDay() - from.first.toSecondOfDay()
        val done = time.toSecondOfDay() - from.first.toSecondOfDay()
        return from.second + (to.second - from.second) * done / span
    }

    fun isBehind(steps: Long, at: LocalDateTime) = steps < expectedSteps(at) * 7 / 10

    /** Null when no walk is worth suggesting: on track, at home in the morning, or late at night. */
    fun nudge(steps: Long, at: LocalDateTime): WalkNudge? {
        if (!isBehind(steps, at)) return null
        val time = at.toLocalTime()
        if (!isWorkday(at)) return if (time.hour in 10..20) WalkNudge.Anytime else null
        return when {
            time < LEAVE_HOME || time >= HOME -> null
            time < LUNCH -> WalkNudge.AfterLunch
            time < AFTER_LUNCH -> WalkNudge.Now
            time < TEA -> WalkNudge.AtTea
            time < AFTER_TEA -> WalkNudge.Now
            else -> WalkNudge.OnTheWayHome
        }
    }
}
