package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.core.Equipment
import io.github.alexanderop.eisen.core.loadText
import io.github.alexanderop.eisen.feature.workout.loadText
import io.github.alexanderop.eisen.feature.workout.targetText

/*
 * What the app shows outside itself: the "Next workout" home-screen widget and the launcher
 * shortcuts. Both follow the saved state only, so `update` compares them before and after a change
 * and asks for a redraw (Cmd.RefreshHomeSurfaces) only when they differ. The widget and shortcut
 * adapters draw exactly this and decide nothing.
 */

/**
 * One exercise as the home screen's "Next up" card shows it. [load] is "80kg", "BW" or "BW + 10kg".
 * [reps] are seconds to hold when [timed].
 */
data class WidgetRow(val name: String, val sets: Int, val reps: Int?, val load: String, val timed: Boolean = false) {
    /** "5×5", "3×max" or "3×1:00". */
    val scheme: String get() = "$sets×${targetText(reps, timed)}"
}

/** What the widget shows: the running workout, or the next one as it is set up right now. */
data class WidgetContent(
    val running: Boolean,
    /** The workout the button starts. Null while one runs, or when there is no workout at all. */
    val routineId: String?,
    val name: String,
    val rows: List<WidgetRow>,
    val logged: Int,
    val total: Int,
) {
    /** A workout without exercises cannot start, like the disabled button on the home screen. */
    val canStart: Boolean get() = running || (routineId != null && rows.isNotEmpty())
}

fun widgetContent(state: AppState): WidgetContent {
    val unit = state.settings.unit
    val active = state.active
    if (active != null) {
        val sets = active.exercises.flatMap { it.sets }
        return WidgetContent(
            running = true,
            routineId = null,
            name = active.name,
            rows = active.exercises.map { log ->
                WidgetRow(log.name, log.sets.size, log.targetReps, loadText(log.equipment == Equipment.BODYWEIGHT, log.weight, unit), log.timed)
            },
            logged = sets.count { it != null },
            total = sets.size,
        )
    }
    // nextRoutine assumes at least one workout. A broken import must not take the widget down with it.
    val next = state.routines.firstOrNull { it.id == state.nextRoutineId } ?: state.routines.firstOrNull()
        ?: return WidgetContent(running = false, routineId = null, name = "Eisen", rows = emptyList(), logged = 0, total = 0)
    val rows = next.slots.mapNotNull { slot ->
        val exercise = state.findExercise(slot.exerciseId) ?: return@mapNotNull null
        val weight = progressOf(state, exercise.id).weight
        WidgetRow(exercise.name, slot.sets, slot.reps, loadText(exercise.equipment == Equipment.BODYWEIGHT, weight, unit), exercise.timed)
    }
    return WidgetContent(running = false, routineId = next.id, name = next.name, rows = rows, logged = 0, total = 0)
}

/**
 * A launcher shortcut. [routineId] is the workout it starts; null resumes the running one, the same
 * way the widget's button does. Its place in the list is its rank.
 */
data class ShortcutSpec(val id: String, val shortLabel: String, val longLabel: String, val routineId: String?)

/** Launchers show four at most. The phone may allow fewer; the adapter cuts the list to that. */
const val MAX_SHORTCUTS = 4

/** During a workout only "Resume". Otherwise one per workout, the one offered next first. */
fun shortcutsOf(state: AppState): List<ShortcutSpec> {
    if (state.active != null) return listOf(ShortcutSpec("resume", "Resume", "Resume workout", routineId = null))
    val routines = state.routines
    val next = routines.firstOrNull { it.id == state.nextRoutineId } ?: routines.firstOrNull() ?: return emptyList()
    return (listOf(next) + routines.filter { it.id != next.id }).take(MAX_SHORTCUTS).map { routine ->
        // Android refuses a shortcut without a label.
        val name = routine.name.ifBlank { "Workout" }
        ShortcutSpec("routine-${routine.id}", name, "Start $name", routine.id)
    }
}

/** The redraw of the widget and the shortcuts for [state]. */
fun refreshHomeSurfaces(state: AppState): Cmd.RefreshHomeSurfaces = Cmd.RefreshHomeSurfaces(widgetContent(state), shortcutsOf(state))
