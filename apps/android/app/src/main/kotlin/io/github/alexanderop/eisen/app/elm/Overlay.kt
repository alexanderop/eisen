package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.feature.workout.canRate

/*
 * What lies over the workout screen, as one value: the rest screen (full, put away to the rest bar, or the
 * "set done" screen), the hold timer of a timed set, or the reps question of a max-reps set; and over the
 * history's log the sheet "What was it?" of a finished timer ([ActivitySheet]). One at a
 * time, so no state can hold two of them and no guard has to keep them apart. Every change goes through
 * [overlayAfter]; [fitting] follows a moved card by its exercise id, takes away one that no longer belongs
 * to the workout (a backup, an erase, a finished workout, a removed exercise, an ended rest), and puts the
 * screen of a running rest back, put away to the rest bar, when nothing else lies there (after a restart,
 * or when a hold timer or a question over it went): a rest can always be opened.
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
 *   from every state: Cleared (an erase, a backup) ──▶ none; Reordered closes a hold timer, which names its set by place;
 *   none while a rest of a logged set runs ──▶ RestScreen.Minimised of that set ([fitting])
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

/**
 * The sheet "What was it?" over the history's log, for the finished timer [sessionId]. One thing over the
 * screen like the others, so it never lies over a rest screen or a question. It is gone ([fitting]) once the
 * log is not what shows (another tab, Progress, the workout, a running timer) or its timer left the history
 * (an erase, a backup); a rest that runs is back in the bar then. Never kept past the end of the process.
 *
 *            OpenActivity(id)                  CloseActivity, Back, another tab, Progress,
 *   ┌──────┐ ───────────────▶ ┌────────────┐   the workout or a timer on screen, the timer gone,
 *   │ none │                  │ Sheet(id)  │ ─────────────────────────────────────────────────▶ none
 *   └──────┘                  └────────────┘   (or the rest bar, when a rest runs)
 *                               │      ▲
 *                               └──────┘ SetActivity: the sheet stays, the chip lights
 */
data class ActivitySheet(val sessionId: String) : Overlay

/** What happens to the overlay. */
sealed interface OverlayEvent {
    /** A finished timer in the history was tapped: its sheet opens. */
    data class PickActivity(val sheet: ActivitySheet) : OverlayEvent

    /** The sheet "What was it?" is put away. */
    data object ActivityClosed : OverlayEvent

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
        // The question goes over the rest screen, which is back put away when the question goes.
        is RestScreen.Shown -> event.question.copy(under = RestScreen.Minimised(overlay.exercise, overlay.set, overlay.exerciseId))
        is RepsQuestion -> event.question.copy(under = overlay.under)
        null, is RestScreen.SetDone, is ActivitySheet -> event.question
    }
    OverlayEvent.Answered, OverlayEvent.CloseReps -> if (overlay is RepsQuestion) overlay.under else overlay
    is OverlayEvent.Hold -> event.timer
    OverlayEvent.HoldClosed -> if (overlay is HoldTimer) null else overlay
    OverlayEvent.Minimise -> when (overlay) {
        is RestScreen.Shown -> RestScreen.Minimised(overlay.exercise, overlay.set, overlay.exerciseId)
        is RestScreen.SetDone -> null
        null, is RestScreen.Minimised, is HoldTimer, is RepsQuestion, is ActivitySheet -> overlay
    }
    // The log shows: a rest screen there is put away, and comes back to the bar when the sheet goes ([fitting]).
    is OverlayEvent.PickActivity -> event.sheet
    OverlayEvent.ActivityClosed -> if (overlay is ActivitySheet) null else overlay
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
    // A running rest can always be opened: with nothing over the workout, its screen lies put away to the
    // rest bar (after a restart, a closed hold timer or reps question).
    null -> restBar(model)
    is HoldTimer -> overlay.takeIf { holdFits(model, it) } ?: restBar(model)
    is RestScreen -> followed(model, overlay).takeIf { restScreenFits(model, it) } ?: restBar(model)
    is RepsQuestion -> followed(model, overlay).takeIf { repsQuestionFits(model, it) } ?: fits(model, overlay.under)
    is ActivitySheet -> overlay.takeIf { activitySheetFits(model, it) } ?: restBar(model)
}

/** The sheet's timer is in the history, not a WOD, and the log it lies over is what shows (not the workout or the Food screen over it). */
fun activitySheetFits(model: Model, sheet: ActivitySheet): Boolean =
    model.nav.tab == Tab.HISTORY && model.nav.history == HistoryView.LOG && !showsWorkout(model) && model.timer == null && model.food.day == null &&
        model.state.timerSessions.any { it.id == sheet.sessionId && it.wodId == null }

/** The sheet "What was it?", when it is what lies over the screen. */
val Model.activitySheet: ActivitySheet? get() = overlay as? ActivitySheet

/** Where the exercise of [screen] is now: a card moved, put off or one before it removed. Unchanged when it is gone. */
private fun followed(model: Model, screen: RestScreen): RestScreen {
    val at = model.session.active?.exercises?.indexOfFirst { it.id == screen.exerciseId } ?: -1
    if (at == -1 || at == screen.exercise) return screen
    return when (screen) {
        is RestScreen.Shown -> screen.copy(exercise = at)
        is RestScreen.Minimised -> screen.copy(exercise = at)
        is RestScreen.SetDone -> screen.copy(exercise = at)
    }
}

private fun followed(model: Model, question: RepsQuestion): RepsQuestion {
    val at = model.session.active?.exercises?.indexOfFirst { it.id == question.exerciseId } ?: -1
    val under = question.under?.let { followed(model, it) as? RestScreen.Minimised }
    return if (at == -1 || at == question.exercise) question.copy(under = under) else question.copy(exercise = at, under = under)
}

/** The running rest's screen put away to the rest bar, when the rest names a logged set of the workout. */
private fun restBar(model: Model): RestScreen.Minimised? {
    val rest = model.rest ?: return null
    val id = rest.exerciseId ?: return null
    val set = rest.set ?: return null
    val at = model.session.active?.exercises?.indexOfFirst { it.id == id } ?: return null
    return RestScreen.Minimised(at, set, id).takeIf { at != -1 && restScreenFits(model, it) }
}

/**
 * The rest screen still belongs to the running rest and to a logged set of its exercise in the running
 * workout. The "set done" screen has no rest: it belongs to the workout screen and a set that can still be
 * rated, so it leaves nothing behind when either goes.
 */
fun restScreenFits(model: Model, screen: RestScreen): Boolean {
    when (screen) {
        is RestScreen.SetDone -> if (!model.nav.workoutOpen || !model.state.settings.trackRir) return false
        is RestScreen.Shown, is RestScreen.Minimised -> {
            val rest = model.rest ?: return false
            // The screen of the set that started the rest: a rest saved by an older build names none.
            if (rest.exerciseId != null && (rest.exerciseId != screen.exerciseId || rest.set != screen.set)) return false
        }
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
