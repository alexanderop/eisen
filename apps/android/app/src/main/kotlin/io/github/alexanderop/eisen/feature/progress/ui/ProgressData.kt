package io.github.alexanderop.eisen.feature.progress.ui

import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.Exercise
import io.github.alexanderop.eisen.feature.workout.ExerciseLog
import io.github.alexanderop.eisen.feature.workout.Outcome
import io.github.alexanderop.eisen.feature.workout.Routine
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.feature.workout.Workout
import io.github.alexanderop.eisen.core.convert
import io.github.alexanderop.eisen.feature.workout.longestHold
import io.github.alexanderop.eisen.feature.workout.outcome
import io.github.alexanderop.eisen.feature.workout.recordIds
import io.github.alexanderop.eisen.feature.workout.recordOf
import io.github.alexanderop.eisen.core.roundTo
import io.github.alexanderop.eisen.core.stepFor
import io.github.alexanderop.eisen.feature.workout.totalReps

/** [timed]: held for time. A timed bodyweight exercise charts its longest hold. */
class Option(val id: String, val name: String, val equipment: Equipment, val timed: Boolean = false)

/** How far back both charts look. */
enum class Range(val label: String, val days: Long?) {
    THREE_MONTHS("3 months", 91),
    YEAR("1 year", 365),
    ALL("All", null),
}

/** One logged exercise in the range. `record` says it beat everything before it. */
class Entry(val time: Long, val unit: WeightUnit, val log: ExerciseLog, val record: Boolean)

/** A workout of one exercise, with what happened in it. */
class Point(
    val time: Long,
    val value: Double,
    val log: ExerciseLog,
    val record: Boolean,
    /** The record that still stands. */
    val best: Boolean,
    val missed: Boolean,
    val deload: Boolean,
)

/**
 * Exercises in the current workouts first, then ones that only exist in the history.
 * [exercises] is every known exercise, built-in first, as `exercises(model)` gives them.
 */
fun optionsOf(routines: List<Routine>, history: List<Workout>, exercises: List<Exercise>): List<Option> {
    // The first exercise with an id wins, like the lookup the rest of the app uses.
    val known = LinkedHashMap<String, Exercise>()
    for (exercise in exercises) known.putIfAbsent(exercise.id, exercise)
    val found = LinkedHashMap<String, Option>()
    for (routine in routines) {
        for (slot in routine.slots) {
            val exercise = known[slot.exerciseId] ?: continue
            found[exercise.id] = Option(exercise.id, exercise.name, exercise.equipment, exercise.timed)
        }
    }
    for (workout in history) {
        for (log in workout.exercises) {
            if (log.id in found) continue
            val exercise = known[log.id]
            found[log.id] = if (exercise != null) {
                Option(exercise.id, exercise.name, exercise.equipment, exercise.timed)
            } else {
                Option(log.id, log.name, log.equipment, log.timed)
            }
        }
    }
    return found.values.toList()
}

/** Weights logged in the other unit are rounded to what the plates can load, not 176.37lb. */
fun inCurrentUnit(weight: Double, unit: WeightUnit, equipment: Equipment, current: WeightUnit): Double =
    if (unit == current) weight else roundTo(convert(weight, unit, current), stepFor(equipment, current))

/** Every logged exercise in the range, oldest first. Records count against the whole history. */
fun entriesOf(history: List<Workout>, range: Range, now: Long): List<Entry> {
    val cutoff = range.days?.let { now - it * 24 * 60 * 60 * 1000 } ?: Long.MIN_VALUE
    return history.flatMapIndexed { index, workout ->
        if (workout.startedAt < cutoff) return@flatMapIndexed emptyList()
        val records = recordIds(history, index)
        workout.exercises
            .filter { log -> log.sets.any { it != null } }
            .map { log -> Entry(workout.startedAt, workout.unit, log, log.id in records) }
    }.asReversed()
}

/** What a workout of an exercise charts: the weight, the total reps of a bodyweight one, the longest hold of a timed one. */
fun valueOf(log: ExerciseLog, unit: WeightUnit, current: WeightUnit): Double = when {
    log.equipment != Equipment.BODYWEIGHT -> inCurrentUnit(log.weight, unit, log.equipment, current)
    log.timed -> longestHold(log).toDouble()
    else -> totalReps(log).toDouble()
}

fun valueOf(entry: Entry, current: WeightUnit): Double = valueOf(entry.log, entry.unit, current)

/**
 * Reps were missed at this weight. A plain hold has no weight to repeat: it only counts as missed when
 * a hold was cut short, not when one lasted longer than its target or was left out.
 */
private fun missedIn(log: ExerciseLog): Boolean {
    val target = log.targetReps
    if (!log.timed || log.equipment != Equipment.BODYWEIGHT) return outcome(log) == Outcome.FAIL
    return target != null && log.sets.any { it != null && it < target }
}

/**
 * `record` says the workout beat everything before it. With steady progress that is every
 * workout, so the chart only marks `best`: the record that still stands.
 */
fun pointsOf(id: String, entries: List<Entry>, history: List<Workout>, current: WeightUnit): List<Point> {
    val own = entries.filter { it.log.id == id }
    // The same log object, not an equal one: two logs of a workout can hold the same numbers.
    val standing = recordOf(history, id)?.log
    return own.mapIndexed { index, entry ->
        val value = valueOf(entry, current)
        Point(
            time = entry.time,
            value = value,
            log = entry.log,
            record = entry.record,
            best = entry.log === standing,
            missed = missedIn(entry.log),
            deload = entry.log.equipment != Equipment.BODYWEIGHT && index > 0 && value < valueOf(own[index - 1], current),
        )
    }
}
