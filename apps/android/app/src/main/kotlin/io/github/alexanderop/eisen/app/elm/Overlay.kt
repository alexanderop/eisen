package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.workout.canRate

/*
 * What lies over the workout screen, as one value: the rest screen (full, put away to the rest bar, or the
 * "set done" screen), the hold timer of a timed set, or the reps question of a max-reps set. One at a
 * time, so no state can hold two of them and no guard has to keep them apart. Every change goes through
 * [overlayAfter]; [fitting] takes away one that no longer belongs to the workout (a backup, an erase, a
 * finished workout, a moved or removed exercise, an ended rest).
 *
 * Two things stay outside on purpose: [Model.rest] (kept in the preferences, it drives the alarms and
 * outlives its screen), and a running interval timer, [Model.timer], which covers everything: while it
 * runs nothing lies over the workout ([fitting]).
 *
 *                              ┌──────────────────── Minimise ──────────────────┐
 *                              │                                                ▼
 *   rest starts ──▶ ┌──────────────────┐ ◀────── Open ──────── ┌────────────────────────┐
 *   (full screen    │ RestScreen.Shown │                       │ RestScreen.Minimised   │ ◀── rest starts
 *   setting on)     └──────────────────┘                       └────────────────────────┘     (setting off)
 *                     │ Rated                                       │ AskReps: the question goes over it,
 *                     ▼                                             ▼ and it comes back when the question goes
 *   last set ──▶ ┌────────────────────┐ Minimise, Rated ──▶ none   ┌──────────────┐ Answered, CloseReps
 *                │ RestScreen.SetDone │                            │ RepsQuestion │ ──────────▶ what it covered
 *                └────────────────────┘                            └──────────────┘
 *   tap on an open timed set ──▶ ┌───────────┐ HoldClosed (Cancel, Stop, logged, Back) ──▶ none
 *                                │ HoldTimer │ the hold stays over a rest screen or a question that would open
 *                                └───────────┘ under it
 *   from every state: Cleared (an erase, a backup) ──▶ none; Reordered closes a hold timer, which names its set by place
 *   Rest, SetDone, AskReps and Hold replace a rest screen or a question: the newest thing the lifter did shows.
 */

/** One thing over the workout screen. [Model.overlay] is null when nothing is. */
sealed interface Overlay

/**
 * The rest screen of set [set] of the exercise at [exercise] in the running workout: the set that started
 * the rest, which its wheels correct and its chips rate. [exerciseId] makes sure it is still the same
 * exercise there.
 */
sealed interface RestScreen : Overlay {
    val exercise: Int
    val set: Int
    val exerciseId: String

    /** Over the whole workout screen, with the clock of the running [Model.rest]. */
    data class Shown(override val exercise: Int, override val set: Int, override val exerciseId: String) : RestScreen

    /** Put away by the lifter: the rest bar shows instead, and a tap on it brings the screen back. */
    data class Minimised(override val exercise: Int, override val set: Int, override val exerciseId: String) : RestScreen

    /**
     * The same screen without a clock after the last set of an exercise, which starts no rest. It is there
     * to rate that set. It never minimises: put away, it is gone.
     */
    data class SetDone(override val exercise: Int, override val set: Int, override val exerciseId: String) : RestScreen
}

/**
 * The hold timer of set [set] of the exercise at [exercise] in the running workout, a timed one.
 * [exerciseId] makes sure it is still the same exercise there. Based on timestamps like the rest, so it
 * stays right while the app sleeps: "Get ready" until [startsAt], then the hold counts down from
 * [target] seconds, or up from 0 when [target] is null (as long as possible). [started] is true once
 * the start of the hold was signalled, so its beep comes once.
 */
data class HoldTimer(
    val exercise: Int,
    val set: Int,
    val exerciseId: String,
    val target: Int?,
    val startsAt: Long,
    val started: Boolean = false,
) : Overlay

/**
 * The max-reps set [set] of the exercise at [exercise] being asked about, and the count the entry starts
 * at. [exerciseId] makes sure it is still the same exercise there. [under]: the rest screen that was put
 * away when the question came, which is back when the question goes.
 */
data class RepsQuestion(
    val exercise: Int,
    val set: Int,
    val exerciseId: String,
    val reps: Int,
    val under: RestScreen.Minimised? = null,
) : Overlay

/** What happens to the overlay. */
sealed interface OverlayEvent {
    /** A rest started for a set, or the last set of an exercise was logged: its screen, [screen]. */
    data class Rest(val screen: RestScreen) : OverlayEvent

    /** A max-reps set asks for its count. */
    data class AskReps(val question: RepsQuestion) : OverlayEvent

    /** The count of a max-reps set was logged: the question is answered. */
    data object Answered : OverlayEvent

    /** The question was put away without an answer. */
    data object CloseReps : OverlayEvent

    /** An open timed set was tapped: its hold timer opens. */
    data class Hold(val timer: HoldTimer) : OverlayEvent

    /** The hold timer closes: cancelled, stopped, logged, or Back. */
    data object HoldClosed : OverlayEvent

    /** The rest screen is put away: a swipe down, the chevron or Back. */
    data object Minimise : OverlayEvent

