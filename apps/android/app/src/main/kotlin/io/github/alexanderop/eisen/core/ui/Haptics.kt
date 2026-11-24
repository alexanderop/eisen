package io.github.alexanderop.eisen.core.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class Haptic {
    /** A set logged or cleared. */
    TAP,
    /** One step on a stepper or one point under the chart crosshair. */
    STEP,
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
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val constant = when (kind) {
            Haptic.TAP -> HapticFeedbackConstants.VIRTUAL_KEY
            Haptic.STEP -> HapticFeedbackConstants.CLOCK_TICK
            Haptic.WARNING -> if (modern) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            Haptic.SUCCESS, Haptic.DONE -> if (modern) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}

/**
 * The lifter's "Vibrate on taps" setting. The root provides it from the model, the way a theme
 * is provided, so a stepper deep inside a sheet does not need the setting handed down by hand.
 */
val LocalHapticsEnabled = compositionLocalOf { true }

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
