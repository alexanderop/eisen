package io.github.alexanderop.eisen.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.alexanderop.eisen.domain.AppState
import io.github.alexanderop.eisen.domain.CATALOG
import io.github.alexanderop.eisen.domain.Change
import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.Exercise
import io.github.alexanderop.eisen.domain.ExerciseLog
import io.github.alexanderop.eisen.domain.ExerciseProgress
import io.github.alexanderop.eisen.domain.Outcome
import io.github.alexanderop.eisen.domain.Routine
import io.github.alexanderop.eisen.domain.Settings
import io.github.alexanderop.eisen.domain.Slot
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.Workout
import io.github.alexanderop.eisen.domain.applyProgression
import io.github.alexanderop.eisen.domain.catalogExercise
import io.github.alexanderop.eisen.domain.convertProgress
import io.github.alexanderop.eisen.domain.createLog
import io.github.alexanderop.eisen.domain.createWorkout
import io.github.alexanderop.eisen.domain.customExercise
import io.github.alexanderop.eisen.domain.decodeState
import io.github.alexanderop.eisen.domain.defaultSlot
import io.github.alexanderop.eisen.domain.encodeState
import io.github.alexanderop.eisen.domain.freshState
import io.github.alexanderop.eisen.domain.nextReps
import io.github.alexanderop.eisen.domain.nextRoutineId
import io.github.alexanderop.eisen.domain.outcome
import io.github.alexanderop.eisen.domain.startProgress
import io.github.alexanderop.eisen.domain.unitDefaults
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

private val NO_PROGRESS = ExerciseProgress(weight = 0.0, fails = 0, increment = 0.0)

/**
 * Everything the app knows, as one immutable value. Screens read [state] and call the functions
 * here to change it. Every change is written to a file in the app's private storage.
 */
object Store {
    var state: AppState by mutableStateOf(freshState())
        private set

    /** Warmup sets ticked off in the running workout, as `exerciseId:setIndex`. Not saved. */
    var warmupDone: Set<String> by mutableStateOf(emptySet())

