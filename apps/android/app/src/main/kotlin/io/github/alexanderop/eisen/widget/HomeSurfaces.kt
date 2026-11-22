package io.github.alexanderop.eisen.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.alexanderop.eisen.data.Store

/** Keeps the home-screen widget and the launcher shortcuts in step with the store. */
object HomeSurfaces {
    /** A workout taps through sets quickly. The widget redraws once the taps pause, not after each one. */
    private const val DELAY_MS = 600L

    private val handler = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private val refresh = Runnable { refreshNow() }

    fun install(context: Context) {
        if (this.context != null) return
        this.context = context.applicationContext
        Store.changeListeners += { schedule() }
        // The first run publishes the shortcuts after an install and redraws widgets after an update.
        schedule()
    }

    private fun schedule() {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, DELAY_MS)
    }

    private fun refreshNow() {
        val context = context ?: return
        // Neither is worth crashing the app over: a launcher can refuse shortcuts, a host can be gone.
        runCatching { NextWorkoutWidget.updateAll(context) }.onFailure { Log.w("Eisen", "Widget update failed", it) }
        runCatching { Shortcuts.sync(context) }.onFailure { Log.w("Eisen", "Shortcut update failed", it) }
    }
}
