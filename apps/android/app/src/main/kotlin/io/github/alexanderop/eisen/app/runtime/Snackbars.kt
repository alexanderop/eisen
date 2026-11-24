package io.github.alexanderop.eisen.app.runtime

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import io.github.alexanderop.eisen.app.elm.Msg
import io.github.alexanderop.eisen.app.elm.UiEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one snackbar queue of the app. It lives with the process and not with the screen, so turning
 * the phone or a recreated activity does not lose an Undo. The activity only shows [host].
 */
class Snackbars internal constructor(private val context: Context, private val dispatch: (Msg) -> Unit) {
    val host = SnackbarHostState()
    private val scope = MainScope()
    private val shown = mutableMapOf<Job, String?>()

    internal fun show(snackbar: UiEvent.Snackbar) {
        // A newer snackbar replaces the one showing, so only the newest Undo is offered.
        host.currentSnackbarData?.dismiss()
        val job = scope.launch {
            var result: SnackbarResult? = null
            try {
                // Indefinite plus a timeout: the snackbar's own durations are 4 or 10 seconds, and
                // this one must time out also while no host is on screen.
                result = withTimeoutOrNull(timeout(snackbar)) {
                    host.showSnackbar(snackbar.text, actionLabel = snackbar.actionLabel, duration = SnackbarDuration.Indefinite)
                }
            } catch (_: CancellationException) {
                // Taken away by dismiss(): it went without its action.
            } finally {
                val msg = if (result == SnackbarResult.ActionPerformed) snackbar.onAction else snackbar.onDismiss
                msg?.let(dispatch)
            }
        }
        shown[job] = snackbar.tag
        job.invokeOnCompletion { shown.remove(job) }
    }

    internal fun dismiss(tag: String) {
        shown.filterValues { it == tag }.keys.toList().forEach { it.cancel() }
    }

    /** Longer when the accessibility settings ask for more time to act. */
    private fun timeout(snackbar: UiEvent.Snackbar): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return snackbar.millis
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return snackbar.millis
        var flags = AccessibilityManager.FLAG_CONTENT_TEXT
        if (snackbar.actionLabel != null) flags = flags or AccessibilityManager.FLAG_CONTENT_CONTROLS
        return manager.getRecommendedTimeoutMillis(snackbar.millis.toInt(), flags).toLong()
    }
}
