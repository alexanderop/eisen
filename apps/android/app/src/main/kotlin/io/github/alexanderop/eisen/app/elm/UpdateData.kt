package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.core.counted
import io.github.alexanderop.eisen.feature.workout.MAX_BAR
import io.github.alexanderop.eisen.feature.workout.MAX_INCREMENT
import io.github.alexanderop.eisen.feature.workout.MAX_REST_SECONDS
import io.github.alexanderop.eisen.feature.workout.logWithin
import io.github.alexanderop.eisen.feature.workout.slotWithin
import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.app.state.Loaded
import io.github.alexanderop.eisen.feature.workout.Routine
import io.github.alexanderop.eisen.feature.workout.convertProgress
import io.github.alexanderop.eisen.core.customExercise
import io.github.alexanderop.eisen.feature.workout.defaultSlot
import io.github.alexanderop.eisen.app.state.freshState
import io.github.alexanderop.eisen.app.state.loadState
import io.github.alexanderop.eisen.core.unitDefaults

/*
 * The Msgs that edit the saved data: routines, exercises, history, settings, backups, and the
 * Health Connect switch. They mirror the PWA's store.ts. Because the state is immutable, a change
 * names what it changes by id or index where the web mutates an object.
 */

/**
 * The list with the item at [from] taken out and put back at [to]. The same list when an index
 * is outside it, so a stale index from a drag or an old screen changes nothing.
 */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> = mapIndexed { at, item -> if (at == index) value else item }

/** The same model when nothing changed, so a Msg that does nothing hands back what it got. */
internal fun Model.withState(change: (AppState) -> AppState): Model {
    val next = change(state)
    return if (next == state) this else copy(state = next)
}

/** The state with its settings changed. */
internal inline fun AppState.settings(change: (io.github.alexanderop.eisen.app.state.Settings) -> io.github.alexanderop.eisen.app.state.Settings): AppState = copy(settings = change(settings))

private fun AppState.editRoutine(id: String, change: (Routine) -> Routine): AppState =
    copy(routines = routines.map { if (it.id == id) change(it) else it })

// Routines

internal fun routines(msg: Msg.Routines, model: Model, env: Env): Next = when (msg) {
    is Msg.Routines.Select ->
        // The running workout stays what it is; choosing applies to the next one. A routine that is gone
        // (a stale tap) is not chosen: the next workout always names one that exists.
        if (model.state.active != null || model.state.routines.none { it.id == msg.id }) model.only()
        else model.withState { it.copy(nextRoutineId = msg.id) }.only()
    is Msg.Routines.Add -> {
        val taken = model.state.routines.map { it.name }.toSet()
        var number = model.state.routines.size + 1
        while ("Workout $number" in taken) number++
        val routine = Routine(env.newId(), "Workout $number", emptyList())
        val added = model.withState { it.copy(routines = it.routines + routine) }
        (if (msg.openEditor) added.nav { it.copy(routine = routine.id) } else added).only()
    }
    is Msg.Routines.Rename -> model.withState { state -> state.editRoutine(msg.id) { it.copy(name = msg.name.take(ROUTINE_NAME_LENGTH)) } }.only()
    // An empty name would leave a blank card.
    is Msg.Routines.CommitName -> model.withState { state ->
        state.editRoutine(msg.id) { if (it.name.isBlank()) it.copy(name = "Workout") else it }
    }.only()
    // The next workout is kept by id, so it stays the same workout.
    is Msg.Routines.Move -> model.withState { it.copy(routines = it.routines.moved(msg.from, msg.to)) }.only()
    is Msg.Routines.Remove -> {
        val state = model.state
        if (state.routines.size <= 1 || state.routines.none { it.id == msg.id }) {
            model.only()
        } else {
            val routines = state.routines.filter { it.id != msg.id }
            val next = if (state.nextRoutineId == msg.id) routines[0].id else state.nextRoutineId
            val nav = if (model.nav.routine == msg.id) model.nav.copy(routine = null) else model.nav
            model.withState { it.copy(routines = routines, nextRoutineId = next) }.copy(nav = nav).only()
        }
    }
    is Msg.Routines.AddSlot -> addSlot(model, msg.routineId, msg.exerciseId)
    is Msg.Routines.UpdateSlot -> model.withState { state ->
        state.editRoutine(msg.routineId) { routine ->
            // Only the same exercise: a stale index from a card that moved must not overwrite its neighbour.
            if (routine.slots.getOrNull(msg.index)?.exerciseId == msg.slot.exerciseId) {
                routine.copy(slots = routine.slots.replaced(msg.index, slotWithin(msg.slot, state.findExercise(msg.slot.exerciseId)?.timed == true)))
            } else {
                routine
            }
        }
    }.only()
    is Msg.Routines.MoveSlot -> model.withState { state ->
        state.editRoutine(msg.routineId) { it.copy(slots = it.slots.moved(msg.from, msg.to)) }
    }.only()
    is Msg.Routines.RemoveSlot -> removeSlot(msg, model)
    is Msg.Routines.InsertSlot -> insertSlot(model, msg)
    Msg.Routines.UndoRemoveSlot -> {
        val removed = model.undo.removedSlot
        if (removed == null) {
            model.only()
        } else {
            insertSlot(model.undo { it.copy(removedSlot = null) }, Msg.Routines.InsertSlot(removed.routineId, removed.index, removed.slot))
        }
    }
}

