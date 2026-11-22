package io.github.alexanderop.eisen.ui.workout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.domain.Effect
import io.github.alexanderop.eisen.domain.Feel
import io.github.alexanderop.eisen.domain.WorkoutEvent
import io.github.alexanderop.eisen.rest.Rest
import io.github.alexanderop.eisen.ui.Haptic
import io.github.alexanderop.eisen.ui.Haptics
import io.github.alexanderop.eisen.ui.rememberHaptics

/**
 * The one place that turns what the workout machine asks for into calls: the rest timer, the
 * haptics and the reps entry. Screens only send events through [send].
 */
@Stable
class WorkoutEffects internal constructor(private val haptics: Haptics, private val startRest: (Int, String) -> Unit) {
    /** Sends [event] to the Store and runs what comes back. [onAskReps] opens the entry for a max-reps set. */
    fun send(event: WorkoutEvent, onAskReps: (Effect.AskReps) -> Unit = {}): List<Effect> {
        val effects = Store.send(event)
        for (effect in effects) {
            when (effect) {
                is Effect.StartRest -> startRest(effect.seconds, effect.note)
                Effect.StopRest -> Rest.stop()
                is Effect.Haptic -> haptics.perform(effect.kind.haptic())
                is Effect.AskReps -> onAskReps(effect)
                // The Store handled these already: they change data, not the screen.
                is Effect.Started, is Effect.Saved, is Effect.DiscardedWorkout -> Unit
            }
        }
        return effects
    }
}

private fun Feel.haptic(): Haptic = when (this) {
    Feel.TAP -> Haptic.TAP
    Feel.WARNING -> Haptic.WARNING
    Feel.SUCCESS -> Haptic.SUCCESS
    Feel.DONE -> Haptic.DONE
}

@Composable
fun rememberWorkoutEffects(): WorkoutEffects {
    val haptics = rememberHaptics()
    val startRest = rememberRestStarter()
    return remember(haptics, startRest) { WorkoutEffects(haptics, startRest) }
}

/**
 * Starts a rest. The first time, Android 13 and newer asks to allow notifications, so the rest
 * counts down on the lock screen. The rest starts whatever the answer is.
 */
@Composable
private fun rememberRestStarter(): (Int, String) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val current = Rest.state ?: return@rememberLauncherForActivityResult
        // The rest started before the answer came, so it has no notification yet. Re-arming it
        // with nothing added shows one. Skipped for short or ending rests, whose cues it would change.
        if (granted && current.endsAt - current.startedAt >= 20_000 && current.endsAt - System.currentTimeMillis() > 11_000) {
            Rest.extend(0)
        }
    }
    return remember(context, launcher) {
        { seconds, note ->
            if (shouldAskForNotifications(context)) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ASKED_KEY, true).apply()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Rest.start(seconds, note)
        }
    }
}

private const val PREFS = "workout_screen"
private const val ASKED_KEY = "askedNotifications"

private fun shouldAskForNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ASKED_KEY, false)
