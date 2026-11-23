package io.github.alexanderop.eisen.health

import androidx.health.connect.client.records.ExerciseSegment
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.Workout
import io.github.alexanderop.eisen.domain.loadText
import io.github.alexanderop.eisen.domain.repsText
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** One exercise of the session, as Health Connect wants it: a time span, a type and the reps in it (0 for a hold). */
data class SegmentFields(val start: Instant, val end: Instant, val type: Int, val repetitions: Int)

/** Everything an exercise session record is built from. Kept free of Android so it can be unit tested. */
data class SessionFields(
    val start: Instant,
    val startOffset: ZoneOffset,
    val end: Instant,
    val endOffset: ZoneOffset,
    val title: String,
    val notes: String,
    val segments: List<SegmentFields>,
)

/**
 * Health Connect knows a few dozen strength exercises. Only exercises that are plainly the same
 * movement are listed. Everything else, the lifter's own exercises too, gets no segment at all:
 * a wrong exercise in another app is worse than a missing one.
 */
private val SEGMENT_TYPES: Map<String, Int> = mapOf(
    "squat" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT,
    "frontSquat" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT,
    "gobletSquat" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT,
    "legPress" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_PRESS,
    "deadlift" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT,
    "romanianDeadlift" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT,
    "hipThrust" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST,
    "lunge" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE,
    "legExtension" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_EXTENSION,
    "legCurl" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL,
    "bench" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
    "inclineBench" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
    "closeGripBench" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
    "dumbbellBench" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
    "dumbbellRow" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW,
    "latPulldown" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN,
    "egymLatPulldown" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN,
    "pullups" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP,
    "chinups" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP,
    "backExtension" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION,
    "ohp" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS,
    "dumbbellShoulderPress" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS,
    "egymShoulderPress" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS,
    "lateralRaise" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE,
    "barbellCurl" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL,
    "dumbbellCurl" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL,
    "hammerCurl" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL,
    "overheadTricepsExtension" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION,
    "hangingLegRaise" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_RAISE,
    "situps" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SIT_UP,
    "cableCrunch" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH,
    "egymAbdominalCrunch" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH,
    "plank" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK,
    "sidePlank" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK,
)

fun segmentTypeFor(exerciseId: String): Int? = SEGMENT_TYPES[exerciseId]

/**
 * The workout as one plain-text line per exercise, for the notes field. Health apps show notes as
 * typed, so this is the share text without the Markdown. Exercises without a logged set are left out.
 */
fun workoutNotes(workout: Workout): String = workout.exercises
    .filter { log -> log.sets.any { it != null } }
    .joinToString("\n") { log ->
        // Spelled out: health apps show the notes to people who never saw the app's "BW".
        val load = loadText(log.equipment == Equipment.BODYWEIGHT, log.weight, workout.unit, label = "bodyweight")
        "${log.name} $load: ${repsText(log.sets, log.targetReps, log.timed)}"
    }

/**
 * The record for a finished workout, or null when there is nothing Health Connect would accept:
 * a workout that is still running, or one whose end is not after its start (a hand-edited backup).
 *
 * Eisen does not keep when each exercise started, only when the workout did. The segments split
 * the session into equal parts in the order the exercises were done, so the reps are right and the
 * times are an estimate. Health Connect needs every segment to have a time span, so there is no
 * way to send reps without one. A hold has no reps: its seconds are in the notes, never counted as repetitions.
 */
fun sessionFields(workout: Workout, zone: ZoneId): SessionFields? {
    val finishedAt = workout.finishedAt ?: return null
    if (finishedAt <= workout.startedAt) return null
    val start = Instant.ofEpochMilli(workout.startedAt)
    val end = Instant.ofEpochMilli(finishedAt)

    val done = workout.exercises.filter { log -> log.sets.any { it != null } }
    val slice = if (done.isEmpty()) 0L else (finishedAt - workout.startedAt) / done.size
    val segments = if (slice <= 0) emptyList() else done.mapIndexedNotNull { index, log ->
        val type = segmentTypeFor(log.id) ?: return@mapIndexedNotNull null
        val from = workout.startedAt + slice * index
        SegmentFields(
            start = Instant.ofEpochMilli(from),
            end = Instant.ofEpochMilli(from + slice),
            type = type,
            repetitions = if (log.timed) 0 else log.sets.sumOf { it ?: 0 },
        )
    }

    return SessionFields(
        start = start,
        startOffset = zone.rules.getOffset(start),
        end = end,
        endOffset = zone.rules.getOffset(end),
        title = workout.name,
        notes = workoutNotes(workout),
        segments = segments,
    )
}
