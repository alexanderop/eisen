package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.Exercise
import io.github.alexanderop.eisen.core.catalogExercise
import io.github.alexanderop.eisen.core.holdText
import io.github.alexanderop.eisen.feature.workout.Change
import io.github.alexanderop.eisen.feature.workout.ExerciseLog
import io.github.alexanderop.eisen.feature.workout.ExerciseProgress
import io.github.alexanderop.eisen.feature.workout.MAX_HELD_SECONDS
import io.github.alexanderop.eisen.feature.workout.MAX_RIR
import io.github.alexanderop.eisen.feature.workout.Outcome
import io.github.alexanderop.eisen.feature.workout.Progress
import io.github.alexanderop.eisen.feature.workout.Workout
import io.github.alexanderop.eisen.feature.workout.applyProgression
import io.github.alexanderop.eisen.feature.workout.canRate
import io.github.alexanderop.eisen.feature.workout.createLog
import io.github.alexanderop.eisen.feature.workout.createWorkout
import io.github.alexanderop.eisen.feature.workout.defaultSlot
import io.github.alexanderop.eisen.feature.workout.isComplete
import io.github.alexanderop.eisen.feature.workout.nextReps
import io.github.alexanderop.eisen.feature.workout.nextRoutineId
import io.github.alexanderop.eisen.feature.workout.outcome
import io.github.alexanderop.eisen.feature.workout.repsChoices
import io.github.alexanderop.eisen.feature.workout.rirOf
import io.github.alexanderop.eisen.feature.workout.startProgress
import io.github.alexanderop.eisen.feature.workout.withReps
import io.github.alexanderop.eisen.feature.workout.withRir
import io.github.alexanderop.eisen.app.state.AppState

/*
 * The running workout as a finite state machine. Every rule about what a tap, a finish or a
 * discard does lives in [transition]. It is the workout's part of the app's `update` (see
 * `app/elm/Update.kt`), which keeps the result in the Model and turns the [Effect]s into
 * model changes and Cmds. Screens send [WorkoutEvent]s as `Msg.Workout(event)`. Pure Kotlin,
 * so all of it is covered by unit tests.
 *
 *                 ┌──────────────── Start ────────────────┐
 *                 │                                       ▼
 *            ┌────────┐  RequestFinish, all sets done ┌─────────┐  TapSet LogReps LogHold SetWeight AddExercise
 *            │  Idle  │ ◀──────── (saves) ─────────── │ Running │  CorrectReps RateSet RemoveExercise Defer Move Rise
 *            └────────┘                               └─────────┘  (all stay in Running)
 *              ▲    ▲                            │  ▲       │  ▲
 *              │    │     RequestFinish,         │  │       │  │   RequestDiscard,
 *              │    │     sets open              ▼  │Cancel ▼  │Cancel  sets logged
 *              │    │  Confirm,          ┌──────────────────┐  ┌───────────────────┐
 *              │    └─ sets logged ───── │ ConfirmingFinish │  │ ConfirmingDiscard │
 *              │       (saves)           └──────────────────┘  └───────────────────┘
 *              │                           │ Confirm,                │ Confirm
 *              │                           │ nothing logged          │
 *              │                           ▼                         ▼
 *              │    Forget            ┌─────────────────────────────────┐   RequestDiscard,
 *              └───────────────────── │            Discarded            │ ◀ nothing logged
 *                                     └─────────────────────────────────┘   (from Running)
 *                                        │ Undo  → Running, the same workout and weights
 *                                        │ Start → Running, a new workout (the old one is forgotten)
 *
 * Running has a derived [Stage]: Fresh (nothing logged), Underway, Complete (every set logged).
 * Any event a state does not list is ignored: the session, the state and the effects stay as they are.
 * [WorkoutEvent.SetWorkingWeight] is the one event every state takes, because the Settings tab can
 * change a working weight at any time.
 *
 * Only Running and the two confirmations are saved, as `AppState.active`. A confirmation is a
 * question asked on top of a running workout, so after the process dies it is Running again.
 * Discarded lives only in memory: after the process dies the discarded workout is gone.
 */

