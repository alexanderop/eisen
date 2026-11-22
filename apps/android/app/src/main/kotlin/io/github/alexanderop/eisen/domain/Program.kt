package io.github.alexanderop.eisen.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

@Serializable
data class Slot(
    val exerciseId: String,
    val sets: Int,
    /** null = as many reps as possible. */
    val reps: Int?,
)

/** A workout the lifter has set up: a name and the exercises to do. */
@Serializable
data class Routine(val id: String, val name: String, val slots: List<Slot>)

const val MAX_SETS = 10
const val MAX_REPS = 30

fun defaultRoutines(): List<Routine> = listOf(
    Routine(
        "A", "Workout A",
        listOf(Slot("hackSquat", 5, 5), Slot("bench", 5, 5), Slot("row", 5, 5), Slot("dips", 3, null)),
    ),
    Routine(
        "B", "Workout B",
        listOf(Slot("hackSquat", 5, 5), Slot("ohp", 5, 5), Slot("deadlift", 5, 5), Slot("pullups", 3, null)),
    ),
)

/** Sets and reps a newly added exercise starts with. */
fun defaultSlot(exercise: Exercise): Slot = when (exercise.equipment) {
    Equipment.BODYWEIGHT -> Slot(exercise.id, 3, null)
    Equipment.WEIGHT -> Slot(exercise.id, 3, 10)
    else -> Slot(exercise.id, 5, 5)
}

class UnitDefaults(
    val bar: Double,
    /** Smallest jump the bar can make: two of the smallest plates. */
    val step: Double,
    val plates: List<Double>,
)

private val KG_DEFAULTS = UnitDefaults(20.0, 2.5, listOf(20.0, 15.0, 10.0, 5.0, 2.5, 1.25))
private val LB_DEFAULTS = UnitDefaults(45.0, 5.0, listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5))

fun unitDefaults(unit: WeightUnit): UnitDefaults = if (unit == WeightUnit.KG) KG_DEFAULTS else LB_DEFAULTS

private fun increments(unit: WeightUnit): List<Double> =
    if (unit == WeightUnit.KG) listOf(0.5, 1.0, 1.25, 2.0, 2.5, 5.0, 7.5, 10.0) else listOf(2.5, 5.0, 10.0, 15.0, 20.0)

/**
 * Smallest weight change: plate pairs on a bar, finer for dumbbells and weight stacks.
 * Weight added to a bodyweight exercise is a dumbbell or a plate on a belt, so it moves like a dumbbell.
 */
fun stepFor(equipment: Equipment, unit: WeightUnit): Double =
    if (equipment == Equipment.WEIGHT || equipment == Equipment.BODYWEIGHT) (if (unit == WeightUnit.KG) 1.0 else 2.5)
    else unitDefaults(unit).step

/** Warmup weights on a machine use whole plates: nobody loads 1.25s onto a sled to warm up. */
fun warmupStep(equipment: Equipment, unit: WeightUnit): Double {
    val step = unitDefaults(unit).step
    return if (equipment == Equipment.MACHINE) step * 2 else step
}

fun incrementStep(unit: WeightUnit): Double = if (unit == WeightUnit.KG) 0.5 else 2.5

fun isPlateLoaded(equipment: Equipment): Boolean = equipment == Equipment.BARBELL || equipment == Equipment.MACHINE

/** Weight that is on the exercise before any plates are loaded. */
fun emptyWeight(equipment: Equipment, bar: Double): Double = if (equipment == Equipment.BARBELL) bar else 0.0

const val DELOAD_AFTER_FAILS = 3
const val DELOAD_FACTOR = 0.9

@Serializable
data class ExerciseProgress(
    val weight: Double,
    /** Workouts in a row where this weight was not completed. */
    val fails: Int,
    /** Weight a fully completed workout adds. */
    val increment: Double,
)

typealias Progress = Map<String, ExerciseProgress>

private fun nearest(value: Double, options: List<Double>): Double =
    options.reduce { best, option -> if (abs(option - value) < abs(best - value)) option else best }

private fun convertWeight(weight: Double, equipment: Equipment, from: WeightUnit, to: WeightUnit): Double {
    // A bodyweight exercise counts the weight added on top. 0 means none and must not become a weight.
    if (equipment == Equipment.BODYWEIGHT && weight == 0.0) return 0.0
    val step = stepFor(equipment, to)
    return max(step, roundTo(convert(weight, from, to), step))
}

fun startProgress(exercise: Exercise, unit: WeightUnit) = ExerciseProgress(
    weight = convertWeight(exercise.start, exercise.equipment, WeightUnit.KG, unit),
    fails = 0,
    increment = nearest(convert(exercise.increment, WeightUnit.KG, unit), increments(unit)),
)

/** Added weight on a bodyweight exercise never goes up by itself, so its increment stays 0. */
fun convertProgress(entry: ExerciseProgress, equipment: Equipment, from: WeightUnit, to: WeightUnit) = ExerciseProgress(
    weight = convertWeight(entry.weight, equipment, from, to),
    fails = entry.fails,
    increment = if (equipment == Equipment.BODYWEIGHT) entry.increment else nearest(convert(entry.increment, from, to), increments(to)),
)

