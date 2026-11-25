package io.github.alexanderop.eisen.feature.workout

import io.github.alexanderop.eisen.core.MAX_WEIGHT
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.Exercise
import io.github.alexanderop.eisen.core.WeightUnit
import io.github.alexanderop.eisen.core.convert
import io.github.alexanderop.eisen.core.holdText
import io.github.alexanderop.eisen.core.loadText
import io.github.alexanderop.eisen.core.roundTo
import io.github.alexanderop.eisen.core.stepFor
import io.github.alexanderop.eisen.core.unitDefaults

@Serializable
data class Slot(
    val exerciseId: String,
    val sets: Int,
    /** null = as many reps as possible. For a timed exercise the hold in seconds, null = as long as possible. */
    val reps: Int?,
)

/** A workout the lifter has set up: a name and the exercises to do. */
@Serializable
data class Routine(val id: String, val name: String, val slots: List<Slot>)

const val MAX_SETS = 10
const val MAX_REPS = 30

/** The longest hold a timed exercise can aim for, in seconds. */
const val MAX_HOLD_SECONDS = 600
const val HOLD_STEP_SECONDS = 5
const val MIN_HOLD_SECONDS = 5
const val DEFAULT_HOLD_SECONDS = 60

/** "Get ready": time to put the phone down and get into position before a hold starts. */
const val HOLD_LEAD_IN_SECONDS = 5

/** A hold as long as possible stops itself here. */
const val MAX_HELD_SECONDS = 3600

/** A logged set as the lifter reads it: "5" reps, or "1:00" held for a timed exercise. */
fun setText(count: Int, timed: Boolean): String = if (timed) holdText(count) else count.toString()

/** A target: "5" reps, "1:00" to hold, or "max" for as many reps or as long as possible. */
fun targetText(target: Int?, timed: Boolean): String = target?.let { setText(it, timed) } ?: "max"

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
fun defaultSlot(exercise: Exercise): Slot = when {
    exercise.timed -> Slot(exercise.id, 3, DEFAULT_HOLD_SECONDS)
    else -> when (exercise.equipment) {
        Equipment.BODYWEIGHT -> Slot(exercise.id, 3, null)
        Equipment.WEIGHT -> Slot(exercise.id, 3, 10)
        else -> Slot(exercise.id, 5, 5)
    }
}

private fun increments(unit: WeightUnit): List<Double> =
    if (unit == WeightUnit.KG) listOf(0.5, 1.0, 1.25, 2.0, 2.5, 5.0, 7.5, 10.0) else listOf(2.5, 5.0, 10.0, 15.0, 20.0)

/** The heaviest weight the rest screen's weight wheel goes up to, at least. */
fun wheelTop(unit: WeightUnit): Double = if (unit == WeightUnit.KG) 400.0 else 900.0

/**
 * The weights the rest screen's wheel offers, lightest first: from the empty bar (one step for a
 * machine or a weight, nothing added for a bodyweight exercise) up to [wheelTop], one step of
 * [stepFor] apart. [current] is always among them, also when it is off that grid or above the top.
 */
fun weightChoices(equipment: Equipment, unit: WeightUnit, bar: Double, current: Double): List<Double> {
    val step = stepFor(equipment, unit)
    val lightest = when (equipment) {
        Equipment.BARBELL -> bar
        Equipment.BODYWEIGHT -> 0.0
        else -> step
    }
    // Up to the top of the wheel or the weight it shows, but never past MAX_WEIGHT: a saved weight above
    // it (from an older build) is one more choice at the end, not thousands of rows.
    val count = ((maxOf(wheelTop(unit), current).coerceAtMost(MAX_WEIGHT) - lightest) / step + 1e-9).toInt()
    // Rounded to the cent, so 20 + 3 × 2.5 is 27.5 and not 27.499999.
    val grid = (0..count).map { Math.round((lightest + it * step) * 100) / 100.0 }
    return if (current in grid) grid else (grid + current).sorted()
}

/**
 * The most reps a logged set holds. The reps question stops here, the machine refuses more, and the rest
 * screen's wheel offers no more. A count above it in a saved file (QA build 19 saved 9999999) still loads
 * and shows, as one more choice at the end of the wheel.
 */
const val MAX_LOGGED_REPS = 100

/** The most weight added after a workout with all reps, in the lifter's unit. */
const val MAX_INCREMENT = 50.0

/** The heaviest bar, in the lifter's unit. */
const val MAX_BAR = 50.0

/** The longest rest the settings take, in seconds. */
const val MAX_REST_SECONDS = 3600

/**
 * The counts the rest screen's reps wheel offers: 0 up to the target, or up to [MAX_LOGGED_REPS] for a
 * max-reps set, and [current] after them when it is more. Never more than one past the limit.
 */