/** Everything about the running workout. Only [workout] is saved; the rest lives while the app runs. */
data class Run(
    val workout: Workout,
    /** Working weights when the workout started. Discarding puts them back. */
    val baseline: Progress,
    /** Exercises with sets logged, most recently worked on first. The screen shows them on top. */
    val ranking: List<String> = emptyList(),
    /**
     * True once the lifter moved an exercise by hand. From then on the open exercises keep the
     * lifter's order and the most recent one no longer rises to the top. Finished ones still sink.
     */
    val arranged: Boolean = false,
)

/** The run with its workout changed. */
internal inline fun Run.workout(change: (Workout) -> Workout): Run = copy(workout = change(workout))

enum class FinishReason {
    /** Some sets are open. Finishing saves what was logged. */
    EARLY,

    /** Not one set is logged. Finishing discards instead of saving. */
    NOTHING_LOGGED,
}

enum class Stage { FRESH, UNDERWAY, COMPLETE }

/** The kinds of haptic feedback the workout asks for. The screen maps them to the phone's effects. */
enum class Feel { TAP, WARNING, SUCCESS, DONE }

sealed interface Session {
    data object Idle : Session

    /** Every state that holds a workout. */
    sealed interface WithRun : Session {
        val run: Run
        val workout: Workout get() = run.workout
    }

    data class Running(override val run: Run) : WithRun
    data class ConfirmingFinish(override val run: Run, val reason: FinishReason) : WithRun
    data class ConfirmingDiscard(override val run: Run) : WithRun

    /**
     * Just discarded. [undo] holds the working weights the discard put back, to restore them on Undo.
     * [cleared] are the entries the workout had removed (an added weight set back to none): the
     * discard brought them back, so Undo removes them again.
     */
    data class Discarded(override val run: Run, val undo: Progress, val cleared: Set<String> = emptySet()) : WithRun

    companion object {
        /** The session for a saved state: its workout running again, or nothing. */
        fun restore(state: AppState): Session {
            val workout = state.active ?: return Idle
            // The weights at the start are not saved. The ones on disk are the best guess there is.
            val ranking = workout.exercises.filter { isStarted(it) && !isDone(it) }.map { it.id }
            return Running(Run(workout, baseline = state.progress, ranking = ranking))
        }
    }
}

/** The workout saved as `AppState.active`. A discarded workout is not saved. */
val Session.active: Workout?
    get() = when (this) {
        Session.Idle, is Session.Discarded -> null
        is Session.Running -> run.workout
        is Session.ConfirmingFinish -> run.workout
        is Session.ConfirmingDiscard -> run.workout
    }

sealed interface WorkoutEvent {
    /** Starts the routine the home screen offers next. */
    data object Start : WorkoutEvent

    /**
     * One tap on a set button. A max-reps set logs last time's count, or asks for it, see [Effect.AskReps].
     * An open set of a timed exercise opens the hold timer, see [Effect.OpenHold]; a logged one is cleared.
     */
    data class TapSet(val exercise: Int, val set: Int) : WorkoutEvent

    /** Logs a set with an entered count. Null clears it. */
    data class LogReps(val exercise: Int, val set: Int, val reps: Int?) : WorkoutEvent

    /** The hold timer logs a set of a timed exercise: [seconds] held, at least 1. */
    data class LogHold(val exercise: Int, val set: Int, val seconds: Int) : WorkoutEvent

    /**
     * Corrects the count of a logged set, from the rest screen's reps wheel. Unlike a tap it never
     * clears the set (0 is a set with no reps), asks nothing, and leaves the running rest as it is.
     * Not for timed sets, whose count only the hold timer logs.
     */
    data class CorrectReps(val exercise: Int, val set: Int, val reps: Int) : WorkoutEvent

    /**
     * The reps in reserve of a logged set, from the rest screen: 0 to [MAX_RIR], null takes the rating
     * away. Only for a set that [canRate], and only while the lifter tracks them. Leaves the rest alone.
     */
    data class RateSet(val exercise: Int, val set: Int, val rir: Int?) : WorkoutEvent

    /**
     * The weight of one exercise in the running workout. It is also its working weight.
     * For a bodyweight exercise it is the weight added on top, 0 for none.
     */
    data class SetWeight(val exercise: Int, val weight: Double) : WorkoutEvent

    /** A working weight set from Settings. A running workout follows it; any state takes it. */
    data class SetWorkingWeight(val id: String, val weight: Double) : WorkoutEvent

    /** Adds an exercise to the running workout only. The routine stays as it is. */
    data class AddExercise(val id: String) : WorkoutEvent