@Serializable
data class ExerciseLog(
    val id: String,
    /** Name and equipment are copied in, so the log still reads right after the exercise is deleted. */
    val name: String,
    val equipment: Equipment,
    /** For bodyweight exercises the weight added on top (dip belt, vest), 0 for none. */
    val weight: Double,
    /** null = as many reps as possible. */
    val targetReps: Int?,
    /** null = set not logged yet. */
    val sets: List<Int?>,
)

@Serializable
data class Workout(
    val id: String,
    val routineId: String,
    val name: String,
    val unit: WeightUnit,
    val startedAt: Long,
    val finishedAt: Long?,
    val exercises: List<ExerciseLog>,
)

enum class Outcome { SUCCESS, FAIL, SKIPPED }

enum class ChangeKind { UP, REPEAT, DELOAD }

data class Change(val id: String, val name: String, val kind: ChangeKind, val from: Double, val to: Double)

fun createLog(exercise: Exercise, slot: Slot, progress: Progress, unit: WeightUnit) = ExerciseLog(
    id = exercise.id,
    name = exercise.name,
    equipment = exercise.equipment,
    // A bodyweight exercise has an entry only while the lifter adds weight to it.
    weight = if (exercise.equipment == Equipment.BODYWEIGHT) progress[exercise.id]?.weight ?: 0.0
    else (progress[exercise.id] ?: startProgress(exercise, unit)).weight,
    targetReps = slot.reps,
    sets = List(slot.sets) { null },
)

/** Slots whose exercise no longer exists are left out. */
fun createWorkout(
    routine: Routine,
    find: (String) -> Exercise?,
    progress: Progress,
    unit: WeightUnit,
    now: Long,
    id: String,
) = Workout(
    id = id,
    routineId = routine.id,
    name = routine.name,
    unit = unit,
    startedAt = now,
    finishedAt = null,
    exercises = routine.slots.mapNotNull { slot -> find(slot.exerciseId)?.let { createLog(it, slot, progress, unit) } },
)

/** One tap on a set: empty -> all reps -> one fewer each tap -> 0 -> empty. */
fun nextReps(current: Int?, target: Int): Int? = when (current) {
    null -> target
    0 -> null
    else -> current - 1
}

/** A max-reps exercise succeeds as soon as any set is logged: it has no target to miss. */
fun outcome(log: ExerciseLog): Outcome = when {
    log.sets.all { it == null } -> Outcome.SKIPPED
    log.targetReps == null -> Outcome.SUCCESS
    log.sets.all { it == log.targetReps } -> Outcome.SUCCESS
    else -> Outcome.FAIL
}

fun isComplete(workout: Workout): Boolean = workout.exercises.all { log -> log.sets.all { it != null } }

/** Workouts rotate in list order. A workout that was deleted meanwhile leaves the choice alone. */
fun nextRoutineId(routines: List<Routine>, finishedId: String, current: String): String {
    val index = routines.indexOfFirst { it.id == finishedId }
    return if (index == -1) current else routines[(index + 1) % routines.size].id
}

fun totalReps(log: ExerciseLog): Int = log.sets.sumOf { it ?: 0 }

class Progression(val progress: Progress, val changes: List<Change>)

/**
 * Progressive overload: all reps done -> add weight next time.
 * Missed reps -> repeat the weight. Three misses in a row -> drop 10%.
 * Bodyweight and max-reps exercises have no target weight to progress. Weight added to a
 * bodyweight exercise only changes when the lifter changes it.
 */
fun applyProgression(progress: Progress, workout: Workout, bar: Double): Progression {
    val next = progress.toMutableMap()
    val changes = mutableListOf<Change>()

    for (log in workout.exercises) {
        val entry = progress[log.id]
        if (entry == null || log.equipment == Equipment.BODYWEIGHT || log.targetReps == null) continue
        val result = outcome(log)
        if (result == Outcome.SKIPPED) continue

        if (result == Outcome.SUCCESS) {
            val to = log.weight + entry.increment
            next[log.id] = entry.copy(weight = to, fails = 0)
            changes += Change(log.id, log.name, ChangeKind.UP, log.weight, to)
            continue
        }

        val fails = entry.fails + 1
        if (fails >= DELOAD_AFTER_FAILS) {
            val step = stepFor(log.equipment, workout.unit)
            val floor = max(step, emptyWeight(log.equipment, bar))
            val to = max(floor, roundTo(log.weight * DELOAD_FACTOR, step))
            next[log.id] = entry.copy(weight = to, fails = 0)
            changes += Change(log.id, log.name, ChangeKind.DELOAD, log.weight, to)
        } else {
            next[log.id] = entry.copy(weight = log.weight, fails = fails)
            changes += Change(log.id, log.name, ChangeKind.REPEAT, log.weight, log.weight)
        }
    }

    return Progression(next, changes)
}

/** How heavy a log is, as text: "80kg", "BW", or "BW + 10kg" with weight added to a bodyweight exercise. */
fun loadText(bodyweight: Boolean, weight: Double, unit: WeightUnit, label: String = "BW"): String = when {
    !bodyweight -> formatWeight(weight, unit)
    weight > 0 -> "$label + ${formatWeight(weight, unit)}"
    else -> label
}

fun loadText(log: ExerciseLog, unit: WeightUnit): String = loadText(log.equipment == Equipment.BODYWEIGHT, log.weight, unit)
