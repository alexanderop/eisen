package io.github.alexanderop.eisen.core.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.drop

enum class Haptic {
    /** A set logged or cleared, a button that does something at once. */
    TAP,
    /** One step of a stepper or a short wheel, one point under the chart crosshair, a reordered row passing another. */
    STEP,
    /** One row of a long wheel that spins fast (seconds, minutes): a lighter tick, so a fling does not buzz. */
    FAST_STEP,
    /** A switch turned on, or an option of a button group chosen. */
    TOGGLE_ON,
    /** A switch turned off, or a choice taken back. */
    TOGGLE_OFF,
    /** A hold or a swipe passed the point where letting go does it: "Hold to finish" full, a row swiped far enough, a row picked up to reorder. */
    THRESHOLD,
    /** A swipe pulled back before that point: letting go now does nothing. */
    THRESHOLD_BACK,
    /** A set logged with reps missed. */
    WARNING,
    /** Every set of an exercise done with all reps. */
    SUCCESS,
    /** Workout finished. */
    DONE,
}

/** The phone's own haptic effects, so a tap feels like it does in every other app. */
class Haptics(private val view: View, private val enabled: Boolean) {
    fun perform(kind: Haptic) {
        if (!enabled) return
        val constant = when (kind) {
            Haptic.TAP -> HapticFeedbackConstants.VIRTUAL_KEY
            Haptic.STEP -> HapticFeedbackConstants.SEGMENT_TICK
            Haptic.FAST_STEP -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            Haptic.TOGGLE_ON -> HapticFeedbackConstants.TOGGLE_ON
            Haptic.TOGGLE_OFF -> HapticFeedbackConstants.TOGGLE_OFF
            Haptic.THRESHOLD -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
            Haptic.THRESHOLD_BACK -> HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
            Haptic.WARNING -> HapticFeedbackConstants.REJECT
            Haptic.SUCCESS, Haptic.DONE -> HapticFeedbackConstants.CONFIRM
        }
        view.performHapticFeedback(constant)
    }

    /** A switch or a toggle that is now [on]. */
    fun toggle(on: Boolean) = perform(if (on) Haptic.TOGGLE_ON else Haptic.TOGGLE_OFF)
}

/**
 * The lifter's "Vibrate on taps" setting. The root provides it from the model, the way a theme
 * is provided, so a stepper deep inside a sheet does not need the setting handed down by hand.
 */
val LocalHapticsEnabled = compositionLocalOf { true }

/**
 * A swipe row's haptics: [Haptic.THRESHOLD] when the swipe passes the point where letting go acts,
 * [Haptic.THRESHOLD_BACK] when it is pulled back before it.
 */
@Composable
fun SwipeHaptics(state: SwipeToDismissBoxState) {
    val haptics by rememberUpdatedState(rememberHaptics())
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }.drop(1).collect {
            haptics.perform(if (it == SwipeToDismissBoxValue.Settled) Haptic.THRESHOLD_BACK else Haptic.THRESHOLD)
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