    /** What the last finished workout changed. Shown once on the home screen. */
    var lastChanges: List<Change>? by mutableStateOf(null)

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
    }

    private fun update(change: (AppState) -> AppState) {
        val next = change(state)
        if (next == state) return
        state = next
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

    fun findExercise(id: String): Exercise? = catalogExercise(id) ?: state.customExercises.firstOrNull { it.id == id }

    /** The working weight of an exercise, or its starting weight when it was never used. */
    fun progressOf(id: String): ExerciseProgress {
        state.progress[id]?.let { return it }
        val exercise = findExercise(id)
        if (exercise == null || exercise.equipment == Equipment.BODYWEIGHT) return NO_PROGRESS
        return startProgress(exercise, state.settings.unit)
    }

    /** The state with a working weight for the exercise. Unchanged when it has or needs none. */
    private fun AppState.withProgress(id: String): AppState {
        if (id in progress) return this
        val exercise = findExercise(id)
        if (exercise == null || exercise.equipment == Equipment.BODYWEIGHT) return this
        return copy(progress = progress + (id to startProgress(exercise, settings.unit)))
    }

    private fun AppState.editProgress(id: String, change: (ExerciseProgress) -> ExerciseProgress): AppState {
        val ready = withProgress(id)
        val entry = ready.progress[id] ?: return this
        return ready.copy(progress = ready.progress + (id to change(entry)))
    }

    private fun AppState.editActive(change: (Workout) -> Workout): AppState =
        active?.let { copy(active = change(it)) } ?: this

    private fun Workout.editLog(index: Int, change: (ExerciseLog) -> ExerciseLog): Workout =
        if (index !in exercises.indices) this
        else copy(exercises = exercises.mapIndexed { at, log -> if (at == index) change(log) else log })

    private fun <T> List<T>.replaced(index: Int, value: T): List<T> = mapIndexed { at, item -> if (at == index) value else item }

    val nextRoutine: Routine get() = state.routines.firstOrNull { it.id == state.nextRoutineId } ?: state.routines[0]

    fun selectRoutine(id: String) {
        if (state.active == null) update { it.copy(nextRoutineId = id) }
    }

    /** False when the workout has no exercise left to do. */
    fun startWorkout(): Boolean {
        if (state.active != null) return true
        val routine = nextRoutine
        val ready = routine.slots.fold(state) { current, slot -> current.withProgress(slot.exerciseId) }
        val workout = createWorkout(routine, ::findExercise, ready.progress, ready.settings.unit, System.currentTimeMillis(), newId())
        if (workout.exercises.isEmpty()) return false
        lastChanges = null
        warmupDone = emptySet()
        update { ready.copy(active = workout) }
        return true
    }

    /** Adds an exercise to the running workout only. The workout it was started from stays as it is. */
    fun addActiveExercise(id: String) {
        val workout = state.active ?: return
        val exercise = findExercise(id) ?: return
        if (workout.exercises.any { it.id == id }) return
        update { current ->
            val ready = current.withProgress(id)
            ready.editActive { it.copy(exercises = it.exercises + createLog(exercise, defaultSlot(exercise), ready.progress, ready.settings.unit)) }
        }
    }

    /** The last exercise stays: a workout without exercises has nothing to finish. */
    fun removeActiveExercise(exerciseIndex: Int) = update { current ->
        current.editActive { workout ->
            if (workout.exercises.size > 1) workout.copy(exercises = workout.exercises.filterIndexed { index, _ -> index != exerciseIndex })
            else workout
        }
    }

    /** One tap on a set. Returns what the set shows now, or null when it was cleared or cannot be tapped. */
    fun tapSet(exerciseIndex: Int, setIndex: Int): Int? {
        val log = state.active?.exercises?.getOrNull(exerciseIndex) ?: return null
        val target = log.targetReps ?: return null
        if (setIndex !in log.sets.indices) return null
        val reps = nextReps(log.sets[setIndex], target)
        logReps(exerciseIndex, setIndex, reps)
        return reps
    }

    fun setActiveWeight(exerciseIndex: Int, weight: Double) {
        val log = state.active?.exercises?.getOrNull(exerciseIndex) ?: return
        if (log.equipment == Equipment.BODYWEIGHT) return
        update { current ->
            current.editProgress(log.id) { it.copy(weight = weight) }
                .editActive { workout -> workout.editLog(exerciseIndex) { it.copy(weight = weight) } }
        }
    }

    /** Logs a set. For a max-reps set the lifter enters the count instead of tapping it down. */
    fun logReps(exerciseIndex: Int, setIndex: Int, reps: Int?) = update { current ->
        current.editActive { workout ->
            workout.editLog(exerciseIndex) { log ->
                if (setIndex in log.sets.indices) log.copy(sets = log.sets.replaced(setIndex, reps)) else log
            }
        }
    }

    /** Starting point for a max-reps entry: what this set got last time. */
    fun suggestedReps(id: String, setIndex: Int): Int {
        for (workout in state.history) {
            workout.exercises.firstOrNull { it.id == id }?.sets?.getOrNull(setIndex)?.let { return it }
        }
        return 8
    }

    fun setProgressWeight(id: String, weight: Double) = update { current ->
        current.editProgress(id) { it.copy(weight = weight, fails = 0) }.editActive { workout ->
            workout.copy(exercises = workout.exercises.map { if (it.id == id && it.equipment != Equipment.BODYWEIGHT) it.copy(weight = weight) else it })
        }
    }

    fun setIncrement(id: String, increment: Double) = update { current -> current.editProgress(id) { it.copy(increment = increment) } }

    fun discardWorkout() = update { it.copy(active = null) }

    fun finishWorkout() {
        val workout = state.active ?: return
        if (workout.exercises.all { outcome(it) == Outcome.SKIPPED }) {
            update { it.copy(active = null) }
            return
        }
        val result = applyProgression(state.progress, workout, state.settings.bar)
        val following = nextRoutineId(state.routines, workout.routineId, nextRoutine.id)
        update {
            it.copy(
                progress = result.progress,
                history = listOf(workout.copy(finishedAt = System.currentTimeMillis())) + it.history,
                nextRoutineId = following,
                active = null,
            )
        }
        lastChanges = result.changes
    }

    /** Corrects what a finished workout logged. Working weights stay as they are. */
    fun updateWorkout(id: String, exercises: List<ExerciseLog>) = update { current ->
        current.copy(history = current.history.map { if (it.id == id) it.copy(exercises = exercises) else it })
    }

    fun deleteWorkout(id: String) = update { current -> current.copy(history = current.history.filter { it.id != id }) }

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

    /** The last workout stays: the home screen always needs one to offer. */
    fun removeRoutine(id: String) = update { current ->
        if (current.routines.size <= 1) return@update current
        val routines = current.routines.filter { it.id != id }
        current.copy(routines = routines, nextRoutineId = if (current.nextRoutineId == id) routines[0].id else current.nextRoutineId)
    }

    fun addSlot(routineId: String, exerciseId: String) {
        val exercise = findExercise(exerciseId) ?: return
        update { current ->
            current.withProgress(exerciseId).editRoutine(routineId) { routine ->
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
    fun moveSlot(routineId: String, index: Int, direction: Int) = update { current ->
        current.editRoutine(routineId) { routine ->
            val target = index + direction
            if (index !in routine.slots.indices || target !in routine.slots.indices) return@editRoutine routine
            val slots = routine.slots.toMutableList()
            slots.add(target, slots.removeAt(index))
            routine.copy(slots = slots)
        }
    }

    fun removeSlot(routineId: String, index: Int) = update { current ->
        current.editRoutine(routineId) { routine -> routine.copy(slots = routine.slots.filterIndexed { at, _ -> at != index }) }
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

    /** Converts the working weights. Blocked during a workout so its log stays in one unit. */
    fun setUnit(unit: WeightUnit) = update { current ->
        val from = current.settings.unit
        if (current.active != null || unit == from) return@update current
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
        update { parsed }
        lastChanges = null
        return true
    }

    fun resetAll() {
        update { freshState(it.settings.unit) }
        lastChanges = null
    }
}