private fun addSlot(model: Model, routineId: String, exerciseId: String): Next {
    val exercise = model.state.findExercise(exerciseId) ?: return model.only()
    // A routine that is gone gets nothing, not even a working weight for the exercise.
    if (model.state.routines.none { it.id == routineId }) return model.only()
    return model.withState { state ->
        state.ensureProgress(exerciseId).editRoutine(routineId) { routine ->
            if (routine.slots.any { it.exerciseId == exerciseId }) routine else routine.copy(slots = routine.slots + defaultSlot(exercise))
        }
    }.only()
}

private fun removeSlot(msg: Msg.Routines.RemoveSlot, model: Model): Next {
    val slots = model.state.routines.firstOrNull { it.id == msg.routineId }?.slots ?: return model.only()
    val index = slots.indexOfFirst { it.exerciseId == msg.exerciseId }
    if (index == -1) return model.only()
    val removed = model.withState { state ->
        state.editRoutine(msg.routineId) { it.copy(slots = it.slots.filterIndexed { at, _ -> at != index }) }
    }
    // A tap is felt when the slot goes, so a swipe that removed something feels different from one that did not.
    val felt = if (model.state.settings.haptics) listOf(Cmd.Ui(UiEvent.Haptic(Feel.TAP))) else emptyList()
    if (!msg.offerUndo) return removed and felt
    val name = model.state.findExercise(msg.exerciseId)?.name ?: "Deleted exercise"
    // A second remove replaces the first snackbar, so only the newest one can be undone.
    return removed.undo { it.copy(removedSlot = RemovedSlot(msg.routineId, index, slots[index])) } and felt + Cmd.Ui(
        UiEvent.Snackbar("Removed $name", actionLabel = "Undo", onAction = Msg.Routines.UndoRemoveSlot, tag = SLOT_SNACKBAR),
    )
}

private fun insertSlot(model: Model, msg: Msg.Routines.InsertSlot): Next {
    // An Undo after the routine or the lifter's own exercise was deleted brings nothing back.
    if (model.state.routines.none { it.id == msg.routineId } || model.state.findExercise(msg.slot.exerciseId) == null) return model.only()
    return model.withState { state ->
        state.ensureProgress(msg.slot.exerciseId).editRoutine(msg.routineId) { routine ->
            if (routine.slots.any { it.exerciseId == msg.slot.exerciseId }) return@editRoutine routine
            routine.copy(slots = routine.slots.toMutableList().apply { add(msg.index.coerceIn(0, size), slotWithin(msg.slot, state.findExercise(msg.slot.exerciseId)?.timed == true)) })
        }
    }.only()
}

// Exercises

