package io.github.alexanderop.eisen.feature.health.platform

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.feature.timer.Activity
import io.github.alexanderop.eisen.feature.timer.TimerMode
import io.github.alexanderop.eisen.feature.timer.TimerOutcome
import io.github.alexanderop.eisen.feature.timer.TimerSession
import io.github.alexanderop.eisen.feature.timer.timerOutcome
import io.github.alexanderop.eisen.feature.workout.Workout
import io.github.alexanderop.eisen.core.loadText
import io.github.alexanderop.eisen.feature.workout.loadText
import io.github.alexanderop.eisen.feature.share.repsText
import io.github.alexanderop.eisen.feature.workout.rirSuffix
import io.github.alexanderop.eisen.feature.timer.sessionSummary
import io.github.alexanderop.eisen.feature.timer.timerSummary
import io.github.alexanderop.eisen.feature.food.FoodEntry
import io.github.alexanderop.eisen.feature.food.WeightEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
    val exerciseType: Int = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
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
        "${log.name} $load: ${repsText(log.sets, log.targetReps, log.timed)}${rirSuffix(log)}"
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

/**
 * The record for a finished interval timer: of the type of its activity ([exerciseTypeOf]), else a HIIT session or a
 * boxing one, named like the timer, from Start to the end, with the activity, what it was and what was done in the notes. Null when its end is not after its start.
 */
fun timerSessionFields(session: TimerSession, zone: ZoneId): SessionFields? {
    if (session.finishedAt <= session.startedAt) return null
    val start = Instant.ofEpochMilli(session.startedAt)
    val end = Instant.ofEpochMilli(session.finishedAt)
    // The same outcome the result and the history show: a For Time that ran into its cap says so.
    val done = when (timerOutcome(session)) {
        TimerOutcome.COMPLETED -> ""
        TimerOutcome.CAPPED -> ", time cap"
        TimerOutcome.STOPPED -> ", stopped early"
    }
    val activity = session.activity
    return SessionFields(
        start = start,
        startOffset = zone.rules.getOffset(start),
        end = end,
        endOffset = zone.rules.getOffset(end),
        title = session.name,
        notes = "${activity?.let { "${it.label}. " }.orEmpty()}${session.config.mode.title} ${timerSummary(session.config)}: ${sessionSummary(session)}$done",
        segments = emptyList(),
        exerciseType = when {
            activity != null -> exerciseTypeOf(activity)
            session.config.mode == TimerMode.BOXING -> ExerciseSessionRecord.EXERCISE_TYPE_BOXING
            else -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
        },
    )
}

/**
 * The Health Connect type of what a timer was done for. Every constant exists in connect-client 1.1.0
 * (ExerciseSessionRecord.EXERCISE_TYPE_*). Health Connect has no jump rope and no battle rope: those stay
 * interval training, the label is in the notes. An interval timer runs indoors, so rowing and the bike are
 * the machines; running is plain running, a treadmill included.
 */
fun exerciseTypeOf(activity: Activity): Int = when (activity) {
    Activity.BOXING -> ExerciseSessionRecord.EXERCISE_TYPE_BOXING
    Activity.JUMP_ROPE -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
    Activity.BATTLE_ROPE -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
    Activity.CROSS_TRAINER -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
    Activity.ROWING -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
    Activity.BIKE -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
    Activity.RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
}

/** Everything a nutrition record is built from: one food entry. Kept free of Android so it can be unit tested. */
data class NutritionFields(
    val start: Instant,
    val startOffset: ZoneOffset,
    val end: Instant,
    val endOffset: ZoneOffset,
    val kcal: Double,
    /** Null for a quick entry whose protein was not typed: unknown, not none. */
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val name: String,
)

/** How long a nutrition record lasts: Health Connect needs its end after its start, and a bite has no length of its own. */
const val NUTRITION_RECORD_MILLIS = 60_000L

/**
 * The nutrition record of [entry]: its calories, protein, carbs and fat as logged, under its name, the meal type left
 * unknown. It starts when the entry was logged when that was on the entry's day; an entry filled in later for an
 * earlier day (or copied onto one) starts at noon of that day, so Health Connect counts it on the day it was eaten.
 * Null for an entry whose day is not a date (a hand-edited backup).
 */
fun nutritionFields(entry: FoodEntry, zone: ZoneId): NutritionFields? {
    val day = runCatching { LocalDate.parse(entry.day) }.getOrNull() ?: return null
    val logged = Instant.ofEpochMilli(entry.at)
    val start = if (logged.atZone(zone).toLocalDate() == day) logged else day.atTime(LocalTime.NOON).atZone(zone).toInstant()
    val end = start.plusMillis(NUTRITION_RECORD_MILLIS)
    return NutritionFields(
        start = start,
        startOffset = zone.rules.getOffset(start),
        end = end,
        endOffset = zone.rules.getOffset(end),
        kcal = entry.kcal.toDouble(),
        protein = entry.protein.takeIf { it > 0 || entry.foodId != null },
        carbs = entry.carbs,
        fat = entry.fat,
        name = entry.name,
    )
}

/** Everything a weight record is built from: one weight of the log. Kept free of Android so it can be unit tested. */
data class WeightFields(val id: String, val time: Instant, val offset: ZoneOffset, val kg: Double)

/** The client record id of the weight of [day]: one per day, so a second weight that day rewrites the record, and a delete finds it. */
fun weightRecordId(day: String): String = "weight:$day"

/**
 * The weight record of [entry]: its kilograms, at the moment it was logged when that was on its day; a weight filled in
 * later for an earlier day is put at noon of that day, so Health Connect counts it on the day it was weighed. Null for an
 * entry whose day is not a date (a hand-edited backup).
 */
fun weightFields(entry: WeightEntry, zone: ZoneId): WeightFields? {
    val day = runCatching { LocalDate.parse(entry.day) }.getOrNull() ?: return null
    val logged = Instant.ofEpochMilli(entry.at)
    val time = if (logged.atZone(zone).toLocalDate() == day) logged else day.atTime(LocalTime.NOON).atZone(zone).toInstant()
    return WeightFields(weightRecordId(entry.day), time, zone.rules.getOffset(time), entry.kg)
}
