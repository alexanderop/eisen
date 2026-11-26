package io.github.alexanderop.eisen.app.elm

import io.github.alexanderop.eisen.app.state.AppState
import io.github.alexanderop.eisen.feature.timer.TimerRun
import io.github.alexanderop.eisen.feature.timer.TimerSession
import io.github.alexanderop.eisen.feature.workout.Workout

/**
 * A side effect as plain data. [update] decides it, the runtime carries it out with a small adapter
 * and may answer with a Msg. An adapter never decides: when a Cmd needs an `if`, the `if` belongs in [update].
 */
sealed interface Cmd {
    /** Writes the state file. */
    data class Save(val state: AppState) : Cmd

    /** Keeps the rest in an Android preference, so it survives the end of the process. Null clears it. */
    data class SaveRest(val rest: RestState?) : Cmd

    /** The system alarms of a rest. A null [tenAt] cancels the "ten seconds left" alarm. */
    data class ScheduleRestAlarms(val tenAt: Long?, val overAt: Long) : Cmd
    data object CancelRestAlarms : Cmd

    /** Starts (or updates) the foreground service with the countdown notification. */
    data class ShowRestCountdown(val notice: RestCountdown) : Cmd

    /** Asks the countdown service to stop itself. */
    data object HideRestCountdown : Cmd
    data class ShowRestOver(val notice: RestOver) : Cmd
    data object HideRestOver : Cmd
    data class PlayCue(val cue: Cue) : Cmd

    /**
     * Shakes the phone whatever the haptics setting says: the end of a rest must be felt. Whether a
     * hold timer shakes is decided in [update] by that setting.
     */
    data class Vibrate(val pattern: List<Long>) : Cmd

    /** [times] short beeps in a row, the hold timer's cue. Sent only with the sound on. */
    data class Beep(val times: Int) : Cmd

    /** Sends [msg] after [millis]. Elm would sleep in a Task; this is the same thing. */
    data class After(val millis: Long, val msg: Msg) : Cmd

    /** Remembers that the notification permission was asked for, see [Notifications.asked]. */
    data object MarkNotificationsAsked : Cmd

    data class SaveHealthEnabled(val enabled: Boolean) : Cmd
    data class Health(val job: HealthJob) : Cmd

    /**
     * Redraws the home-screen widget with [widget] and republishes [shortcuts]. Sent only when one of
     * them changed, see [widgetContent] and [shortcutsOf]. The adapter waits for a pause in the taps.
     */
    data class RefreshHomeSurfaces(val widget: WidgetContent, val shortcuts: List<ShortcutSpec>) : Cmd

    /** Reads a picked file and answers with [Msg.Data.BackupRead]. */
    data class ReadBackup(val uri: String) : Cmd

    /** Writes [json] into a picked file and answers with [Msg.Data.BackupWritten]. */
    data class WriteBackup(val uri: String, val json: String) : Cmd

    /** Keeps the running interval timer in a file of its own, so it survives the end of the process. Null deletes it. */
    data class SaveTimer(val run: TimerRun?) : Cmd

    /**
     * Replaces what the interval timer's service plays: [sounds] and [vibrations] at their times on the wall
     * clock. A sound more than [TIMER_CUE_LATE_MILLIS] late is skipped, not played late. The phone is kept
     * awake until [awakeUntil]; null lets it sleep. Empty lists silence the timer at once (a pause).
     */
    data class ScheduleTimer(val sounds: List<TimerSound>, val vibrations: List<TimerVibration>, val awakeUntil: Long?) : Cmd

    /** Starts (or updates) the interval timer's foreground service with its notification. */
    data class ShowTimer(val notice: TimerNotice) : Cmd

    /** Stops the interval timer's service, its sounds and its notification. */
    data object HideTimer : Cmd

    /** Keeps the builder's drafts in a file of their own, so they survive the end of the process. Empty deletes it. See [draftFile]. */
    data class SaveDraft(val drafts: List<WodDraft>) : Cmd

    /** Keeps the page of the Timer tab in an Android preference, see [Navigation.timerPage]. */
    data class SaveTimerPage(val page: TimerPage) : Cmd

    /** Plays one sound of the timers now, the way a run would. [fallback] plays instead when [sound] is not recorded. */
    data class PlayTimerSound(val sound: String, val fallback: String? = null) : Cmd

    /**
     * Plays sounds of the timers one after the other, each [TimerSound.at] milliseconds from now: a
     * combination of the lifter's own, move by move, the way a run says it. [fallback] stands in for a
     * sound that is not recorded.
     */
    data class PlayTimerSounds(val sounds: List<TimerSound>, val fallback: String? = null) : Cmd

    /** Asks Open Food Facts for the product with [barcode]. Answers with [Msg.Food.Looked]. */
    data class LookUpProduct(val barcode: String) : Cmd

    /** Asks Open Food Facts to search for [query]. Answers with [Msg.Food.Searched]. */
    data class SearchFoods(val query: String) : Cmd

    /** Something the screen shows or does once. See [UiEvent]. */
    data class Ui(val event: UiEvent) : Cmd
}

