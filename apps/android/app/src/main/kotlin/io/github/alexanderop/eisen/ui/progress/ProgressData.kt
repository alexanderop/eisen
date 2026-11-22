package io.github.alexanderop.eisen.ui.progress

import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.domain.AppState
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.ExerciseLog
import io.github.alexanderop.eisen.domain.Outcome
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.convert
import io.github.alexanderop.eisen.domain.outcome
import io.github.alexanderop.eisen.domain.recordIds
import io.github.alexanderop.eisen.domain.recordOf
import io.github.alexanderop.eisen.domain.roundTo
import io.github.alexanderop.eisen.domain.stepFor
import io.github.alexanderop.eisen.domain.totalReps

class Option(val id: String, val name: String, val equipment: Equipment)

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

/** Exercises in the current workouts first, then ones that only exist in the history. */
fun optionsOf(state: AppState): List<Option> {
    val found = LinkedHashMap<String, Option>()
    for (routine in state.routines) {
        for (slot in routine.slots) {
            val exercise = Store.findExercise(slot.exerciseId) ?: continue
            found[exercise.id] = Option(exercise.id, exercise.name, exercise.equipment)
        }
    }
    for (workout in state.history) {
        for (log in workout.exercises) {
            if (log.id in found) continue
            val exercise = Store.findExercise(log.id)
            found[log.id] = if (exercise != null) Option(exercise.id, exercise.name, exercise.equipment) else Option(log.id, log.name, log.equipment)
        }
    }
    return found.values.toList()
}

/** Weights logged in the other unit are rounded to what the plates can load, not 176.37lb. */
fun inCurrentUnit(weight: Double, unit: WeightUnit, equipment: Equipment, current: WeightUnit): Double =
    if (unit == current) weight else roundTo(convert(weight, unit, current), stepFor(equipment, current))

/** Every logged exercise in the range, oldest first. Records count against the whole history. */
fun entriesOf(state: AppState, range: Range, now: Long): List<Entry> {
    val cutoff = range.days?.let { now - it * 24 * 60 * 60 * 1000 } ?: Long.MIN_VALUE
    return state.history.flatMapIndexed { index, workout ->
        if (workout.startedAt < cutoff) return@flatMapIndexed emptyList()
        val records = recordIds(state.history, index)
        workout.exercises
            .filter { log -> log.sets.any { it != null } }
            .map { log -> Entry(workout.startedAt, workout.unit, log, log.id in records) }
    }.asReversed()
}

fun valueOf(entry: Entry, current: WeightUnit): Double =
    if (entry.log.equipment == Equipment.BODYWEIGHT) {
        totalReps(entry.log).toDouble()
    } else {
        inCurrentUnit(entry.log.weight, entry.unit, entry.log.equipment, current)
    }

/**
 * `record` says the workout beat everything before it. With steady progress that is every
 * workout, so the chart only marks `best`: the record that still stands.
 */
fun pointsOf(id: String, entries: List<Entry>, state: AppState): List<Point> {
    val current = state.settings.unit
    val own = entries.filter { it.log.id == id }
    // The same log object, not an equal one: two logs of a workout can hold the same numbers.
    val standing = recordOf(state.history, id)?.log
    return own.mapIndexed { index, entry ->
        val value = valueOf(entry, current)
        Point(
            time = entry.time,
            value = value,
            log = entry.log,
            record = entry.record,
            best = entry.log === standing,
            missed = outcome(entry.log) == Outcome.FAIL,
            deload = entry.log.equipment != Equipment.BODYWEIGHT && index > 0 && value < valueOf(own[index - 1], current),
        )
    }
}
