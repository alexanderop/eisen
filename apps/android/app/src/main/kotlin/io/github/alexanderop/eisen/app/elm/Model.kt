package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingPlan
import io.github.alexanderop.eisen.feature.timer.boxing.BoxingRound
import io.github.alexanderop.eisen.feature.workout.Change
import io.github.alexanderop.eisen.feature.workout.Slot
import io.github.alexanderop.eisen.feature.timer.TimerConfig
import io.github.alexanderop.eisen.feature.timer.TimerMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import io.github.alexanderop.eisen.feature.timer.TimerPreset
import io.github.alexanderop.eisen.feature.timer.TimerRun
import io.github.alexanderop.eisen.feature.timer.TimerSession
import io.github.alexanderop.eisen.feature.timer.wod.Wod
import io.github.alexanderop.eisen.feature.workout.Workout
import io.github.alexanderop.eisen.app.state.freshState

/**
 * Everything that decides what the app does, as one immutable value. Only [update] makes a new one.
 *
 * Only [state] is saved to the file the PWA shares. [rest], [health] and [notifications] are kept by
 * Android-only preferences, the running [timer] by a file of its own (see `runtime/`). Everything else
 * lives while the process lives.
 */
data class Model(
    /** The saved data. Its JSON shape and `STATE_VERSION` are shared with the PWA and must not change. */
    val state: AppState,
    /**
     * The running workout and the question asked about it, see `app/elm/WorkoutMachine.kt`.
     * `state.active` is always `session.active`: the workout belongs to the session.
     */
    val session: Session = Session.restore(state),
    /**
     * Null when no rest runs. A rest that is over stays until it is dismissed. Its own field, not part of
     * [overlay]: it is kept in the preferences, drives the alarms and outlives its screen.
     */
    val rest: RestState? = null,
    /** What lies over the workout screen: the rest screen, the hold timer or the reps question. See [Overlay]. */
    val overlay: Overlay? = null,
    /** What the last finished workout changed. Shown once on the home screen. */
    val lastChanges: List<Change>? = null,
    /** Warmup sets ticked off in the running workout, as [warmupKey]. */
    val warmupDone: Set<String> = emptySet(),
    /** The exercise tapped last, which holds its place on the workout screen for a moment. */
    val settling: Settling? = null,
    /** The setup of an interval timer open in the Timer tab. Null shows the modes and the saved timers. */
    val timerSetup: TimerSetup? = null,
    /** A running interval timer, or the result of the one that just ended. Shown over everything else. */
    val timer: TimerState? = null,
    /** Boxing: the rounds the lifter took off the end of the mode's setup, kept while the process lives. */
    val boxingRemoved: List<BoxingRound> = emptyList(),
    /** Start was tapped before the app ever asked to show notifications: the run starts once that is answered. */
    val pendingStart: PendingStart? = null,
    /** The WODs page of the Timer tab: the filter of the benchmarks, the WOD open in its detail and the builder. */
    val wods: WodsModel = WodsModel(),
    val nav: Navigation = Navigation(workoutOpen = state.active != null),
    /** What an Undo snackbar can still bring back. The discarded workout lives in [session]. */
    val undo: Undo = Undo(),
    val health: HealthModel = HealthModel(),
    val notifications: Notifications = Notifications(granted = true, asked = false),
    /** The status line under "Your data" in Settings. */
    val dataNotice: String? = null,
    /** True while the activity is on screen. Decides which [subscriptions] run. */
    val visible: Boolean = false,
    /**
     * The clock as of the start, the last [Msg.Rest.Tick], [Msg.Hold.Tick] or [Msg.Timer], [Msg.App.Foreground],
     * change of screen ([Msg.Nav], a workout that starts, asks to discard or ends). Ticks keep it fresh every
     * second only while a rest runs and the app shows, while a hold timer is open, or while an interval timer runs. Views read the time only from here:
     * the home screen's date and "last 7 days", the discard question's "N min", the Progress calendar, the timers.
     */
    val now: Long = 0,
)

/*
 * Elm's record update, `{ model | wods = change model.wods }`: the model with one part changed.
 * A part that is null stays null.
 */
internal inline fun Model.wods(change: (WodsModel) -> WodsModel): Model = copy(wods = change(wods))
internal inline fun Model.nav(change: (Navigation) -> Navigation): Model = copy(nav = change(nav))
internal inline fun Model.undo(change: (Undo) -> Undo): Model = copy(undo = change(undo))
internal inline fun Model.health(change: (HealthModel) -> HealthModel): Model = copy(health = change(health))
internal inline fun Model.notifications(change: (Notifications) -> Notifications): Model = copy(notifications = change(notifications))
internal inline fun Model.timerSetup(change: (TimerSetup) -> TimerSetup): Model = copy(timerSetup = timerSetup?.let(change))

