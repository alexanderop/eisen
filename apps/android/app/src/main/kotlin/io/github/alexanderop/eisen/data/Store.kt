package io.github.alexanderop.eisen.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.alexanderop.eisen.domain.AppState
import io.github.alexanderop.eisen.domain.CATALOG
import io.github.alexanderop.eisen.domain.Change
import io.github.alexanderop.eisen.domain.Effect
import io.github.alexanderop.eisen.domain.Env
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.Exercise
import io.github.alexanderop.eisen.domain.ExerciseLog
import io.github.alexanderop.eisen.domain.ExerciseProgress
import io.github.alexanderop.eisen.domain.Routine
import io.github.alexanderop.eisen.domain.Session
import io.github.alexanderop.eisen.domain.Settings
import io.github.alexanderop.eisen.domain.Slot
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.Workout
import io.github.alexanderop.eisen.domain.WorkoutEvent
import io.github.alexanderop.eisen.domain.active
import io.github.alexanderop.eisen.domain.convertProgress
import io.github.alexanderop.eisen.domain.customExercise
import io.github.alexanderop.eisen.domain.decodeState
import io.github.alexanderop.eisen.domain.defaultSlot
import io.github.alexanderop.eisen.domain.editProgress
import io.github.alexanderop.eisen.domain.encodeState
import io.github.alexanderop.eisen.domain.ensureProgress
import io.github.alexanderop.eisen.domain.findExercise
import io.github.alexanderop.eisen.domain.freshState
import io.github.alexanderop.eisen.domain.startProgress
import io.github.alexanderop.eisen.domain.transition
import io.github.alexanderop.eisen.domain.unitDefaults
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

private val NO_PROGRESS = ExerciseProgress(weight = 0.0, fails = 0, increment = 0.0)

/**
 * The list with the item at [from] taken out and put back at [to]. The same list when an index
 * is outside it, so a stale index from a drag or an old screen changes nothing.
 */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Everything the app knows, as one immutable value. Screens read [state] and call the functions
 * here to change it. Every change is written to a file in the app's private storage.
 */
object Store {
    var state: AppState by mutableStateOf(freshState())
        private set

    /**
     * The running workout and the question asked about it, if any. See `domain/WorkoutMachine.kt`.
     * It is the one source of the workout: [state]'s `active` is always the session's saved workout.
     */
    var session: Session by mutableStateOf(Session.Idle)
        private set

    /** Warmup sets ticked off in the running workout, as `exerciseId:setIndex`. Not saved. */
    var warmupDone: Set<String> by mutableStateOf(emptySet())

    /** What the last finished workout changed. Shown once on the home screen. */
    var lastChanges: List<Change>? by mutableStateOf(null)

    /**
     * Called on the main thread after every change, with the new state.
     * For what lives outside the screens: the home-screen widget and the app shortcuts.
     */
    val changeListeners = mutableListOf<(AppState) -> Unit>()

    /** Called on the main thread with every workout that was finished and saved. */
    val finishListeners = mutableListOf<(Workout) -> Unit>()

    private var file: File? = null
    private val writer = Executors.newSingleThreadExecutor()

