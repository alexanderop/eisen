package io.github.alexanderop.eisen.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import io.github.alexanderop.eisen.data.Store

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
class Haptics(private val view: View) {
    fun perform(kind: Haptic) {
        if (!Store.state.settings.haptics) return
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

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