    /** The last exercise stays: a workout without exercises has nothing to finish. */
    data class RemoveExercise(val exercise: Int) : WorkoutEvent

    /** Moves an exercise behind the others, for when its machine or rack is taken. */
    data class Defer(val exercise: Int) : WorkoutEvent

    /** Moves an open exercise in the order the screen shows the open ones ([order] with nothing settling). */
    data class Move(val from: Int, val to: Int) : WorkoutEvent

    /** The exercise tapped last has settled: when it is started and not finished, it rises to the top. */
    data class Rise(val id: String) : WorkoutEvent

    data object RequestFinish : WorkoutEvent
    data object RequestDiscard : WorkoutEvent
    data object Confirm : WorkoutEvent
    data object Cancel : WorkoutEvent
    data object Undo : WorkoutEvent
    data object Forget : WorkoutEvent
}

sealed interface Effect {
    /** A rest after set [set] of the exercise at [exercise], the set that started it. */
    data class StartRest(val seconds: Int, val note: String, val exercise: Int, val set: Int) : Effect
    data object StopRest : Effect

    /** Set [set] was the last open one of the exercise at [exercise]. No rest follows it. */
    data class LastSet(val exercise: Int, val set: Int) : Effect
    data class Haptic(val kind: Feel) : Effect

    /** A max-reps set was tapped: ask for the count, starting at [reps]. */
    data class AskReps(val exercise: Int, val set: Int, val reps: Int) : Effect

    /** An open set of a timed exercise was tapped: the hold timer opens for it. */
    data class OpenHold(val exercise: Int, val set: Int) : Effect

    /** A new workout started. `update` forgets the last summary and the ticked warmups. */
    data class Started(val workout: Workout) : Effect

    /** A workout was finished and saved to the history with these weight changes. */
    data class Saved(val workout: Workout, val changes: List<Change>) : Effect

    /** A workout was discarded. It can still be undone until [WorkoutEvent.Forget] or a new start. */
    data class DiscardedWorkout(val workout: Workout) : Effect
}

/** The new session, the state that goes with it and what `update` has to do about it. */
data class Transition(val session: Session, val state: AppState, val effects: List<Effect> = emptyList())

/**
 * What `update` and a transition need from outside: the clock, new ids and random seeds. Elm would ask
 * for them with a Cmd and get the answer back as a Msg. Here the runtime passes them in with every Msg:
 * one value instead of a round trip, and `update` stays pure because a test passes its own.
 * [newSeed] gives a number from 0 to [Int.MAX_VALUE]; a test that does not pass one gets one made of the clock.
 */
class Env(val now: Long, val newSeed: () -> Int = { (now xor (now ushr 29)).toInt() and Int.MAX_VALUE }, val newId: () -> String)

fun transition(session: Session, state: AppState, event: WorkoutEvent, env: Env): Transition {
    val next = when {
        event is WorkoutEvent.SetWorkingWeight -> setWorkingWeight(session, state, event)
        // Adding a state is a compile error here until it says which events it takes.
        else -> when (session) {
            Session.Idle -> idle(state, event, env)
            is Session.Running -> running(session.run, state, event, env)
            is Session.ConfirmingFinish -> confirmingFinish(session, state, event, env)
            is Session.ConfirmingDiscard -> confirmingDiscard(session, state, event)
            is Session.Discarded -> discarded(session, state, event, env)
        }
    } ?: Transition(session, state)
    // Keeps the saved workout and the session in step whatever the branch above did.
    return next.copy(state = next.state.copy(active = next.session.active))
}

// Each state lists the events it ignores, so a new event does not compile until every state says what it does.
// SetWorkingWeight never reaches them: [transition] takes it in every state.

private fun idle(state: AppState, event: WorkoutEvent, env: Env): Transition? = when (event) {
    WorkoutEvent.Start -> start(state, env)
    is WorkoutEvent.TapSet, is WorkoutEvent.LogReps, is WorkoutEvent.LogHold, is WorkoutEvent.CorrectReps, is WorkoutEvent.RateSet,
    is WorkoutEvent.SetWeight, is WorkoutEvent.SetWorkingWeight, is WorkoutEvent.AddExercise, is WorkoutEvent.RemoveExercise,
    is WorkoutEvent.Defer, is WorkoutEvent.Move,
    is WorkoutEvent.Rise, WorkoutEvent.RequestFinish, WorkoutEvent.RequestDiscard, WorkoutEvent.Confirm,
    WorkoutEvent.Cancel, WorkoutEvent.Undo, WorkoutEvent.Forget,
    -> null
}