/** The hold timer changed in place, while it is open: it is the same timer, so no transition of the [Overlay]. */
internal inline fun Model.hold(change: (HoldTimer) -> HoldTimer): Model = (overlay as? HoldTimer)?.let { copy(overlay = change(it)) } ?: this

/** A running rest. Based on timestamps, so it stays right when the app is closed. */
data class RestState(val startedAt: Long, val endsAt: Long, val note: String)

/**
 * The exercise [id] was tapped at [since]. It counts as open in `order` even when that tap finished it,
 * so the card does not jump away under the thumb: the tap after a full set is how missed reps are
 * entered. After [SETTLE_MS] it takes its place, see [Msg.WorkoutScreen.Settle].
 */
data class Settling(val id: String, val since: Long)

/**
 * The setup of an interval timer: the numbers on its wheels, not yet started. [presetId] is the saved
 * timer it was opened from or saved as, which "Update preset" overwrites with [config] and [name],
 * the name as it is being typed.
 *
 * A boxing setup also edits the plan in [config]: [sheet] is the round whose sheet is open, and
 * [template] the Undo a template offers next to the chips.
 */
data class TimerSetup(
    val config: TimerConfig,
    val presetId: String? = null,
    val name: String = "",
    /** Boxing: the open round sheet, null while none is open. See [RoundSheet]. */
    val sheet: RoundSheet? = null,
    /** Boxing: the Undo of the template applied last, null while none is offered. See [TemplateUndo]. */
    val template: TemplateUndo? = null,
    /** Boxing: the rounds taken off the end by a lower number of rounds, which a higher one brings back. */
    val removed: List<BoxingRound> = emptyList(),
    /** Boxing: the combo builder, null while it is closed. See [ComboBuild]. */
    val combo: ComboBuild? = null,
)

/**
 * The combo builder over a boxing setup. Closed is no builder at all (null). Which one is open decides
 * what Save does: a new combination, or new moves for one of the lifter's own.
 *
 *              NewCombo(round)                       SaveOwn, CloseCombo,
 *   ┌────────┐ ──────────────▶ ┌──────────────────┐  DeleteOwn of its combination
 *   │ Closed │  EditOwn(id)    │ New(round)       │ ───────────────────────────▶ Closed
 *   └────────┘ ──────────────▶ │ Edit(id)         │
 *                              └──────────────────┘  another NewCombo or EditOwn: ignored
 */
sealed interface ComboBuild {
    /**
     * A new combination. [round]: the round whose "My combos" tile opened it, which becomes a "My combos"
     * round when it is saved; null when "New combination" under "My combos" opened it.
     */
    data class New(val round: Int?) : ComboBuild

    /** New moves for the lifter's own combination [id]. */
    data class Edit(val id: String) : ComboBuild
}

/**
 * The sheet of round [index] of a boxing setup, open over it. Closed is no sheet at all (null), so
 * nothing of a sheet outlives it. What it shows belongs to its round: [copied] says "Copied to all 6
 * rounds" until the plan changes again, [heard] names the combination "Listen" played last, and the
 * next Listen plays the one after it.
 *
 *              OpenRound(i)                       CopyToAll: copied     Listen: heard
 *   ┌────────┐ ────────────▶ ┌────────────────┐   another edit: not copied
 *   │ Closed │               │ Open(i, ...)   │   EditRound with another theme: nothing heard
 *   └────────┘ ◀──────────── └────────────────┘   MoveRound, DuplicateRound, RemoveRound of another
 *       CloseRound, Back,       │         ▲        round: the same round at its new place, as it was
 *       Edit to fewer rounds    └─────────┘
 *       than i+1               OpenRound(j), RemoveRound(i): another round, a fresh sheet
 *
 * See [sheetAfter], where every change of the sheet is decided.
 */
data class RoundSheet(val index: Int, val copied: Boolean = false, val heard: String? = null, val picks: Picks? = null)

/**
 * The list of the round's combinations to check, open over its sheet ([RoundSheet.picks], null while it is
 * closed). What it shows is [pickChoice]: the round's pick says it, except right after "None".
 *
 *            OpenPicks              PickNone                  TogglePick(id): only id
 *   ┌──────┐ ───────▶ ┌───────┐ ──────────────▶ ┌──────────┐ ───────────────────────▶ SHOWN
 *   │ null │          │ SHOWN │                 │ NONE_YET │
 *   └──────┘ ◀─────── └───────┘ ◀────────────── └──────────┘ ClosePicks: null, the round calls all
 *            ClosePicks          PickAll
 */
