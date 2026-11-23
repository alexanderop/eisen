package io.github.alexanderop.eisen.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.alexanderop.eisen.MainActivity
import io.github.alexanderop.eisen.domain.elm.Msg

/**
 * The way into a workout from outside the app. The widget's button and the launcher shortcuts
 * both send this intent to [MainActivity], which turns it into a Msg with [message].
 */
object WorkoutLaunch {
    const val ACTION_START_WORKOUT = "io.github.alexanderop.eisen.START_WORKOUT"
    const val EXTRA_ROUTINE_ID = "routineId"

    /** Starts [routineId], or resumes the running workout when it is null. */
    fun intent(context: Context, routineId: String?): Intent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_START_WORKOUT)
            // A different address per routine, so the widget's PendingIntents never replace each other.
            .setData(Uri.Builder().scheme("eisen").authority("workout").appendPath(routineId ?: "current").build())
            // Reuses the open activity instead of stacking a second one on top of it.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (routineId != null) intent.putExtra(EXTRA_ROUTINE_ID, routineId)
        return intent
    }

    /**
     * The Msg for an intent that asks to start a workout, null for any other intent. It is read
     * once: the action is taken off, so the same intent object handed back later is no longer one.
     * What Android hands back in other ways (after a configuration change or the end of the process:
     * [restored]; from recents: a flag) the Msg says, and `update` ignores it.
     */
    fun message(intent: Intent?, restored: Boolean): Msg.App.LaunchWorkout? {
        if (intent?.action != ACTION_START_WORKOUT) return null
        intent.action = null
        return Msg.App.LaunchWorkout(
            routineId = intent.getStringExtra(EXTRA_ROUTINE_ID),
            restored = restored,
            fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0,
        )
    }
}