internal fun exercises(msg: Msg.Exercises, model: Model, env: Env): Next = when (msg) {
    is Msg.Exercises.AddCustom -> {
        val trimmed = msg.name.trim().take(EXERCISE_NAME_LENGTH)
        if (trimmed.isEmpty()) {
            model.only()
        } else {
            // A taken name gives the exercise that has it, built-in or own.
            val existing = exerciseNamed(model, trimmed)
            val exercise = existing ?: customExercise("custom-${env.newId()}", trimmed, msg.equipment, msg.timed)
            val created = if (existing != null) model else model.withState { it.copy(customExercises = it.customExercises + exercise) }
            when (val target = msg.addTo) {
                null -> created.only()
                is AddTarget.Routine -> addSlot(created, target.id, exercise.id)
                AddTarget.Workout -> workout(WorkoutEvent.AddExercise(exercise.id), created, env)
                AddTarget.Wod -> wods(Msg.Wods.AddItem(exercise.id), created, env)
            }
        }
    }
    // Only the lifter's own: a built-in exercise is never deleted, nor taken out of the routines this way.
    is Msg.Exercises.DeleteCustom -> if (model.state.customExercises.none { it.id == msg.id }) model.only() else model.withState { state ->
        state.copy(
            customExercises = state.customExercises.filter { it.id != msg.id },
            routines = state.routines.map { routine -> routine.copy(slots = routine.slots.filter { it.exerciseId != msg.id }) },
            progress = state.progress - msg.id,
        )
    }.only()
}

// History

internal fun history(msg: Msg.History, model: Model): Next = when (msg) {
    is Msg.History.Edit -> model.withState { state ->
        state.copy(history = state.history.map { if (it.id == msg.id) it.copy(exercises = msg.exercises.map(::logWithin)) else it })
    }.only()
    is Msg.History.Delete -> {
        val index = model.state.history.indexOfFirst { it.id == msg.id }
        if (index == -1) {
            model.only()
        } else {
            val workout = model.state.history[index]
            val deleted = model.withState { state -> state.copy(history = state.history.filter { it.id != msg.id }) }
            if (!msg.offerUndo) {
                deleted.only()
            } else {
                // A second delete replaces the first snackbar, so only the newest one can be undone.
                deleted.undo { it.copy(deletedWorkout = DeletedWorkout(workout, index)) } and Cmd.Ui(
                    UiEvent.Snackbar("Workout deleted", actionLabel = "Undo", onAction = Msg.History.UndoDelete, tag = HISTORY_SNACKBAR),
                )
            }
        }
    }
    is Msg.History.Restore -> restoreWorkout(model, msg.workout, msg.index)
    Msg.History.UndoDelete -> {
        val deleted = model.undo.deletedWorkout
        if (deleted == null) model.only()
        else restoreWorkout(model.undo { it.copy(deletedWorkout = null) }, deleted.workout, deleted.index)
    }
    is Msg.History.OpenActivity -> {
        val sheet = ActivitySheet(msg.id)
        // Only over the log, for a timer that is filed and not a WOD.
        if (model.activitySheet == sheet || !activitySheetFits(model, sheet)) model.only() else model.overlay(OverlayEvent.PickActivity(sheet)).only()
    }
    Msg.History.CloseActivity -> model.overlay(OverlayEvent.ActivityClosed).only()
}

private fun restoreWorkout(model: Model, workout: io.github.alexanderop.eisen.feature.workout.Workout, index: Int): Next {
    if (model.state.history.any { it.id == workout.id }) return model.only()
    return model.withState { state ->
        state.copy(history = state.history.toMutableList().apply { add(index.coerceIn(0, size), workout) })
    }.only()
}

// Settings