private fun running(run: Run, state: AppState, event: WorkoutEvent, env: Env): Transition? {
    val workout = run.workout
    return when (event) {
        is WorkoutEvent.TapSet -> tapSet(run, state, event)
        is WorkoutEvent.LogReps -> logReps(run, state, event)
        is WorkoutEvent.LogHold -> logHold(run, state, event)
        is WorkoutEvent.CorrectReps -> correctReps(run, state, event)
        is WorkoutEvent.RateSet -> rateSet(run, state, event)
        is WorkoutEvent.SetWeight -> {
            val log = workout.exercises.getOrNull(event.exercise) ?: return null
            val weight = event.weight.coerceAtLeast(0.0)
            Transition(
                Session.Running(run.copy(workout = workout.editLog(event.exercise) { it.copy(weight = weight) })),
                state.editProgress(log.id) { it.copy(weight = weight) },
            )
        }
        is WorkoutEvent.AddExercise -> {
            val exercise = state.findExercise(event.id) ?: return null
            if (workout.exercises.any { it.id == event.id }) return null
            val ready = state.ensureProgress(event.id)
            val log = createLog(exercise, defaultSlot(exercise), ready.progress, ready.settings.unit)
            Transition(Session.Running(run.workout { it.copy(exercises = it.exercises + log) }), ready)
        }
        is WorkoutEvent.RemoveExercise -> {
            val log = workout.exercises.getOrNull(event.exercise) ?: return null
            if (workout.exercises.size <= 1) return null
            val exercises = workout.exercises.filterIndexed { index, _ -> index != event.exercise }
            Transition(Session.Running(run.workout { it.copy(exercises = exercises) }.copy(ranking = run.ranking - log.id)), state)
        }
        is WorkoutEvent.Defer -> {
            val log = workout.exercises.getOrNull(event.exercise) ?: return null
            val exercises = workout.exercises.filterIndexed { index, _ -> index != event.exercise } + log
            Transition(Session.Running(run.workout { it.copy(exercises = exercises) }.copy(ranking = run.ranking - log.id)), state)
        }
        is WorkoutEvent.Move -> move(run, event)?.let { Transition(Session.Running(it), state) }
        is WorkoutEvent.Rise -> {
            // A manual order wins: the exercise worked on no longer jumps to the top.
            if (run.arranged) return null
            val log = workout.exercises.firstOrNull { it.id == event.id }
            val rises = log != null && isStarted(log) && !isDone(log)
            val ranking = (if (rises) listOf(event.id) else emptyList()) + (run.ranking - event.id)
            Transition(Session.Running(run.copy(ranking = ranking)), state)
        }
        WorkoutEvent.RequestFinish -> when (stageOf(workout)) {
            Stage.COMPLETE -> finish(run, state, env)
            Stage.FRESH -> Transition(Session.ConfirmingFinish(run, FinishReason.NOTHING_LOGGED), state)
            Stage.UNDERWAY -> Transition(Session.ConfirmingFinish(run, FinishReason.EARLY), state)
        }
        WorkoutEvent.RequestDiscard ->
            // Nothing logged means nothing is lost, so there is nothing to confirm.
            if (stageOf(workout) == Stage.FRESH) discard(run, state)
            else Transition(Session.ConfirmingDiscard(run), state, listOf(Effect.Haptic(Feel.WARNING)))
        WorkoutEvent.Start, is WorkoutEvent.SetWorkingWeight, WorkoutEvent.Confirm, WorkoutEvent.Cancel,
        WorkoutEvent.Undo, WorkoutEvent.Forget,
        -> null
    }
}

private fun confirmingFinish(session: Session.ConfirmingFinish, state: AppState, event: WorkoutEvent, env: Env): Transition? =
    when (event) {
        // With nothing logged, finishing discards: see [finish].
        WorkoutEvent.Confirm -> finish(session.run, state, env)
        WorkoutEvent.Cancel -> Transition(Session.Running(session.run), state)
        WorkoutEvent.Start, is WorkoutEvent.TapSet, is WorkoutEvent.LogReps, is WorkoutEvent.LogHold, is WorkoutEvent.CorrectReps,
        is WorkoutEvent.RateSet, is WorkoutEvent.SetWeight, is WorkoutEvent.SetWorkingWeight, is WorkoutEvent.AddExercise, is WorkoutEvent.RemoveExercise,
        is WorkoutEvent.Defer, is WorkoutEvent.Move, is WorkoutEvent.Rise, WorkoutEvent.RequestFinish,
        WorkoutEvent.RequestDiscard, WorkoutEvent.Undo, WorkoutEvent.Forget,
        -> null
    }

