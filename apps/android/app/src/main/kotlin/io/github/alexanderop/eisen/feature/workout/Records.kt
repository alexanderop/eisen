package io.github.alexanderop.eisen.feature.workout

import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.core.convert

class Logged(val workout: Workout, val log: ExerciseLog)

private fun logsOf(history: List<Workout>, id: String): List<Logged> =
    history.flatMap { workout -> workout.exercises.filter { it.id == id }.map { Logged(workout, it) } }

/** The newest finished workout with a logged set of this exercise. History is newest first. */
fun lastLogged(history: List<Workout>, id: String): Logged? =
    logsOf(history, id).firstOrNull { entry -> entry.log.sets.any { it != null } }

/**
 * What a logged exercise counts as for records, comparable across units.
 * Bodyweight exercises count their total reps, timed ones their longest hold in seconds.
 * Weighted ones count the weight in kg, and only when every rep was done: a weight with missed
 * reps is not a record. A timed one with a weight counts the same way.
 */
fun recordValue(log: ExerciseLog, unit: WeightUnit): Double? {
    if (log.equipment == Equipment.BODYWEIGHT) {
        val value = if (log.timed) longestHold(log) else totalReps(log)
        return value.takeIf { it > 0 }?.toDouble()
    }
    return if (outcome(log) == Outcome.SUCCESS) convert(log.weight, unit, WeightUnit.KG) else null
}

/** Converting between units leaves rounding noise. A record has to beat it. */
private const val NOISE = 0.01

/** The log that holds the record of this exercise: the earliest one with the highest value. */
fun recordOf(history: List<Workout>, id: String): Logged? {
    var best: Logged? = null
    var bestValue = 0.0
    for (entry in logsOf(history, id).asReversed()) {
        val value = recordValue(entry.log, entry.workout.unit)
        if (value != null && value > bestValue + NOISE) {
            best = entry
            bestValue = value
        }
    }
    return best
}

/**
 * Ids of the exercises that set a record in the workout at this position of the history.
 * A first log has nothing to beat, so it is not a record.
 */
fun recordIds(history: List<Workout>, index: Int): Set<String> {
    val workout = history.getOrNull(index) ?: return emptySet()
    val earlier = history.drop(index + 1)
    val ids = mutableSetOf<String>()
    for (log in workout.exercises) {
        val value = recordValue(log, workout.unit) ?: continue
        val before = recordOf(earlier, log.id) ?: continue
        val beaten = recordValue(before.log, before.workout.unit) ?: 0.0
        if (value > beaten + NOISE) ids += log.id
    }
    return ids
}

/** A record a finished workout set: [log] of [workout] beat every earlier log of its exercise. */
class RecordEvent(val workout: Workout, val log: ExerciseLog)

/**
 * Every record of [history] (newest first, as saved), oldest first: what [recordIds] finds for each of its workouts,
 * in one pass instead of one per workout. A first log has nothing to beat, so it is not a record.
 */
fun recordEvents(history: List<Workout>): List<RecordEvent> {
    val best = HashMap<String, Double>()
    val events = mutableListOf<RecordEvent>()
    for (workout in history.asReversed()) {
        val values = workout.exercises.mapNotNull { log -> recordValue(log, workout.unit)?.let { log to it } }
        for ((log, value) in values) {
            val before = best[log.id] ?: continue
            if (value > before + NOISE) events += RecordEvent(workout, log)
        }
        // Against the workouts before it only, as recordIds compares: then this one counts for the next.
        for ((log, value) in values) {
            if (value > (best[log.id] ?: 0.0) + NOISE) best[log.id] = value
        }
    }
    return events
}
