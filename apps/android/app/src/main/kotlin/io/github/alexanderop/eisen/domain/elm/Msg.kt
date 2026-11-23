package io.github.alexanderop.eisen.domain.elm

import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.ExerciseLog
import io.github.alexanderop.eisen.domain.Slot
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.WorkoutEvent
import io.github.alexanderop.eisen.domain.Workout as FinishedWorkout

/**
 * Everything that can happen, from the lifter or from outside the app. [update] takes each one.
 * A Msg that makes no sense in the current model (an index that is gone, a second Undo) changes nothing.
 */
sealed interface Msg {
    /**
     * The running workout. The machine in `domain/WorkoutMachine.kt` decides; [update] adds the rest
     * timer, haptics, navigation and Undo around it. Start, SetWorkingWeight (from Settings) and
     * Undo/Forget of a discard all come through here.
     */
    data class Workout(val event: WorkoutEvent) : Msg

    /** The parts of the workout screen that are not the machine's business. */
    sealed interface WorkoutScreen : Msg {
        /** Ticks a warmup set off, or back on. [key] is [warmupKey]. */
        data class ToggleWarmup(val key: String) : WorkoutScreen

        /** The reps entry was closed without logging a count. */
        data object CloseRepsEntry : WorkoutScreen

        /**
         * [SETTLE_MS] after the tap at [since] on [id] ([Model.settling]): the exercise takes its place.
         * A later tap started a new wait, so a Settle of an older one changes nothing.
         */
        data class Settle(val id: String, val since: Long) : WorkoutScreen

        /** A drag starts: the exercise tapped last takes its place now, so the order under the finger holds still. */
        data object SettleNow : WorkoutScreen

        /** TalkBack's "Move up" (-1) and "Move down" (1) of an open exercise. */
        data class MoveBy(val id: String, val step: Int) : WorkoutScreen
    }

    sealed interface Nav : Msg {
        data class SelectTab(val tab: Tab) : Nav
        data object OpenWorkout : Nav
        data object CloseWorkout : Nav
        data class OpenRoutine(val id: String) : Nav
        data object CloseRoutine : Nav

        /** The system back gesture. Only sent while [handlesBack] is true; otherwise Android closes the app. */
        data object Back : Nav

        /** What the activity saved before Android ended the process. */
        data class Restore(val tab: Tab, val workoutOpen: Boolean, val routine: String?) : Nav
    }

    sealed interface Home : Msg {
        /** Hides the "Workout saved" card. */
        data object DismissSummary : Home
    }

    sealed interface Routines : Msg {
        /** Chooses the workout the home screen offers next. Ignored while one runs. */
        data class Select(val id: String) : Routines

        /** Adds "Workout N" at the end. [openEditor] opens it in the Workouts tab. */
        data class Add(val openEditor: Boolean = true) : Routines

        /** Cut to [ROUTINE_NAME_LENGTH]. May be blank while typing, see [CommitName]. */
        data class Rename(val id: String, val name: String) : Routines

        /** The name field was left: a blank name becomes "Workout". */
        data class CommitName(val id: String) : Routines

        /** Changes the rotation order. The workout offered next stays the same workout. */
        data class Move(val from: Int, val to: Int) : Routines

        /** The last workout stays: the home screen always needs one to offer. */
        data class Remove(val id: String) : Routines

        data class AddSlot(val routineId: String, val exerciseId: String) : Routines
        data class UpdateSlot(val routineId: String, val index: Int, val slot: Slot) : Routines

        /** Any distance as one change. One place up is `MoveSlot(id, i, i - 1)`. */
        data class MoveSlot(val routineId: String, val from: Int, val to: Int) : Routines

        /**
         * By exercise, not by index: a swipe and a button can fire for the same card, and the second
         * must not take the slot that moved into its place. [offerUndo] shows "Removed X" with Undo.
         */
        data class RemoveSlot(val routineId: String, val exerciseId: String, val offerUndo: Boolean = true) : Routines

        /** Puts a slot back at [index]. Nothing happens when the exercise is in the routine again. */
        data class InsertSlot(val routineId: String, val index: Int, val slot: Slot) : Routines

        /** The Undo of the last [RemoveSlot]. */
        data object UndoRemoveSlot : Routines
    }

    sealed interface Exercises : Msg {
        /**
         * Creates the lifter's own exercise, or finds the one with that name. An empty name does nothing.
         * [addTo] also adds it where the picker was opened. See [exerciseNamed] for the result.
         * [timed]: held for time, like a plank.
         */
        data class AddCustom(val name: String, val equipment: Equipment, val addTo: AddTarget? = null, val timed: Boolean = false) : Exercises

        /** Removes it from every routine. Finished workouts keep their logs of it. */
        data class DeleteCustom(val id: String) : Exercises
    }

    sealed interface History : Msg {
        /** Corrects what a finished workout logged. Working weights stay as they are. */
        data class Edit(val id: String, val exercises: List<ExerciseLog>) : History

        /** [offerUndo] shows "Workout deleted" with Undo (a swipe); the dialog asks first and offers none. */
        data class Delete(val id: String, val offerUndo: Boolean = false) : History

        /** Puts a deleted workout back at [index]. Nothing happens when it is still there. */
        data class Restore(val workout: FinishedWorkout, val index: Int) : History

        /** The Undo of the last [Delete] with `offerUndo`. */
        data object UndoDelete : History
    }