private fun confirmingDiscard(session: Session.ConfirmingDiscard, state: AppState, event: WorkoutEvent): Transition? =
    when (event) {
        WorkoutEvent.Confirm -> discard(session.run, state)
        WorkoutEvent.Cancel -> Transition(Session.Running(session.run), state)
        WorkoutEvent.Start, is WorkoutEvent.TapSet, is WorkoutEvent.LogReps, is WorkoutEvent.LogHold, is WorkoutEvent.CorrectReps,
        is WorkoutEvent.RateSet, is WorkoutEvent.SetWeight, is WorkoutEvent.SetWorkingWeight, is WorkoutEvent.AddExercise, is WorkoutEvent.RemoveExercise,
        is WorkoutEvent.Defer, is WorkoutEvent.Move, is WorkoutEvent.Rise, WorkoutEvent.RequestFinish,
        WorkoutEvent.RequestDiscard, WorkoutEvent.Undo, WorkoutEvent.Forget,
        -> null
    }

private fun discarded(session: Session.Discarded, state: AppState, event: WorkoutEvent, env: Env): Transition? =
    when (event) {
        // The rest timer is not restored: the rest it showed is long over or no longer true.
        WorkoutEvent.Undo -> Transition(Session.Running(session.run), state.copy(progress = state.progress + session.undo - session.cleared))
        WorkoutEvent.Forget -> Transition(Session.Idle, state)
        WorkoutEvent.Start -> start(state, env)
        is WorkoutEvent.TapSet, is WorkoutEvent.LogReps, is WorkoutEvent.LogHold, is WorkoutEvent.CorrectReps, is WorkoutEvent.RateSet,
        is WorkoutEvent.SetWeight, is WorkoutEvent.SetWorkingWeight, is WorkoutEvent.AddExercise, is WorkoutEvent.RemoveExercise, is WorkoutEvent.Defer,
        is WorkoutEvent.Move, is WorkoutEvent.Rise, WorkoutEvent.RequestFinish, WorkoutEvent.RequestDiscard, WorkoutEvent.Confirm,
        WorkoutEvent.Cancel,
        -> null
    }

private fun start(state: AppState, env: Env): Transition? {
    val routine = state.routines.firstOrNull { it.id == state.nextRoutineId } ?: state.routines.firstOrNull() ?: return null
    val ready = routine.slots.fold(state) { current, slot -> current.ensureProgress(slot.exerciseId) }
    val workout = createWorkout(routine, ready::findExercise, ready.progress, ready.settings.unit, env.now, env.newId())
    // A routine whose exercises were all deleted has nothing to do.
    if (workout.exercises.isEmpty()) return null
    return Transition(Session.Running(Run(workout, baseline = ready.progress)), ready, listOf(Effect.Started(workout)))
}

private fun tapSet(run: Run, state: AppState, event: WorkoutEvent.TapSet): Transition? {
    val log = run.workout.exercises.getOrNull(event.exercise) ?: return null
    if (event.set !in log.sets.indices) return null
    if (log.timed) return tapHold(run, state, event)
    val target = log.targetReps ?: return tapMaxSet(run, state, event, log)
    val reps = nextReps(log.sets[event.set], target)
    val workout = run.workout.editLog(event.exercise) { withReps(it, event.set, reps) }
    val feel = when {
        reps != null && reps < target -> Feel.WARNING
        outcome(workout.exercises[event.exercise]) == Outcome.SUCCESS -> Feel.SUCCESS
        else -> Feel.TAP
    }
    val rest = when {
        reps == null || lastSetLogged(workout, event.exercise) -> Effect.StopRest
        reps == target -> Effect.StartRest(state.settings.restDone, "All reps done.", event.exercise, event.set)
        else -> Effect.StartRest(state.settings.restMissed, "Missed reps. Rest longer.", event.exercise, event.set)
    }
    return Transition(Session.Running(run.copy(workout = workout)), state, listOf(Effect.Haptic(feel), rest) + lastSet(workout, event.exercise, event.set, reps))
}