/** A sound of an interval timer at [at] on the wall clock: "beep", "beep-long" or a recording in `voice/timer`. */
data class TimerSound(val at: Long, val sound: String)

data class TimerVibration(val at: Long, val pattern: List<Long>)

/**
 * The ongoing notification of an interval timer. [base] is the chronometer's time, null while paused.
 * [lines]: what to do in this round of a WOD, one per line when the notification is expanded.
 * [chip]: the few characters of Android 16's status bar chip when there is no countdown to show
 * there (null: the chronometer is the chip). [progress]: its segmented bar, null where the run has no end.
 */
data class TimerNotice(
    val title: String,
    val text: String,
    val base: Long?,
    val countDown: Boolean,
    val paused: Boolean,
    val toggleLabel: String,
    val endLabel: String,
    val lines: List<String> = emptyList(),
    val chip: String? = null,
    val progress: NoticeProgress? = null,
)

/** The colour of a part of a notification's progress bar, as the screens colour their phases. */
enum class NoticeTone { WORK, REST }

/** A part of a notification's progress bar, [seconds] long. */
data class NoticeSegment(val seconds: Int, val tone: NoticeTone)

/**
 * A notification's progress bar (Android 16's ProgressStyle): [segments] in order and the tracker [at]
 * seconds into them. It only moves when the notification is posted again, which is rare on purpose:
 * see [timerNotice].
 */
data class NoticeProgress(val segments: List<NoticeSegment>, val at: Int)

/** A cue of an interval timer that comes this late (the phone was asleep) is skipped. */
const val TIMER_CUE_LATE_MILLIS = 1_500L

/**
 * What the UI does once instead of showing it from the model: a snackbar, a haptic, a system dialog.
 * The runtime shows snackbars itself (they outlive a turned screen); the rest goes to the activity.
 */
sealed interface UiEvent {
    /**
     * A snackbar in the one SnackbarHost of the app. A new one replaces the one showing.
     * [onAction] is sent when [actionLabel] is tapped, [onDismiss] when it goes any other way.
     * [millis] is stretched by the accessibility settings that ask for longer timeouts.
     */
    data class Snackbar(
        val text: String,
        val actionLabel: String? = null,
        val onAction: Msg? = null,
        val onDismiss: Msg? = null,
        val millis: Long = SNACKBAR_SHORT_MS,
        /** Lets a later [DismissSnackbar] take this one away. */
        val tag: String? = null,
    ) : UiEvent

    /** Takes the snackbar with [tag] away, if it still shows. Its [Snackbar.onDismiss] is sent. */
    data class DismissSnackbar(val tag: String) : UiEvent

    data class Haptic(val feel: Feel) : UiEvent

    /** The system dialog to allow notifications. Answers with [Msg.Rest.NotificationPermission]. */
    data object RequestNotificationPermission : UiEvent

    /** Health Connect's permission screen. Answers with [Msg.Health.PermissionResult]. */
    data object RequestHealthPermission : UiEvent

    /** Writes [json] to a file named [fileName] and opens the share sheet with it. Fails with [Msg.Data.SendFailed]. */
    data class ShareBackup(val fileName: String, val json: String) : UiEvent

    /** Google's code scanner, for the barcodes of products. Answers with [Msg.Food.Scanned] or [Msg.Food.ScanFailed]. */
    data object ScanBarcode : UiEvent
}

/** What the Health Connect adapter does, one job after the other in the order they come. */
sealed interface HealthJob {
    /** Answers with [Msg.Health.PermissionChecked]. */
    data class CheckPermission(val purpose: PermissionCheck) : HealthJob

    /** Writes (or rewrites) these workouts and timers. A missing permission answers with [Msg.Health.Revoked]. */
    data class Upsert(val workouts: List<Workout>, val timers: List<TimerSession> = emptyList()) : HealthJob

    /** Workouts and timers alike, by id. */
    data class Delete(val ids: List<String>) : HealthJob

    /** [Upsert] that answers with [Msg.Health.SyncedPast]. */
    data class SyncPast(val workouts: List<Workout>, val timers: List<TimerSession> = emptyList()) : HealthJob

    /** Asks Android whether Health Connect is installed and usable. Answers with [Msg.Health.Availability]. */
    data object CheckAvailability : HealthJob
}

/**
 * A spoken cue of the rest timer: the recording in the app's assets (the same files the PWA plays),
 * and whether it beeps when the recording cannot be played.
 */
enum class Cue(val asset: String, val beepWhenSilent: Boolean) {
    TEN_SECONDS("voice/ten-seconds.mp3", beepWhenSilent = false),

    // Missing the end of a rest matters more than missing the warning, so it has a beep to fall back on.
    REST_OVER("voice/rest-over.mp3", beepWhenSilent = true),
}

/** The countdown notification of a running rest, with "+30s" and "Skip", and its bar of one segment. */
data class RestCountdown(val title: String, val text: String, val endsAt: Long, val extendLabel: String, val skipLabel: String, val progress: NoticeProgress)

/** The notification at the end of a rest. Swiping it away dismisses the rest. */
data class RestOver(val title: String, val text: String, val endedAt: Long)

const val SNACKBAR_SHORT_MS = 4_000L