enum class Picks {
    /** What the round calls: all of its combinations (its pick is empty) or the ones checked. */
    SHOWN,

    /**
     * "None" was tapped: every box is unchecked, and the next one checked is the only one. A round never
     * calls none: until one is checked it calls all of them, and that is what closing the list keeps.
     */
    NONE_YET,
}

/** What the list of a round's combinations shows checked. */
sealed interface PickChoice {
    /** Every combination: the round's pick is empty. */
    data object All : PickChoice

    /** None checked after "None"; the round still calls all of them. */
    data object NoneYet : PickChoice

    /** The ones in the round's pick. */
    data class Some(val ids: List<String>) : PickChoice
}

/** What the list shows for [round] with its list in state [picks]. */
fun pickChoice(round: BoxingRound, picks: Picks?): PickChoice = when {
    round.pick.isNotEmpty() -> PickChoice.Some(round.pick)
    picks == Picks.NONE_YET -> PickChoice.NoneYet
    else -> PickChoice.All
}

/**
 * A template was applied [at] over the plan [before]: its Undo brings [before] back, until the next
 * edit of the plan, or [TEMPLATE_UNDO_MILLIS] after [at] (`ForgetTemplate(at)`). Both or neither.
 *
 *            ApplyTemplate                 UndoTemplate (plan = before), ForgetTemplate(at),
 *   ┌──────┐ ─────────────▶ ┌──────────┐   Edit, SetStyle and every other edit of the plan
 *   │ None │                │ Offered  │ ─────────────────────────────────────────────────────▶ None
 *   └──────┘                └──────────┘   ApplyTemplate again: Offered over the plan of now
 */
data class TemplateUndo(val before: BoxingPlan, val at: Long)

/** A run that waits for the answer to the notification question, see [Model.pendingStart]. */
data class PendingStart(val config: TimerConfig, val name: String, val wod: Wod? = null)

/** The interval timer over everything else: running, or its result. Never both. */
sealed interface TimerState {
    /**
     * [ending]: "End timer?" is asked. The run goes on meanwhile. [tappedAt]: the last "+1 round" that
     * counted, so a second tap right after it (a bounce of a sweaty thumb) does not.
     */
    data class Running(val run: TimerRun, val ending: Boolean = false, val tappedAt: Long? = null) : TimerState

    /**
     * The result of the run that ended, with "Done" and "Again". [wod]: the WOD it was a run of, as it ran.
     * [undo]: the run as it was when its last tap stopped the clock, so "Not finished? Undo" can take
     * that tap back and let it go on. [at]: when it showed; taps right after it are the thumb still on the button.
     * [combos]: the combinations a boxing run called up to where it ended, null for the other modes.
     */
    data class Finished(val session: TimerSession, val wod: Wod? = null, val undo: TimerRun? = null, val at: Long = 0, val combos: Int? = null) : TimerState
}

/** The two pages of the Timer tab, switched at its top. */
enum class TimerPage { WODS, TIMERS }

/** The chips over the benchmarks. */
enum class WodFilter(val label: String) {
    ALL("All"),
    NO_WEIGHTS("No weights"),
    FOR_TIME("For Time"),
    AMRAP("AMRAP"),
}

/**
 * [filter]: the benchmarks shown, kept while the process lives. [detail]: the id of the WOD whose
 * detail shows, one of the lifter's own or of the library. [builder]: the builder, over the detail or
 * the list, and the drafts kept from before, see [Builder].
 * [query]: what is typed in the search field, kept while the process lives, also over a detail.
 * [suggesting]: the suggestions under the field are open; never without something typed.
 */
data class WodsModel(
    val filter: WodFilter = WodFilter.ALL,
    val detail: String? = null,
    val builder: Builder = Builder.Closed(),
    val query: String = "",
    val suggesting: Boolean = false,
)

/** The draft in the builder, open or asking "Discard changes?". Null while the builder is closed. */
val WodsModel.draft: WodDraft? get() = builder.draft

/** The draft "Continue editing" offers: the newest one kept from before. Only while the builder is closed. */
val WodsModel.pending: WodDraft? get() = (builder as? Builder.Closed)?.kept?.firstOrNull()