internal fun settings(msg: Msg.Settings, model: Model): Next {
    fun edit(change: (io.github.alexanderop.eisen.app.state.Settings) -> io.github.alexanderop.eisen.app.state.Settings): Model =
        model.withState { it.settings(change) }
    return when (msg) {
        is Msg.Settings.SetUnit -> setUnit(model, msg)
        is Msg.Settings.SetBar -> edit { it.copy(bar = msg.bar.coerceIn(0.0, MAX_BAR)) }.only()
        is Msg.Settings.SetRestDone -> edit { it.copy(restDone = msg.seconds.coerceIn(0, MAX_REST_SECONDS)) }.only()
        is Msg.Settings.SetRestMissed -> edit { it.copy(restMissed = msg.seconds.coerceIn(0, MAX_REST_SECONDS)) }.only()
        is Msg.Settings.SetSound -> {
            val next = edit { it.copy(sound = msg.on) }
            // The lifter hears what switching it on does.
            if (msg.on && !model.state.settings.sound) next and Cmd.PlayCue(Cue.REST_OVER) else next.only()
        }
        is Msg.Settings.SetHaptics -> edit { it.copy(haptics = msg.on) }.only()
        is Msg.Settings.SetRestFullScreen -> edit { it.copy(restFullScreen = msg.on) }.only()
        is Msg.Settings.SetTrackRir -> edit { it.copy(trackRir = msg.on) }.only()
        is Msg.Settings.SetIncrement -> model.withState { state -> state.editProgress(msg.id) { it.copy(increment = msg.increment.coerceIn(0.0, MAX_INCREMENT)) } }.only()
    }
}

/**
 * Converts the working weights. Blocked during a workout so its log stays in one unit, and
 * while a discarded workout can still be undone, for the same reason.
 */
private fun setUnit(model: Model, msg: Msg.Settings.SetUnit): Next {
    val from = model.state.settings.unit
    if (model.session is Session.WithRun || msg.unit == from) return model.only()
    return model.withState { state ->
        state.copy(
            progress = state.progress.mapValues { (id, entry) ->
                convertProgress(entry, state.findExercise(id)?.equipment ?: Equipment.BARBELL, from, msg.unit)
            },
        ).settings { it.copy(unit = msg.unit, bar = unitDefaults(msg.unit).bar) }
    }.only()
}

// Backups

const val NOT_A_BACKUP = "That file is not an Eisen backup. Nothing was changed."
const val BACKUP_TOO_NEW = "That backup is from a newer version of Eisen. Update the app, then import it again. Nothing was changed."

/** What the lifter is told about a file that could not be imported. */
fun refusal(loaded: Loaded): String? = when (loaded) {
    is Loaded.Ok -> null
    Loaded.NotJson, Loaded.NotEisen -> NOT_A_BACKUP
    is Loaded.TooNew -> BACKUP_TOO_NEW
    is Loaded.Broken -> "That backup is damaged: ${loaded.reason}. Nothing was changed."
}
const val BACKUP_RESTORED = "Backup restored."

internal fun data(msg: Msg.Data, model: Model, env: Env): Next = when (msg) {
    is Msg.Data.Import -> import(model, msg.json)
    is Msg.Data.Restore -> model and Cmd.ReadBackup(msg.uri)
    is Msg.Data.BackupRead -> msg.text?.let { import(model, it) } ?: model.copy(dataNotice = NOT_A_BACKUP).only()
    is Msg.Data.WriteBackup -> model and Cmd.WriteBackup(msg.uri, exportJson(model))
    is Msg.Data.BackupWritten -> model.copy(dataNotice = if (msg.ok) "Backup saved." else "Could not save the backup.").only()
    Msg.Data.SendBackup -> model and Cmd.Ui(UiEvent.ShareBackup(backupFileName(env.now), exportJson(model)))
    Msg.Data.SendFailed -> model.copy(dataNotice = "Could not create the backup.").only()
    Msg.Data.Reset -> {
        val (stopped, cmds) = stopRest(model)
        stopped.nav { it.copy(workoutOpen = false, routine = null) }.copy(
            state = freshState(model.state.settings.unit),
            session = Session.Idle,
            lastChanges = null,
            overlay = overlayAfter(model.overlay, OverlayEvent.Cleared),
            undo = Undo(),
            dataNotice = "All data erased.",
        ) and cmds
    }
}

/** A backup's workout replaces the running one, its rest goes, and a discarded one can no longer come back. */
private fun import(model: Model, json: String): Next {
    val parsed = when (val loaded = loadState(json)) {
        is Loaded.Ok -> loaded.state
        else -> return model.copy(dataNotice = refusal(loaded)).only()
    }
    val (stopped, cmds) = stopRest(model)
    val routine = stopped.nav.routine?.takeIf { id -> parsed.routines.any { it.id == id } }
    return stopped.nav { it.copy(routine = routine) }.copy(
        state = parsed,
        session = Session.restore(parsed),
        lastChanges = null,
        overlay = overlayAfter(model.overlay, OverlayEvent.Cleared),
        undo = Undo(),
        dataNotice = BACKUP_RESTORED,
    ) and cmds
}


