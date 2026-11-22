package io.github.alexanderop.eisen.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.alexanderop.eisen.MainActivity
import io.github.alexanderop.eisen.data.Store

/**
 * The way into a workout from outside the app. The widget's button and the launcher shortcuts
 * both send this intent to [MainActivity], which hands it to [handle].
 */
object WorkoutLaunch {
    const val ACTION_START_WORKOUT = "io.github.alexanderop.eisen.START_WORKOUT"
    const val EXTRA_ROUTINE_ID = "routineId"

    /** True after an intent started or resumed a workout. The activity shows the workout and sets it back. */
    var openRequested by mutableStateOf(false)

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
     * Starts the workout the intent asks for. Each intent counts once: Android hands the same
     * intent back after a configuration change, after the process was killed, and when the app
     * is reopened from recents. [restored] is true in those first two cases.
     */
    fun handle(intent: Intent?, restored: Boolean) {
        if (intent?.action != ACTION_START_WORKOUT) return
        intent.action = null
        if (restored || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val state = Store.state
        if (state.active == null) {
            if (state.routines.isEmpty()) return
            val id = intent.getStringExtra(EXTRA_ROUTINE_ID)
            if (id != null && state.routines.any { it.id == id }) Store.selectRoutine(id)
            // A workout whose exercises were all deleted cannot start. The app opens on the home screen.
            if (!Store.startWorkout()) return
        }
        openRequested = true
    }
}