/**
 * The WOD builder. [kept] are drafts the process had when it ended, newest first, offered one at a time
 * as "Continue editing" until each is continued or discarded. They wait under an open builder and are
 * offered again when it closes. The draft file holds the open draft and every kept one, see [draftFile],
 * so none of them is lost when the process ends at any moment.
 *
 *                      New, Edit, Copy                      Leave with changes
 *   ┌──────────────┐ ─────────────────▶ ┌──────────────┐ ───────────────────▶ ┌──────────────┐
 *   │ Closed(kept) │                    │ Editing(d)   │                      │ Asking(d)    │
 *   │              │ ◀───────────────── │              │ ◀─── KeepEditing ─── │ "Discard     │
 *   └──────────────┘  Save, Discard,    └──────────────┘                      │  changes?"   │
 *     │   ▲  ▲        Leave without       ▲  every edit                       └──────────────┘
 *     │   │  │        changes, Delete     │  stays in its state                 │ Discard, Save
 *     │   │  │        of its WOD          │                                     │
 *     │   │  └────────────────────────────┼─────────────────────────────────────┘
 *     │   │ DiscardDraft: the first kept  │
 *     │   │ one goes                      │ ContinueDraft: the first kept one opens,
 *     └───┴───────────────────────────────┘ the others wait under it
 *
 * See [builderAfter], the one place the state changes. An edit changes the draft in its state.
 */
sealed interface Builder {
    val kept: List<WodDraft>

    data class Closed(override val kept: List<WodDraft> = emptyList()) : Builder

    data class Editing(val open: WodDraft, override val kept: List<WodDraft> = emptyList()) : Builder

    /** "Discard changes?" over the builder. */
    data class Asking(val open: WodDraft, override val kept: List<WodDraft> = emptyList()) : Builder
}

/** The draft being edited, also while the question is asked. */
val Builder.draft: WodDraft?
    get() = when (this) {
        is Builder.Closed -> null
        is Builder.Editing -> open
        is Builder.Asking -> open
    }

/**
 * The WOD in the builder, as it is being edited: in its limits except for the name, which may be
 * blank while typing. [start] is what it opened with, to tell whether leaving loses changes.
 * A draft whose id is one of the lifter's own WODs edits it, any other one is new. [kept], [keptRounds]
 * and [keptScheme]: the numbers of the types it was set to before, which come back when that type is chosen again.
 *
 * The reps per round: which chip is lit is never stored, it is [schemeChoice] of the scheme, so it always
 * says what Save saves. Only the text of the "Custom" field is: [typed], the field open with what is typed
 * (null while it is closed), and [keptTyped], what it held last, back when it opens again. Neither is
 * written to the draft file: a draft read back shows the field when its scheme is a custom one.
 *
 *                 ChooseCustom: the field opens with keptTyped
 *   ┌─────────────┐ ──────────────────────────────────────▶ ┌──────────────────────┐ TypeScheme(text):
 *   │ field closed│                                         │ field open (typed)   │ scheme = the numbers
 *   │ typed null  │ ◀────────────────────────────────────── │ chip: of the scheme  │ in the text
 *   └─────────────┘  SetScheme (Same or a preset chip),     └──────────────────────┘
 *                    SetMode: keptTyped = typed
 */
@Serializable
data class WodDraft(
    val wod: Wod,
    val start: Wod,
    val kept: Map<TimerMode, TimerConfig> = emptyMap(),
    val keptRounds: Int = 1,
    val keptScheme: List<Int> = emptyList(),
    @Transient val typed: String? = null,
    @Transient val keptTyped: String = "",
)

/** The chip of the reps per round, as the scheme says: the same in the builder and when the WOD is opened again. */
sealed interface SchemeChoice {
    /** "Same each round": no scheme. */
    data object Same : SchemeChoice

    /** One of [io.github.alexanderop.eisen.feature.timer.wod.SCHEMES], also when it was typed. */
    data class Preset(val scheme: List<Int>) : SchemeChoice

    /** Any other scheme. */
    data class Custom(val scheme: List<Int>) : SchemeChoice
}

enum class Tab { HOME, WORKOUTS, TIMER, HISTORY, SETTINGS }

/** The two-way switch at the top of the History tab: the list of what was done, or the charts. */
enum class HistoryView { LOG, PROGRESS }

data class Navigation(
    val tab: Tab = Tab.HOME,
    val history: HistoryView = HistoryView.LOG,
    /** The lifter wants to see the workout. It only shows while there is one, see [showsWorkout]. */
    val workoutOpen: Boolean = false,
    /** The routine open in the editor of the Workouts tab. */
    val routine: String? = null,
    /** The page of the Timer tab. Kept in an Android-only preference, as the PWA keeps it in its storage. */
    val timerPage: TimerPage = TimerPage.WODS,
    /** Counts taps on the Timer tab while it shows, so its page scrolls back to the top. */
    val timerTop: Int = 0,
)