// Health Connect

private const val HEALTH_REFUSED = "Health Connect did not allow Eisen to write workouts."
private const val HEALTH_FOOD_REFUSED = "Health Connect did not allow Eisen to write food. Workouts are still synced."

internal fun health(msg: Msg.Health, model: Model): Next {
    val health = model.health
    fun switched(on: Boolean, notice: String? = health.notice): Next {
        val next = model.health { it.copy(enabled = on, notice = notice) }
        return if (on == health.enabled) next.only() else next and Cmd.SaveHealthEnabled(on)
    }
    fun food(allowed: Boolean, next: Next): Next = if (next.first.health.food == allowed) next else next.first.health { it.copy(food = allowed) } to next.second
    return when (msg) {
        Msg.Health.Toggle ->
            if (health.enabled) switched(false, notice = null)
            // Already allowed, for example after turning sync off and on again: no need to ask.
            else model.health { it.copy(notice = null) } and Cmd.Health(HealthJob.CheckPermission(PermissionCheck.TOGGLE))
        is Msg.Health.PermissionChecked -> when (msg.purpose) {
            // Both allowed: on. One missing: Health Connect asks, for the one that is missing.
            PermissionCheck.TOGGLE -> if (msg.granted && msg.food) food(true, switched(true)) else model and Cmd.Ui(UiEvent.RequestHealthPermission)
            // Taken away in Health Connect while the app was away: show the switch as off. A missing permission for
            // food is only noted, never asked for at the start.
            PermissionCheck.STARTUP, PermissionCheck.ROWS -> food(msg.food, if (!msg.granted && health.enabled) switched(false) else model.only())
        }
        is Msg.Health.PermissionResult ->
            // "Write food too" asks while sync is on: a refusal there leaves the workouts on.
            if (health.enabled && msg.granted) food(msg.food, model.health { it.copy(notice = if (msg.food) null else HEALTH_FOOD_REFUSED) }.only())
            else food(msg.food && msg.granted, switched(msg.granted, notice = if (msg.granted) null else HEALTH_REFUSED))
        Msg.Health.AllowFood -> if (!health.enabled || health.food) model.only() else model.health { it.copy(notice = null) } and Cmd.Ui(UiEvent.RequestHealthPermission)
        Msg.Health.FoodRevoked -> if (!health.food) model.only() else model.health { it.copy(food = false) }.only()
        Msg.Health.PermissionRequestFailed -> model.health { it.copy(notice = "Could not open Health Connect.") }.only()
        Msg.Health.SyncPast ->
            if (health.syncing) model.only()
            else model.health { it.copy(syncing = true, notice = "Syncing…") } and
                Cmd.Health(HealthJob.SyncPast(model.state.history, model.state.timerSessions, if (health.food) model.state.foodLog else emptyList()))
        is Msg.Health.SyncedPast -> {
            val count = msg.count
            val notice = when {
                count == null -> "Could not reach Health Connect."
                !health.enabled -> "Health Connect no longer allows Eisen to write workouts."
                msg.foods > 0 -> "${counted(count, "workout")} and ${counted(msg.foods, "food")} synced."
                else -> "${counted(count, "workout")} synced."
            }
            model.health { it.copy(syncing = false, notice = notice) }.only()
        }
        Msg.Health.Revoked -> switched(false)
        // While sync is on, the rows also learn whether food may be written: it may have been allowed in Health Connect.
        Msg.Health.RowsShown -> model and listOfNotNull(Cmd.Health(HealthJob.CheckAvailability), Cmd.Health(HealthJob.CheckPermission(PermissionCheck.ROWS)).takeIf { health.enabled })
        is Msg.Health.Availability ->
            if (msg.availability == health.availability) model.only()
            else model.health { it.copy(availability = msg.availability) }.only()
    }
}
