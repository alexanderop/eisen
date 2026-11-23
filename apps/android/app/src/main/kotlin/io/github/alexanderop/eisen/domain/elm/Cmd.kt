package io.github.alexanderop.eisen.domain.elm

import io.github.alexanderop.eisen.domain.AppState
import io.github.alexanderop.eisen.domain.Feel
import io.github.alexanderop.eisen.domain.Workout

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

    /** Something the screen shows or does once. See [UiEvent]. */
    data class Ui(val event: UiEvent) : Cmd
}

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

    /** Android 13 and newer: the system dialog to allow notifications. Answers with [Msg.Rest.NotificationPermission]. */
    data object RequestNotificationPermission : UiEvent

    /** Health Connect's permission screen. Answers with [Msg.Health.PermissionResult]. */
    data object RequestHealthPermission : UiEvent

    /** Writes [json] to a file named [fileName] and opens the share sheet with it. Fails with [Msg.Data.SendFailed]. */
    data class ShareBackup(val fileName: String, val json: String) : UiEvent
}

/** What the Health Connect adapter does, one job after the other in the order they come. */
sealed interface HealthJob {
    /** Answers with [Msg.Health.PermissionChecked]. */
    data class CheckPermission(val purpose: PermissionCheck) : HealthJob

    /** Writes (or rewrites) these workouts. A missing permission answers with [Msg.Health.Revoked]. */
    data class Upsert(val workouts: List<Workout>) : HealthJob
    data class Delete(val ids: List<String>) : HealthJob

    /** [Upsert] that answers with [Msg.Health.SyncedPast]. */
    data class SyncPast(val workouts: List<Workout>) : HealthJob

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

/** The countdown notification of a running rest, with "+30s" and "Skip". */
data class RestCountdown(val title: String, val text: String, val endsAt: Long, val extendLabel: String, val skipLabel: String)

/** The notification at the end of a rest. Swiping it away dismisses the rest. */
data class RestOver(val title: String, val text: String, val endedAt: Long)

const val SNACKBAR_SHORT_MS = 4_000L
