package io.github.alexanderop.eisen.domain.elm

import io.github.alexanderop.eisen.domain.AppState
import io.github.alexanderop.eisen.domain.CATALOG
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.Exercise
import io.github.alexanderop.eisen.domain.ExerciseProgress
import io.github.alexanderop.eisen.domain.MAX_HELD_SECONDS
import io.github.alexanderop.eisen.domain.Session
import io.github.alexanderop.eisen.domain.Routine
import io.github.alexanderop.eisen.domain.Settings
import io.github.alexanderop.eisen.domain.active
import io.github.alexanderop.eisen.domain.encodeState
import io.github.alexanderop.eisen.domain.findExercise
import io.github.alexanderop.eisen.domain.startProgress
import java.time.Instant
import java.time.ZoneOffset

/*
 * What screens read from the model besides its fields. Pure, so a screen and a test see the same.
 */

val Model.settings: Settings get() = state.settings

/** The built-in exercises followed by the lifter's own. */
fun exercises(model: Model): List<Exercise> = CATALOG + model.state.customExercises

fun findExercise(model: Model, id: String): Exercise? = model.state.findExercise(id)

/** The exercise a name stands for, the way [Msg.Exercises.AddCustom] matches it. Null for an empty name. */
fun exerciseNamed(model: Model, name: String): Exercise? {
    val trimmed = name.trim().take(EXERCISE_NAME_LENGTH)
    if (trimmed.isEmpty()) return null
    return exercises(model).firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
}

private val NO_PROGRESS = ExerciseProgress(weight = 0.0, fails = 0, increment = 0.0)

/** The working weight of an exercise, or its starting weight when it was never used. 0 for plain bodyweight. */
fun progressOf(model: Model, id: String): ExerciseProgress = progressOf(model.state, id)

fun progressOf(state: AppState, id: String): ExerciseProgress {
    state.progress[id]?.let { return it }
    val exercise = state.findExercise(id)
    if (exercise == null || exercise.equipment == Equipment.BODYWEIGHT) return NO_PROGRESS
    return startProgress(exercise, state.settings.unit)
}

/**
 * The routine the home screen offers next. There is always one: a fresh state has two,
 * a backup without any is refused, and the last one cannot be removed.
 */
fun nextRoutine(model: Model): Routine =
    model.state.routines.firstOrNull { it.id == model.state.nextRoutineId } ?: model.state.routines[0]

/** The workout screen shows instead of the tabs. A hold timer is drawn on it, so it only exists while this is true. */
fun showsWorkout(model: Model): Boolean = model.nav.workoutOpen && model.state.active != null

/** The routine open in the Workouts tab's editor, if it still exists. */
fun openRoutine(model: Model): Routine? = model.nav.routine?.let { id -> model.state.routines.firstOrNull { it.id == id } }

/** The rest screen covers the workout screen. A minimised one leaves the rest bar showing. */
fun showsRestScreen(model: Model): Boolean = showsWorkout(model) && model.restScreen?.minimised == false

/**
 * The rest screen still belongs to the running rest and to a logged set of its exercise in the running
 * workout. A hold timer never shows with it.
 */
fun restScreenFits(model: Model, screen: RestScreen): Boolean {
    if (model.rest == null || model.hold != null) return false
    val log = model.session.active?.exercises?.getOrNull(screen.exercise) ?: return false
    return log.id == screen.exerciseId && log.sets.getOrNull(screen.set) != null
}

/**
 * True when [Msg.Nav.Back] does something: it asks before it ends an interval timer, closes a hold timer
 * before the workout. Otherwise the back gesture leaves the app.
 */
fun handlesBack(model: Model): Boolean =
    model.timer != null || showsWorkout(model) || (model.nav.tab == Tab.WORKOUTS && openRoutine(model) != null) || model.nav.tab != Tab.HOME

/** A workout is running or waiting for an answer. Settings that would split its log are locked meanwhile. */
fun workoutRunning(model: Model): Boolean = model.session.active != null

/** The whole state as JSON. The PWA reads the same file. */
fun exportJson(model: Model): String = encodeState(model.state, pretty = true)

/** The same name the PWA gives its download, so backups from both apps sort together. */
fun backupFileName(now: Long): String = "eisen-backup-${Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()}.json"

/** Seconds left of [rest] at [now], rounded up. 0 once it is over. */
fun restSecondsLeft(rest: RestState, now: Long): Int = maxOf(0, Math.ceil((rest.endsAt - now) / 1000.0).toInt())

fun restCountdown(rest: RestState) = RestCountdown(
    title = "Rest",
    text = rest.note,
    endsAt = rest.endsAt,
    extendLabel = "+${EXTEND_SECONDS}s",
    skipLabel = "Skip",
)

fun restOver(rest: RestState) = RestOver(title = "Rest is over", text = "Start your next set.", endedAt = rest.endsAt)

/** "Get ready" until the hold starts, then the hold. */
enum class HoldPhase { LEAD_IN, HOLD }

fun holdPhase(hold: HoldTimer, now: Long): HoldPhase = if (now < hold.startsAt) HoldPhase.LEAD_IN else HoldPhase.HOLD

/** When the hold ends by itself: at its target, or at [MAX_HELD_SECONDS] when it is held as long as possible. */
fun holdEndsAt(hold: HoldTimer): Long = hold.startsAt + (hold.target ?: MAX_HELD_SECONDS) * 1000L

/** Whole seconds held at [now], never more than the hold can log. */
fun heldSeconds(hold: HoldTimer, now: Long): Int =
    ((now - hold.startsAt) / 1000).coerceIn(0, (hold.target ?: MAX_HELD_SECONDS).toLong()).toInt()

/** The big number of the timer: seconds of "Get ready" left, seconds of the countdown left, or seconds held. */
fun holdSecondsShown(hold: HoldTimer, now: Long): Int = when {
    now < hold.startsAt -> Math.ceil((hold.startsAt - now) / 1000.0).toInt()
    hold.target != null -> maxOf(0, Math.ceil((holdEndsAt(hold) - now) / 1000.0).toInt())
    else -> heldSeconds(hold, now)
}

/** The hold timer still belongs to an open set of a timed exercise of the running workout, which shows. */
fun holdFits(model: Model, hold: HoldTimer): Boolean {
    val session = model.session as? Session.Running ?: return false
    val log = session.run.workout.exercises.getOrNull(hold.exercise) ?: return false
    return model.nav.workoutOpen && log.id == hold.exerciseId && log.timed && log.sets.getOrNull(hold.set) == null && hold.set in log.sets.indices
}