/**
 * An open max-reps set takes last time's count with one tap: most days it is the same again.
 * A logged one, or one with no last time, asks for the count.
 */
private fun tapMaxSet(run: Run, state: AppState, event: WorkoutEvent.TapSet, log: ExerciseLog): Transition? {
    val logged = log.sets[event.set]
    val last = lastReps(state.history, log.id, event.set)
    if (logged == null && last != null) return logReps(run, state, WorkoutEvent.LogReps(event.exercise, event.set, last))
    return Transition(Session.Running(run), state, listOf(Effect.AskReps(event.exercise, event.set, logged ?: FIRST_MAX_REPS)))
}

/**
 * A timed set is not counted down by taps. An open one opens the hold timer, which takes the place
 * of a running rest; a logged one is cleared.
 */
private fun tapHold(run: Run, state: AppState, event: WorkoutEvent.TapSet): Transition {
    if (run.workout.exercises[event.exercise].sets[event.set] == null) {
        return Transition(Session.Running(run), state, listOf(Effect.Haptic(Feel.TAP), Effect.StopRest, Effect.OpenHold(event.exercise, event.set)))
    }
    val workout = run.workout.editLog(event.exercise) { withReps(it, event.set, null) }
    return Transition(Session.Running(run.copy(workout = workout)), state, listOf(Effect.Haptic(Feel.TAP), Effect.StopRest))
}

/**
 * True when the exercise has no open set left. No rest follows its last set: the lifter
 * moves on to the next exercise, and a timer for the old one would only be in the way.
 */
private fun lastSetLogged(workout: Workout, exercise: Int): Boolean =
    workout.exercises[exercise].sets.all { it != null }

/** [Effect.LastSet] when logging [reps] on set [set] left the exercise without an open set. */
private fun lastSet(workout: Workout, exercise: Int, set: Int, reps: Int?): List<Effect> =
    if (reps != null && lastSetLogged(workout, exercise)) listOf(Effect.LastSet(exercise, set)) else emptyList()

private fun logReps(run: Run, state: AppState, event: WorkoutEvent.LogReps): Transition? {
    val log = run.workout.exercises.getOrNull(event.exercise) ?: return null
    if (event.set !in log.sets.indices) return null
    val workout = run.workout.editLog(event.exercise) { withReps(it, event.set, event.reps) }
    val rest = if (event.reps == null || lastSetLogged(workout, event.exercise)) Effect.StopRest
    else Effect.StartRest(state.settings.restDone, "Max set logged.", event.exercise, event.set)
    return Transition(Session.Running(run.copy(workout = workout)), state, listOf(Effect.Haptic(Feel.TAP), rest) + lastSet(workout, event.exercise, event.set, event.reps))
}

/** A logged count corrected after the fact. Only the count changes: no rest, no haptic, no question. */
private fun correctReps(run: Run, state: AppState, event: WorkoutEvent.CorrectReps): Transition? {
    val log = run.workout.exercises.getOrNull(event.exercise) ?: return null
    val logged = log.sets.getOrNull(event.set) ?: return null
    if (log.timed || event.reps == logged || event.reps !in repsChoices(log.targetReps, logged)) return null
    val workout = run.workout.editLog(event.exercise) { withReps(it, event.set, event.reps) }
    return Transition(Session.Running(run.copy(workout = workout)), state)
}

/** A rating given, changed or taken away. Like a correction: no rest, no haptic, no question. */
private fun rateSet(run: Run, state: AppState, event: WorkoutEvent.RateSet): Transition? {
    val log = run.workout.exercises.getOrNull(event.exercise) ?: return null
    if (!state.settings.trackRir || !canRate(log, event.set) || event.rir == rirOf(log, event.set)) return null
    if (event.rir != null && event.rir !in 0..MAX_RIR) return null
    val workout = run.workout.editLog(event.exercise) { withRir(it, event.set, event.rir) }
    return Transition(Session.Running(run.copy(workout = workout)), state)
}

/**
 * A hold that lasted its time, or one as long as possible, rests as long as a set with all reps.
 * One cut short rests as long as one with missed reps.
 */