data class DeletedWorkout(val workout: Workout, val index: Int)

data class DeletedPreset(val preset: TimerPreset, val index: Int)

data class DeletedTimerSession(val session: TimerSession, val index: Int)

data class DeletedWod(val wod: Wod, val index: Int)

/** A combination of the lifter's own was deleted from the boxing plan [before], which became [after]. Undo brings [before] back while the plan is still [after]. */
data class DeletedOwn(val before: BoxingPlan, val after: BoxingPlan)

data class RemovedSlot(val routineId: String, val index: Int, val slot: Slot)

/** Only the newest delete of each kind can be undone, like the snackbar that offers it. */
data class Undo(
    val deletedWorkout: DeletedWorkout? = null,
    val removedSlot: RemovedSlot? = null,
    val deletedPreset: DeletedPreset? = null,
    val deletedTimer: DeletedTimerSession? = null,
    val deletedWod: DeletedWod? = null,
    val deletedOwn: DeletedOwn? = null,
)

/**
 * Health Connect sync. [enabled] is the lifter's switch, kept in an Android-only preference.
 * [notice] is the status line under the switch. [availability] decides which rows Settings shows;
 * the runtime asks Android each time the app comes to the front, see [Msg.Health.Availability].
 */
data class HealthModel(
    val enabled: Boolean = false,
    val syncing: Boolean = false,
    val notice: String? = null,
    val availability: HealthAvailability = HealthAvailability.UNAVAILABLE,
)

/** Whether Health Connect can be used on this phone. It changes outside the app (the Play Store). */
enum class HealthAvailability {
    AVAILABLE,

    /** Installed but too old, or not installed where it can be: the Play Store fixes it. */
    NEEDS_INSTALL,
    UNAVAILABLE,
}

/**
 * The app asks before it may show notifications.
 * [asked] is true once the app asked: it asks only once, the system settings do the rest.
 */
data class Notifications(val granted: Boolean, val asked: Boolean)

/** How a ticked warmup set is known in [Model.warmupDone]. */
fun warmupKey(exerciseId: String, set: Int): String = "$exerciseId:$set"

/**
 * What the process knows when it starts: read from the file and the preferences by the runtime,
 * then turned into the first [Model] by [init].
 */
data class Flags(
    /** Null when the state file is missing or cannot be read. */
    val state: AppState?,
    val savedRest: RestState?,
    val healthEnabled: Boolean,
    val notifications: Notifications,
    /** The clock when the process started, so the first screen dates itself before anything ticks. */
    val now: Long = 0,
    /** The interval timer that ran or was paused when the process ended. */
    val savedTimer: TimerRun? = null,
    val timerPage: TimerPage = TimerPage.WODS,
    /** The builder's drafts when the process ended, newest first: the draft file, see [draftFile]. */
    val savedDrafts: List<WodDraft> = emptyList(),
)

/** The first model, and what has to happen right away. Elm's `init`. */
fun init(flags: Flags): Pair<Model, List<Cmd>> {
    val state = flags.state ?: freshState()
    val model = Model(
        // A first start, or storage that cannot be read: a fresh start instead of a blocked app.
        state = state,
        nav = Navigation(workoutOpen = state.active != null, timerPage = flags.timerPage),
        wods = WodsModel(builder = Builder.Closed(flags.savedDrafts)),
        health = HealthModel(enabled = flags.healthEnabled),
        notifications = flags.notifications,
        now = flags.now,
    )
    val cmds = mutableListOf<Cmd>()
    // The interval timer first: a run that still goes covers everything, so no rest can run under it.
    val (timed, more) = restoreTimer(model, flags.savedTimer, flags.now)
    val saved = flags.savedRest
    val restored = when {
        saved == null -> timed
        // A run starts by ending the rest, and no rest starts under a run: when both were kept (written to
        // two places, one of them did not make it), the run is the newer one and the rest is stale.
        timed.timer is TimerState.Running -> {
            cmds += stopRestCmds()
            timed
        }
        // After the process died during a rest: the alarms are still set, the rest goes on.
        timed.session.active != null -> timed.copy(rest = saved)
        // The workout is gone (finished or discarded elsewhere): its rest is stale.
        else -> {
            cmds += stopRestCmds()
            timed
        }
    }
    // Health Connect may have taken the permission away while the app was closed.
    if (flags.healthEnabled) cmds += Cmd.Health(HealthJob.CheckPermission(PermissionCheck.STARTUP))
    // After an install the shortcuts are published, after an update the widgets redrawn.
    cmds += refreshHomeSurfaces(state)
    return restored to cmds + more
}