    /** The rest bar was tapped. */
    data object Open : OverlayEvent

    /** A chip of [screen] was chosen and its moment to land is over. */
    data class Rated(val screen: RestScreen) : OverlayEvent

    /** Exercises were moved, put off or removed. A hold timer names its set by place: it closes without logging. */
    data object Reordered : OverlayEvent

    /** The workout was erased or replaced by a backup. */
    data object Cleared : OverlayEvent
}

/** The overlay after [event]: the one place it changes. An event a state does not take leaves it as it is. */
fun overlayAfter(overlay: Overlay?, event: OverlayEvent): Overlay? = when (event) {
    // The hold timer covers the workout: what would open under it does not.
    is OverlayEvent.Rest -> if (overlay is HoldTimer) overlay else event.screen
    is OverlayEvent.AskReps -> when (overlay) {
        is HoldTimer -> overlay
        is RestScreen.Minimised -> event.question.copy(under = overlay)
        is RepsQuestion -> event.question.copy(under = overlay.under)
        null, is RestScreen.Shown, is RestScreen.SetDone -> event.question
    }
    OverlayEvent.Answered, OverlayEvent.CloseReps -> if (overlay is RepsQuestion) overlay.under else overlay
    is OverlayEvent.Hold -> event.timer
    OverlayEvent.HoldClosed -> if (overlay is HoldTimer) null else overlay
    OverlayEvent.Minimise -> when (overlay) {
        is RestScreen.Shown -> RestScreen.Minimised(overlay.exercise, overlay.set, overlay.exerciseId)
        is RestScreen.SetDone -> null
        null, is RestScreen.Minimised, is HoldTimer, is RepsQuestion -> overlay
    }
    OverlayEvent.Open -> if (overlay is RestScreen.Minimised) RestScreen.Shown(overlay.exercise, overlay.set, overlay.exerciseId) else overlay
    is OverlayEvent.Rated -> if (overlay == event.screen) null else overlay
    OverlayEvent.Reordered -> if (overlay is HoldTimer) null else overlay
    OverlayEvent.Cleared -> null
}

/** [model] with [event] applied to its overlay. */
internal fun Model.overlay(event: OverlayEvent): Model {
    val next = overlayAfter(overlay, event)
    return if (next == overlay) this else copy(overlay = next)
}

/** The hold timer, when it is what lies over the workout. */
val Model.hold: HoldTimer? get() = overlay as? HoldTimer

/** The rest screen, full, put away or "set done", when it is what lies over the workout. */
val Model.restScreen: RestScreen? get() = overlay as? RestScreen

/** The reps question, when it is what lies over the workout. */
val Model.repsQuestion: RepsQuestion? get() = overlay as? RepsQuestion

/**
 * [overlay] if it still belongs to the running workout, or what is left of it: a question whose set is
 * gone gives back the screen it covered, if that still fits. Nothing lies over the workout while an
 * interval timer runs: it covers everything. [follow] runs it after every Msg.
 */
fun fitting(model: Model, overlay: Overlay?): Overlay? = when {
    model.timer is TimerState.Running -> null
    else -> fits(model, overlay)
}

private fun fits(model: Model, overlay: Overlay?): Overlay? = when (overlay) {
    null -> null
    is HoldTimer -> overlay.takeIf { holdFits(model, it) }
    is RestScreen -> overlay.takeIf { restScreenFits(model, it) }
    is RepsQuestion -> if (repsQuestionFits(model, overlay)) overlay else fits(model, overlay.under)
}

/**
 * The rest screen still belongs to the running rest and to a logged set of its exercise in the running
 * workout. The "set done" screen has no rest: it belongs to the workout screen and a set that can still be
 * rated, so it leaves nothing behind when either goes.
 */
fun restScreenFits(model: Model, screen: RestScreen): Boolean {
    when (screen) {
        is RestScreen.SetDone -> if (!model.nav.workoutOpen || !model.state.settings.trackRir) return false
        is RestScreen.Shown, is RestScreen.Minimised -> if (model.rest == null) return false
    }
    val log = model.session.active?.exercises?.getOrNull(screen.exercise) ?: return false
    return log.id == screen.exerciseId && log.sets.getOrNull(screen.set) != null && (screen !is RestScreen.SetDone || canRate(log, screen.set))
}

/** The hold timer still belongs to an open set of a timed exercise of the running workout, which shows. */
fun holdFits(model: Model, hold: HoldTimer): Boolean {
    val session = model.session as? Session.Running ?: return false
    val log = session.run.workout.exercises.getOrNull(hold.exercise) ?: return false
    return model.nav.workoutOpen && log.id == hold.exerciseId && log.timed && log.sets.getOrNull(hold.set) == null && hold.set in log.sets.indices
}

/** The reps question still asks about a max-reps set of the workout, which shows. */
fun repsQuestionFits(model: Model, question: RepsQuestion): Boolean {
    val log = model.session.active?.exercises?.getOrNull(question.exercise) ?: return false
    return model.nav.workoutOpen && log.id == question.exerciseId && !log.timed && log.targetReps == null && question.set in log.sets.indices
}
