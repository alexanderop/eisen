package io.github.alexanderop.eisen.domain.elm

import io.github.alexanderop.eisen.domain.AppState
import io.github.alexanderop.eisen.domain.Change
import io.github.alexanderop.eisen.domain.Session
import io.github.alexanderop.eisen.domain.Slot
import io.github.alexanderop.eisen.domain.TimerConfig
import io.github.alexanderop.eisen.domain.TimerMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import io.github.alexanderop.eisen.domain.TimerPreset
import io.github.alexanderop.eisen.domain.TimerRun
import io.github.alexanderop.eisen.domain.TimerSession
import io.github.alexanderop.eisen.domain.Wod
import io.github.alexanderop.eisen.domain.Workout
import io.github.alexanderop.eisen.domain.active
import io.github.alexanderop.eisen.domain.freshState

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
     * The running workout and the question asked about it, see `domain/WorkoutMachine.kt`.
     * `state.active` is always `session.active`: the workout belongs to the session.
     */
    val session: Session = Session.restore(state),
    /** Null when no rest runs. A rest that is over stays until it is dismissed. */
    val rest: RestState? = null,
    /** The rest over the whole workout screen, with the set that started it. Null when none is open. */
    val restScreen: RestScreen? = null,
    /** What the last finished workout changed. Shown once on the home screen. */
    val lastChanges: List<Change>? = null,
    /** Warmup sets ticked off in the running workout, as [warmupKey]. */
    val warmupDone: Set<String> = emptySet(),
    /** A max-reps set was tapped: the workout screen asks for the count. */
    val repsQuestion: RepsQuestion? = null,
    /** An open set of a timed exercise was tapped: its hold timer shows. Null when none is open. */
    val hold: HoldTimer? = null,
    /** The exercise tapped last, which holds its place on the workout screen for a moment. */
    val settling: Settling? = null,
    /** The setup of an interval timer open in the Timer tab. Null shows the modes and the saved timers. */
    val timerSetup: TimerSetup? = null,
    /** A running interval timer, or the result of the one that just ended. Shown over everything else. */
    val timer: TimerState? = null,
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

/** A running rest. Based on timestamps, so it stays right when the app is closed. */
data class RestState(val startedAt: Long, val endsAt: Long, val note: String)

/**
 * The rest screen of the running [Model.rest], for set [set] of the exercise at [exercise] in the running
 * workout: the set that started the rest, which its wheels correct and its chips rate. [exerciseId] makes
 * sure it is still the same exercise there. [minimised]: put away by the lifter, the rest bar shows instead
 * and a tap on it brings the screen back. It only exists while its rest runs and its set is logged, see
 * [restScreenFits].
 *
 * [setDone]: the same screen without a clock after the last set of an exercise, which starts no rest. It
 * is there to rate that set. It never minimises: put away, it is gone.
 */
data class RestScreen(val exercise: Int, val set: Int, val exerciseId: String, val minimised: Boolean = false, val setDone: Boolean = false)

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
)

/** The max-reps set being asked about, and the count the entry starts at. */
data class RepsQuestion(val exercise: Int, val set: Int, val reps: Int)

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
 */
data class TimerSetup(val config: TimerConfig, val presetId: String? = null, val name: String = "")

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
     */
    data class Finished(val session: TimerSession, val wod: Wod? = null, val undo: TimerRun? = null, val at: Long = 0) : TimerState
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
 * detail shows, one of the lifter's own or of the library. [draft]: the builder, over the detail or the list.
 * [pending]: a draft the process had when it ended, offered as "Continue editing" until it is
 * continued or discarded. The draft file always holds [draft], or else [pending].
 * [query]: what is typed in the search field, kept while the process lives, also over a detail.
 * [suggesting]: the suggestions under the field are open; never without something typed.
 */
data class WodsModel(
    val filter: WodFilter = WodFilter.ALL,
    val detail: String? = null,
    val draft: WodDraft? = null,
    val pending: WodDraft? = null,
    val query: String = "",
    val suggesting: Boolean = false,
)

/**
 * The WOD in the builder, as it is being edited: in its limits except for the name, which may be
 * blank while typing. [start] is what it opened with, to tell whether leaving loses changes.
 * [asking]: "Discard changes?" is open. A draft whose id is one of the lifter's own WODs edits it,
 * any other one is new. [kept], [keptRounds] and [keptScheme]: the numbers of the types it was set to
 * before, which come back when that type is chosen again.
 */
@Serializable
data class WodDraft(
    val wod: Wod,
    val start: Wod,
    @Transient val asking: Boolean = false,
    val kept: Map<TimerMode, TimerConfig> = emptyMap(),
    val keptRounds: Int = 1,
    val keptScheme: List<Int> = emptyList(),
)

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

data class RemovedSlot(val routineId: String, val index: Int, val slot: Slot)

/** Only the newest delete of each kind can be undone, like the snackbar that offers it. */
data class Undo(
    val deletedWorkout: DeletedWorkout? = null,
    val removedSlot: RemovedSlot? = null,
    val deletedPreset: DeletedPreset? = null,
    val deletedTimer: DeletedTimerSession? = null,
    val deletedWod: DeletedWod? = null,
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
 * Android 13 and newer ask before an app may show notifications. [granted] is always true before that.
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
    /** The builder's draft when the process ended. */
    val savedDraft: WodDraft? = null,
)

/** The first model, and what has to happen right away. Elm's `init`. */
fun init(flags: Flags): Pair<Model, List<Cmd>> {
    val state = flags.state ?: freshState()
    val model = Model(
        // A first start, or storage that cannot be read: a fresh start instead of a blocked app.
        state = state,
        nav = Navigation(workoutOpen = state.active != null, timerPage = flags.timerPage),
        wods = WodsModel(pending = flags.savedDraft),
        health = HealthModel(enabled = flags.healthEnabled),
        notifications = flags.notifications,
        now = flags.now,
    )
    val cmds = mutableListOf<Cmd>()
    val saved = flags.savedRest
    val restored = when {
        saved == null -> model
        // After the process died during a rest: the alarms are still set, the rest goes on.
        model.session.active != null -> model.copy(rest = saved)
        // The workout is gone (finished or discarded elsewhere): its rest is stale.
        else -> {
            cmds += stopRestCmds()
            model
        }
    }
    // Health Connect may have taken the permission away while the app was closed.
    if (flags.healthEnabled) cmds += Cmd.Health(HealthJob.CheckPermission(PermissionCheck.STARTUP))
    // After an install the shortcuts are published, after an update the widgets redrawn.
    cmds += refreshHomeSurfaces(restored.state)
    val (timed, more) = restoreTimer(restored, flags.savedTimer, flags.now)
    return timed to cmds + more
}