    sealed interface Settings : Msg {
        /** Converts the working weights. Ignored during a workout and while a discard can be undone. */
        data class SetUnit(val unit: WeightUnit) : Settings
        data class SetBar(val bar: Double) : Settings
        data class SetRestDone(val seconds: Int) : Settings
        data class SetRestMissed(val seconds: Int) : Settings

        /** Switching the sound on plays the end-of-rest cue once, so the lifter hears what it does. */
        data class SetSound(val on: Boolean) : Settings
        data class SetHaptics(val on: Boolean) : Settings
        data class SetRestFullScreen(val on: Boolean) : Settings

        /** Discarding a workout puts an increment changed during it back. */
        data class SetIncrement(val id: String, val increment: Double) : Settings
    }

    /** Backups and erasing. The file pickers stay in the view; reading and writing are Cmds. */
    sealed interface Data : Msg {
        /** A backup's text. Not one of this app or the PWA: nothing changes and [Model.dataNotice] says so. */
        data class Import(val json: String) : Data

        /** A file the lifter picked to restore. The runtime reads it and answers with [BackupRead]. */
        data class Restore(val uri: String) : Data

        /** Null when the file could not be read. */
        data class BackupRead(val text: String?) : Data

        /** A file the lifter created with the "Save backup" picker, named [backupFileName]. */
        data class WriteBackup(val uri: String) : Data
        data class BackupWritten(val ok: Boolean) : Data

        /** Hands the backup to the share sheet as a file. */
        data object SendBackup : Data
        data object SendFailed : Data

        /** Erases everything but the unit. */
        data object Reset : Data
    }

    /** The hold timer of a timed set, see [HoldTimer]. A Msg without an open timer changes nothing. */
    sealed interface Hold : Msg {
        /** A tap on the "Get ready" countdown: the hold starts now. */
        data object SkipLeadIn : Hold

        /** "Stop" during a countdown, "Done" while holding as long as possible: logs the whole seconds held. */
        data object Stop : Hold

        /** Closes the timer without logging anything. The back gesture does the same. */
        data object Cancel : Hold

        /** A second of the hold passed, see [subscriptions]. */
        data object Tick : Hold
    }

    /** The rest timer. Its alarms and the buttons on its notification end up here too. */
    sealed interface Rest : Msg {
        /** "+30s": in the app and on the notification. */
        data object Extend : Rest

        /**
         * Close, swipe, "Next set" on the rest screen, "Skip" on the notification, or swiping away the
         * "Rest is over" notification.
         */
        data object Dismiss : Rest

        /** "−15s" on the rest screen. Never past now: a rest that is over has nothing left to take. */
        data object Shorten : Rest

        /** "+15s" on the rest screen, counted from now once the rest is over, like [Extend]. */
        data object Lengthen : Rest

        /** The rest screen is swiped down, closed with its chevron or the back gesture. The rest goes on in the bar. */
        data object Minimise : Rest

        /** A tap on the rest bar brings the minimised rest screen back. */
        data object Open : Rest

        /** The "ten seconds left" alarm. */
        data object AlarmTen : Rest

        /** The "rest over" alarm. An alarm left over from before an extend is ignored. */
        data object AlarmOver : Rest

        /** The cue of [rest] had time to finish. Lets the countdown service go if nothing else started. */
        data class CueFinished(val rest: RestState) : Rest

        /** A second of the countdown passed while the app shows, see [subscriptions]. */
        data object Tick : Rest

        /** The answer to the notification permission request. */
        data class NotificationPermission(val granted: Boolean) : Rest
    }

    sealed interface Health : Msg {
        /** The switch: off at once, on only with Health Connect's permission. */
        data object Toggle : Health
        data class PermissionChecked(val purpose: PermissionCheck, val granted: Boolean) : Health

        /** The answer of Health Connect's permission screen. */
        data class PermissionResult(val granted: Boolean) : Health

        /** Health Connect's permission screen could not be opened. */
        data object PermissionRequestFailed : Health

        /** Writes the whole history once. */
        data object SyncPast : Health

        /** Null when Health Connect could not be reached. */
        data class SyncedPast(val count: Int?) : Health

        /** A write found the permission taken away. */
        data object Revoked : Health

        /**
         * The Health Connect rows came on screen or back to it. The Play Store may only cover the app
         * without stopping it, so [App.Foreground] alone would miss an install made from there.
         */
        data object RowsShown : Health

        /** What Android says about Health Connect on this phone, see [HealthJob.CheckAvailability]. */
        data class Availability(val availability: HealthAvailability) : Health
    }

    sealed interface App : Msg {
        /** The activity is on screen. Notifications may have been allowed in the system settings meanwhile. */
        data class Foreground(val notificationsGranted: Boolean) : App
        data object Background : App

        /**
         * The widget or a launcher shortcut asked to start [routineId], or to resume the running workout
         * when it is null. Android hands the same intent back after a configuration change or the end of
         * the process ([restored]) and when the app is reopened from recents ([fromHistory]); it counts once.
         */
        data class LaunchWorkout(val routineId: String?, val restored: Boolean, val fromHistory: Boolean) : App
    }
}

/** Where a newly created exercise goes, besides the list of exercises. */
sealed interface AddTarget {
    data class Routine(val id: String) : AddTarget

    /** The running workout only, like [WorkoutEvent.AddExercise]. */
    data object Workout : AddTarget
}

/** Why Health Connect's permission is checked, so the answer goes to the right place. */
enum class PermissionCheck { STARTUP, TOGGLE }

const val ROUTINE_NAME_LENGTH = 30
const val EXERCISE_NAME_LENGTH = 40
