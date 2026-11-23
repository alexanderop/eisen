package io.github.alexanderop.eisen.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.alexanderop.eisen.domain.elm.Cmd

/**
 * Redraws the home-screen widget and republishes the launcher shortcuts with what
 * `Cmd.RefreshHomeSurfaces` carries. `update` sends it only when either of them changed.
 */
object HomeSurfaces {
    /** A workout taps through sets quickly. The widget redraws once the taps pause, not after each one. */
    private const val DELAY_MS = 600L

    private val handler = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private val refresh = Runnable { refreshNow() }

    /**
     * The newest content, also for a widget that asks to be drawn by itself (added, resized, or its
     * periodic update). `init` always sends one, so it is there once the runtime started.
     */
    var latest: Cmd.RefreshHomeSurfaces? = null
        private set

    fun refresh(context: Context, cmd: Cmd.RefreshHomeSurfaces) {
        this.context = context.applicationContext
        latest = cmd
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, DELAY_MS)
    }

    private fun refreshNow() {
        val context = context ?: return
        val cmd = latest ?: return
        // Neither is worth crashing the app over: a launcher can refuse shortcuts, a host can be gone.
        runCatching { NextWorkoutWidget.updateAll(context, cmd.widget) }.onFailure { Log.w("Eisen", "Widget update failed", it) }
        runCatching { Shortcuts.sync(context, cmd.shortcuts) }.onFailure { Log.w("Eisen", "Shortcut update failed", it) }
    }
}