fun repsChoices(target: Int?, current: Int): List<Int> {
    val top = minOf(target ?: MAX_LOGGED_REPS, MAX_LOGGED_REPS)
    return (0..top).toList() + listOfNotNull(current.takeIf { it > top })
}

/** [slot] within its limits: 1 to [MAX_SETS] sets, 1 to [MAX_REPS] reps, or a hold of [MIN_HOLD_SECONDS] to [MAX_HOLD_SECONDS]. */
fun slotWithin(slot: Slot, timed: Boolean): Slot = slot.copy(
    sets = slot.sets.coerceIn(1, MAX_SETS),
    reps = slot.reps?.let { if (timed) it.coerceIn(MIN_HOLD_SECONDS, MAX_HOLD_SECONDS) else it.coerceIn(1, MAX_REPS) },
)

/** A logged [log] within its limits: weights up to [MAX_WEIGHT], counts up to [MAX_LOGGED_REPS] (a hold: [MAX_HELD_SECONDS]). */
fun logWithin(log: ExerciseLog): ExerciseLog {
    val most = if (log.timed) MAX_HELD_SECONDS else maxOf(MAX_LOGGED_REPS, log.targetReps ?: 0)
    return log.copy(weight = log.weight.coerceIn(0.0, MAX_WEIGHT), sets = log.sets.map { it?.coerceIn(0, most) })
}

/** Warmup weights on a machine use whole plates: nobody loads 1.25s onto a sled to warm up. */
fun warmupStep(equipment: Equipment, unit: WeightUnit): Double {
    val step = unitDefaults(unit).step
    return if (equipment == Equipment.MACHINE) step * 2 else step
}

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

@OptIn(ExperimentalSerializationApi::class)
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
    /** Copied from the exercise: [targetReps] and [sets] count seconds held. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val timed: Boolean = false,
    /** Reps in reserve per set, parallel to [sets], see [rirOf]. Not saved while no set is rated. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val rir: List<Int?> = emptyList(),
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

/** [boosted]: the weight went up twice the increment, because the last set had reps to spare. */
data class Change(val id: String, val name: String, val kind: ChangeKind, val from: Double, val to: Double, val boosted: Boolean = false)

fun createLog(exercise: Exercise, slot: Slot, progress: Progress, unit: WeightUnit) = ExerciseLog(
    id = exercise.id,
    name = exercise.name,
    equipment = exercise.equipment,
    // A bodyweight exercise has an entry only while the lifter adds weight to it.
    weight = if (exercise.equipment == Equipment.BODYWEIGHT) progress[exercise.id]?.weight ?: 0.0
    else (progress[exercise.id] ?: startProgress(exercise, unit)).weight,
    targetReps = slot.reps,
    sets = List(slot.sets) { null },
    timed = exercise.timed,
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

/** One tap on a set: empty -> all reps -> one fewer each tap -> 0 -> empty. Not for timed sets, which a timer logs. */
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

/** The longest single hold of a timed exercise in seconds, 0 when none is logged. */
fun longestHold(log: ExerciseLog): Int = log.sets.maxOfOrNull { it ?: 0 } ?: 0

class Progression(val progress: Progress, val changes: List<Change>)

/**
 * Progressive overload: all reps done -> add weight next time.
 * Missed reps -> repeat the weight. Three misses in a row -> drop 10%.
 * Bodyweight and max-reps exercises have no target weight to progress. Weight added to a
 * bodyweight exercise only changes when the lifter changes it. A timed exercise with a weight
 * (a farmer's carry) goes up when every hold lasted its time.
 * With [trackRir], a last set rated [RIR_BOOST_FROM] or more in reserve makes the jump [RIR_BOOST_FACTOR] times
 * as big. It only ever adds: a missed target stays a miss however the sets were rated.
 */
fun applyProgression(progress: Progress, workout: Workout, bar: Double, trackRir: Boolean = true): Progression {
    val next = progress.toMutableMap()
    val changes = mutableListOf<Change>()

    for (log in workout.exercises) {
        val entry = progress[log.id]
        if (entry == null || log.equipment == Equipment.BODYWEIGHT || log.targetReps == null) continue
        val result = outcome(log)
        if (result == Outcome.SKIPPED) continue

        if (result == Outcome.SUCCESS) {
            val boosted = trackRir && boosts(log)
            val to = log.weight + entry.increment * (if (boosted) RIR_BOOST_FACTOR else 1)
            next[log.id] = entry.copy(weight = to, fails = 0)
            changes += Change(log.id, log.name, ChangeKind.UP, log.weight, to, boosted)
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

fun loadText(log: ExerciseLog, unit: WeightUnit): String = loadText(log.equipment == Equipment.BODYWEIGHT, log.weight, unit)

fun durationMinutes(workout: Workout): Long? {
    val finished = workout.finishedAt ?: return null
    return max(1, Math.round((finished - workout.startedAt) / 60000.0))
}