private fun logHold(run: Run, state: AppState, event: WorkoutEvent.LogHold): Transition? {
    val log = run.workout.exercises.getOrNull(event.exercise) ?: return null
    if (!log.timed || event.set !in log.sets.indices || event.seconds !in 1..MAX_HELD_SECONDS) return null
    val workout = run.workout.editLog(event.exercise) { withReps(it, event.set, event.seconds) }
    val target = log.targetReps
    val short = target != null && event.seconds < target
    val feel = when {
        short -> Feel.WARNING
        outcome(workout.exercises[event.exercise]) == Outcome.SUCCESS && lastSetLogged(workout, event.exercise) -> Feel.SUCCESS
        else -> Feel.TAP
    }
    val rest = when {
        lastSetLogged(workout, event.exercise) -> Effect.StopRest
        short -> Effect.StartRest(state.settings.restMissed, "Hold cut short. Rest longer.", event.exercise, event.set)
        else -> Effect.StartRest(state.settings.restDone, "Held ${holdText(event.seconds)}.", event.exercise, event.set)
    }
    return Transition(Session.Running(run.copy(workout = workout)), state, listOf(Effect.Haptic(feel), rest) + lastSet(workout, event.exercise, event.set, event.seconds))
}

/** Saves the workout, moves the weights on and offers the next routine. Nothing logged discards instead. */
private fun finish(run: Run, state: AppState, env: Env): Transition {
    val workout = run.workout
    if (workout.exercises.all { outcome(it) == Outcome.SKIPPED }) return discard(run, state)
    val result = applyProgression(state.progress, workout, state.settings.bar, state.settings.trackRir)
    val offered = (state.routines.firstOrNull { it.id == state.nextRoutineId } ?: state.routines.firstOrNull())?.id ?: state.nextRoutineId
    val finished = workout.copy(finishedAt = env.now)
    return Transition(
        Session.Idle,
        state.copy(
            progress = result.progress,
            history = listOf(finished) + state.history,
            nextRoutineId = nextRoutineId(state.routines, workout.routineId, offered),
        ),
        listOf(Effect.Haptic(Feel.DONE), Effect.StopRest, Effect.Saved(finished, result.changes)),
    )
}

/**
 * Throws the workout away and puts every working weight back to what it was when the workout
 * started, increments included. What it puts back is kept, so Undo can restore it.
 * An entry made during the workout (an added weight on a bodyweight exercise) is removed, and one
 * removed during it comes back. An entry whose custom exercise was deleted stays gone.
 */
private fun discard(run: Run, state: AppState): Transition {
    val changed = state.progress.filter { (id, entry) -> run.baseline[id] != entry }
    val cleared = run.baseline.filterKeys { it !in state.progress && state.findExercise(it) != null }
    val restored = buildMap {
        for ((id, entry) in state.progress) {
            if (id !in changed) put(id, entry) else run.baseline[id]?.let { put(id, it) }
        }
        putAll(cleared)
    }
    return Transition(
        Session.Discarded(run, undo = changed, cleared = cleared.keys),
        state.copy(progress = restored),
        listOf(Effect.StopRest, Effect.DiscardedWorkout(run.workout)),
    )
}

private fun setWorkingWeight(session: Session, state: AppState, event: WorkoutEvent.SetWorkingWeight): Transition {
    val weight = event.weight.coerceAtLeast(0.0)
    val next = state.editProgress(event.id) { it.copy(weight = weight, fails = 0) }
    fun follow(run: Run) = run.workout { workout ->
        workout.copy(exercises = workout.exercises.map { if (it.id == event.id) it.copy(weight = weight) else it })
    }
    val following = when (session) {
        // A discarded workout keeps the weights it had, so Undo brings back exactly that.
        Session.Idle, is Session.Discarded -> session
        is Session.Running -> session.copy(run = follow(session.run))
        is Session.ConfirmingFinish -> session.copy(run = follow(session.run))
        is Session.ConfirmingDiscard -> session.copy(run = follow(session.run))
    }
    return Transition(following, next)
}

/** Moves an open exercise among the open ones. The finished ones keep their places in the log. */
private fun move(run: Run, event: WorkoutEvent.Move): Run? {
    val open = order(run, settling = null).filterNot { it.done }
    if (event.from !in open.indices || event.to !in open.indices || event.from == event.to) return null
    val moved = open.toMutableList().apply { add(event.to, removeAt(event.from)) }
    // The open exercises fill the places open exercises had, in the new order.
    val places = open.map { it.index }.sorted()
    val exercises = run.workout.exercises.toMutableList()
    places.forEachIndexed { k, place -> exercises[place] = moved[k].log }
    return run.workout { it.copy(exercises = exercises) }.copy(arranged = true)
}

