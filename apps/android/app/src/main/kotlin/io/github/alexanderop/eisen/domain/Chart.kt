package io.github.alexanderop.eisen.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Round axis values that cover low to high, about `target` of them.
 * The first is at or below low and the last at or above high, so no mark touches the frame.
 */
fun niceTicks(low: Double, high: Double, target: Int = 3): List<Double> {
    val span = if (high - low != 0.0) high - low else if (abs(high) != 0.0) abs(high) else 1.0
    val rough = span / target
    val power = 10.0.pow(floor(log10(rough)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * power }.first { it >= rough }
    var first = floor(low / step) * step
    var last = ceil(high / step) * step
    if (first == last) {
        first -= step
        last += step
    }
    val count = ((last - first) / step).roundToInt()
    return List(count + 1) { index -> Math.round((first + index * step) * 1e6) / 1e6 }
}

private fun dayOf(time: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

private fun LocalDate.millis(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

/** Monday 00:00 of the week the moment is in, in local time. */
fun weekStart(time: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    dayOf(time, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).millis(zone)

class CalendarDay(
    /** Local midnight. */
    val time: Long,
    val today: Boolean,
    val future: Boolean,
    val workouts: List<Workout>,
    /** Interval timers done that day. A day with one counts as a training day. */
    val timers: List<TimerSession> = emptyList(),
) {
    val trained: Boolean get() = workouts.isNotEmpty() || timers.isNotEmpty()
}

/** The last `weeks` weeks as columns of seven days, Monday first, ending with the current week. */
fun calendarWeeks(
    history: List<Workout>,
    now: Long,
    weeks: Int,
    zone: ZoneId = ZoneId.systemDefault(),
    timers: List<TimerSession> = emptyList(),
): List<List<CalendarDay>> {
    val byDay = history.groupBy { dayOf(it.startedAt, zone) }
    val timersByDay = timers.groupBy { dayOf(it.startedAt, zone) }
    val today = dayOf(now, zone)
    val first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((weeks - 1).toLong())
    return List(weeks) { week ->
        List(7) { day ->
            val date = first.plusDays((week * 7 + day).toLong())
            CalendarDay(date.millis(zone), date == today, date > today, byDay[date] ?: emptyList(), timersByDay[date] ?: emptyList())
        }
    }
}

/**
 * Weeks in a row with at least one workout, counted back from now.
 * A current week without a workout yet does not break the streak: it is not over.
 */
fun weekStreak(times: List<Long>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
    fun monday(time: Long): LocalDate = dayOf(time, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val trained = times.map(::monday).toSet()
    var week = monday(now)
    if (week !in trained) week = week.minusWeeks(1)
    var streak = 0
    while (week in trained) {
        streak++
        week = week.minusWeeks(1)
    }
    return streak
}