    /** Safe to call again: a broadcast can start the process without the activity. */
    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val target = File(context.filesDir, "eisen.json")
        file = target
        // Unreadable storage: start fresh instead of blocking the app.
        state = runCatching { decodeState(target.readText()) }.getOrNull() ?: freshState()
        session = Session.restore(state)
    }

    /**
     * The only way to change the running workout. Keeps what the machine decided and handles the
     * effects that belong to the data. Returns every effect, so the screen can run the rest
     * (rest timer, haptics, the reps entry) with `ui/workout/WorkoutEffects.kt`.
     */
    fun send(event: WorkoutEvent): List<Effect> {
        val result = transition(session, state, event, Env(System.currentTimeMillis(), ::newId))
        session = result.session
        update { result.state }
        for (effect in result.effects) {
            when (effect) {
                is Effect.Started -> {
                    lastChanges = null
                    warmupDone = emptySet()
                }
                is Effect.Saved -> {
                    lastChanges = effect.changes
                    for (listener in finishListeners) listener(effect.workout)
                }
                else -> Unit
            }
        }
        return result.effects
    }

    private fun update(change: (AppState) -> AppState) {
        // The workout belongs to the session. Nothing else may put a different one on disk.
        val next = change(state).copy(active = session.active)
        if (next == state) return
        state = next
        for (listener in changeListeners) listener(next)
        val target = file ?: return
        writer.execute {
            // Written next to the file and renamed, so a crash mid-write cannot leave half a file.
            runCatching {
                val draft = File(target.parentFile, target.name + ".tmp")
                draft.writeText(encodeState(next))
                if (!draft.renameTo(target)) target.writeText(encodeState(next))
            }
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    /** The built-in exercises followed by the lifter's own. */
    val exercises: List<Exercise> get() = CATALOG + state.customExercises

    fun findExercise(id: String): Exercise? = state.findExercise(id)

    /** The working weight of an exercise, or its starting weight when it was never used. */
    fun progressOf(id: String): ExerciseProgress {
        state.progress[id]?.let { return it }
        val exercise = findExercise(id)
        if (exercise == null || exercise.equipment == Equipment.BODYWEIGHT) return NO_PROGRESS
        return startProgress(exercise, state.settings.unit)
    }

    private fun <T> List<T>.replaced(index: Int, value: T): List<T> = mapIndexed { at, item -> if (at == index) value else item }

    val nextRoutine: Routine get() = state.routines.firstOrNull { it.id == state.nextRoutineId } ?: state.routines[0]

    fun selectRoutine(id: String) {
        if (state.active == null) update { it.copy(nextRoutineId = id) }
    }

    /** Starts the routine offered next, for the widget and the shortcuts. False when it has no exercise left to do. */
    fun startWorkout(): Boolean {
        send(WorkoutEvent.Start)
        return state.active != null
    }

    /** A running workout follows the new weight. */
    fun setProgressWeight(id: String, weight: Double) {
        send(WorkoutEvent.SetWorkingWeight(id, weight))
    }

    /** Discarding a workout puts an increment changed during it back, see `WorkoutMachine.kt`. */
    fun setIncrement(id: String, increment: Double) = update { current -> current.editProgress(id) { it.copy(increment = increment) } }

    /** Corrects what a finished workout logged. Working weights stay as they are. */
    fun updateWorkout(id: String, exercises: List<ExerciseLog>) = update { current ->
        current.copy(history = current.history.map { if (it.id == id) it.copy(exercises = exercises) else it })
    }

    fun deleteWorkout(id: String) = update { current -> current.copy(history = current.history.filter { it.id != id }) }

    /** Undo of a delete: puts the workout back at [index]. Nothing happens when it is still there. */
    fun restoreWorkout(workout: Workout, index: Int) = update { current ->
        if (current.history.any { it.id == workout.id }) return@update current
        val history = current.history.toMutableList().apply { add(index.coerceIn(0, size), workout) }
        current.copy(history = history)
    }

    private fun AppState.editRoutine(id: String, change: (Routine) -> Routine): AppState =
        copy(routines = routines.map { if (it.id == id) change(it) else it })

    fun addRoutine(): Routine {
        val taken = state.routines.map { it.name }.toSet()
        var number = state.routines.size + 1
        while ("Workout $number" in taken) number++
        val routine = Routine(newId(), "Workout $number", emptyList())
        update { it.copy(routines = it.routines + routine) }
        return routine
    }

    fun renameRoutine(id: String, name: String) = update { current -> current.editRoutine(id) { it.copy(name = name) } }

    /** Changes the rotation order. The next workout is kept by id, so it stays the same workout. */
    fun moveRoutine(from: Int, to: Int) = update { it.copy(routines = it.routines.moved(from, to)) }

    /** The last workout stays: the home screen always needs one to offer. */
    fun removeRoutine(id: String) = update { current ->
        if (current.routines.size <= 1) return@update current
        val routines = current.routines.filter { it.id != id }
        current.copy(routines = routines, nextRoutineId = if (current.nextRoutineId == id) routines[0].id else current.nextRoutineId)
    }

    fun addSlot(routineId: String, exerciseId: String) {
        val exercise = findExercise(exerciseId) ?: return
        update { current ->
            current.ensureProgress(exerciseId).editRoutine(routineId) { routine ->
                if (routine.slots.any { it.exerciseId == exerciseId }) routine else routine.copy(slots = routine.slots + defaultSlot(exercise))
            }
        }
    }

    fun updateSlot(routineId: String, index: Int, slot: Slot) = update { current ->
        current.editRoutine(routineId) { routine ->
            if (index in routine.slots.indices) routine.copy(slots = routine.slots.replaced(index, slot)) else routine
        }
    }

    /** Moves a slot one place up (-1) or down (1). */
    fun moveSlot(routineId: String, index: Int, direction: Int) = moveSlotTo(routineId, index, index + direction)

    /** Moves a slot from [from] to [to], any distance, as one change. For drag and drop. */
    fun moveSlotTo(routineId: String, from: Int, to: Int) = update { current ->
        current.editRoutine(routineId) { routine -> routine.copy(slots = routine.slots.moved(from, to)) }
    }

    fun removeSlot(routineId: String, index: Int) = update { current ->
        current.editRoutine(routineId) { routine -> routine.copy(slots = routine.slots.filterIndexed { at, _ -> at != index }) }
    }

    /** Undo of a remove: puts the slot back at [index] with its sets and reps. Nothing happens when the exercise is in the workout again. */
    fun insertSlot(routineId: String, index: Int, slot: Slot) = update { current ->
        current.ensureProgress(slot.exerciseId).editRoutine(routineId) { routine ->
            if (routine.slots.any { it.exerciseId == slot.exerciseId }) return@editRoutine routine
            routine.copy(slots = routine.slots.toMutableList().apply { add(index.coerceIn(0, size), slot) })
        }
    }

    /** The existing exercise when the name is taken. Null when the name is empty. */
    fun addCustomExercise(name: String, equipment: Equipment): Exercise? {
        val trimmed = name.trim().take(40)
        if (trimmed.isEmpty()) return null
        exercises.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }
        val exercise = customExercise("custom-${newId()}", trimmed, equipment)
        update { it.copy(customExercises = it.customExercises + exercise) }
        return exercise
    }

    /** Removes the exercise from every workout. Finished workouts keep their logs of it. */
    fun deleteCustomExercise(id: String) = update { current ->
        current.copy(
            customExercises = current.customExercises.filter { it.id != id },
            routines = current.routines.map { routine -> routine.copy(slots = routine.slots.filter { it.exerciseId != id }) },
            progress = current.progress - id,
        )
    }

    fun updateSettings(change: (Settings) -> Settings) = update { it.copy(settings = change(it.settings)) }

    /**
     * Converts the working weights. Blocked during a workout so its log stays in one unit, and
     * while a discarded workout can still be undone, for the same reason.
     */
    fun setUnit(unit: WeightUnit) = update { current ->
        val from = current.settings.unit
        if (session is Session.WithRun || unit == from) return@update current
        current.copy(
            progress = current.progress.mapValues { (id, entry) ->
                convertProgress(entry, findExercise(id)?.equipment ?: Equipment.BARBELL, from, unit)
            },
            settings = current.settings.copy(unit = unit, bar = unitDefaults(unit).bar),
        )
    }

    /** The whole state as JSON. The PWA reads the same file. */
    fun exportData(): String = encodeState(state, pretty = true)

    /** False when the text is not a backup of this app or of the PWA. */
    fun importData(json: String): Boolean {
        val parsed = decodeState(json) ?: return false
        // The backup's workout replaces the running one, and a discarded one can no longer come back.
        session = Session.restore(parsed)
        update { parsed }
        lastChanges = null
        return true
    }

    fun resetAll() {
        session = Session.Idle
        update { freshState(it.settings.unit) }
        lastChanges = null
    }
}