fun isStarted(log: ExerciseLog): Boolean = log.sets.any { it != null }

fun isDone(log: ExerciseLog): Boolean = log.sets.all { it != null }

fun stageOf(workout: Workout): Stage = when {
    isComplete(workout) -> Stage.COMPLETE
    workout.exercises.all { outcome(it) == Outcome.SKIPPED } -> Stage.FRESH
    else -> Stage.UNDERWAY
}

/** An exercise of the running workout, where it sits in the log and whether it shows as finished. */
data class OrderedExercise(val log: ExerciseLog, val index: Int, val done: Boolean)

/**
 * The order the workout screen shows: the open exercises first, then the finished ones, which
 * fold up at the bottom. The open ones are sorted by [Run.ranking] (most recently worked on
 * first, the untouched ones after them in log order) until the lifter moves one by hand; then
 * they keep the log order, which is the lifter's order.
 * [settling] is the exercise tapped last. It counts as open for a moment even when finished,
 * so the card does not jump away under the thumb.
 */
fun order(run: Run, settling: String?): List<OrderedExercise> {
    val items = run.workout.exercises.mapIndexed { index, log -> OrderedExercise(log, index, done = log.id != settling && isDone(log)) }
    val open = items.filterNot { it.done }
    val sorted = if (run.arranged) {
        open
    } else {
        val (ranked, rest) = open.partition { it.log.id in run.ranking }
        ranked.sortedBy { run.ranking.indexOf(it.log.id) } + rest
    }
    return sorted + items.filter { it.done }
}

/** "Do later" needs another open exercise to go behind, and makes no sense for a finished one. */
fun canDefer(ordered: List<OrderedExercise>, item: OrderedExercise): Boolean =
    ordered.count { !it.done } > 1 && !isDone(item.log)

/** What this set of the exercise got the last time it was logged. Null when it never was. */
fun lastReps(history: List<Workout>, id: String, setIndex: Int): Int? {
    for (workout in history) {
        workout.exercises.firstOrNull { it.id == id }?.sets?.getOrNull(setIndex)?.let { return it }
    }
    return null
}

/** Starting point for a max-reps entry when there is no last time to go by. */
const val FIRST_MAX_REPS = 8

/** The built-in exercise or the lifter's own with this id. */
fun AppState.findExercise(id: String): Exercise? = catalogExercise(id) ?: customExercises.firstOrNull { it.id == id }

/**
 * The state with a working weight for the exercise. Unchanged when it has or needs none.
 * A bodyweight exercise needs none: it gets an entry only when the lifter adds weight, see [editProgress].
 */
fun AppState.ensureProgress(id: String): AppState {
    if (id in progress) return this
    val exercise = findExercise(id)
    if (exercise == null || exercise.equipment == Equipment.BODYWEIGHT) return this
    return copy(progress = progress + (id to startProgress(exercise, settings.unit)))
}

private val NO_ADDED_WEIGHT = ExerciseProgress(weight = 0.0, fails = 0, increment = 0.0)

/**
 * Changes the working weight of an exercise. For a bodyweight exercise that is the weight added on
 * top: it keeps an entry only while that is above 0, so an untouched and a reset install look the same.
 * It never progresses by itself, so fails and increment stay 0.
 */
fun AppState.editProgress(id: String, change: (ExerciseProgress) -> ExerciseProgress): AppState {
    if (findExercise(id)?.equipment == Equipment.BODYWEIGHT) {
        val weight = change(progress[id] ?: NO_ADDED_WEIGHT).weight
        return copy(progress = if (weight > 0) progress + (id to NO_ADDED_WEIGHT.copy(weight = weight)) else progress - id)
    }
    val ready = ensureProgress(id)
    val entry = ready.progress[id] ?: return this
    return ready.copy(progress = ready.progress + (id to change(entry)))
}

private fun Workout.editLog(index: Int, change: (ExerciseLog) -> ExerciseLog): Workout =
    if (index !in exercises.indices) this
    else copy(exercises = exercises.mapIndexed { at, log -> if (at == index) change(log) else log })
