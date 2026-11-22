package io.github.alexanderop.eisen.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.os.BundleCompat
import io.github.alexanderop.eisen.MainActivity
import io.github.alexanderop.eisen.R
import io.github.alexanderop.eisen.data.Store
import io.github.alexanderop.eisen.ui.fmtLoad

/** One exercise as the home screen's "Next up" card shows it. [load] is "80kg", "BW" or "BW + 10kg". */
private class Row(val name: String, val sets: Int, val reps: Int?, val load: String)

/** What the widget shows: the running workout, or the next one as it is set up right now. */
private class Content(
    val running: Boolean,
    /** The workout the button starts. Null while one runs, or when there is no workout at all. */
    val routineId: String?,
    val name: String,
    val rows: List<Row>,
    val logged: Int,
    val total: Int,
)

private fun content(): Content {
    val state = Store.state
    val active = state.active
    if (active != null) {
        val sets = active.exercises.flatMap { it.sets }
        return Content(
            running = true,
            routineId = null,
            name = active.name,
            rows = active.exercises.map { log ->
                Row(log.name, log.sets.size, log.targetReps, fmtLoad(log.equipment, log.weight))
            },
            logged = sets.count { it != null },
            total = sets.size,
        )
    }
    // Store.nextRoutine assumes at least one workout. A broken import must not take the widget down with it.
    val next = state.routines.firstOrNull { it.id == state.nextRoutineId } ?: state.routines.firstOrNull()
        ?: return Content(running = false, routineId = null, name = "Eisen", rows = emptyList(), logged = 0, total = 0)
    val rows = next.slots.mapNotNull { slot ->
        val exercise = Store.findExercise(slot.exerciseId) ?: return@mapNotNull null
        Row(
            exercise.name,
            slot.sets,
            slot.reps,
            fmtLoad(exercise.equipment, Store.progressOf(exercise.id).weight),
        )
    }
    return Content(running = false, routineId = next.id, name = next.name, rows = rows, logged = 0, total = 0)
}

/**
 * The "Next workout" home-screen widget. It shows what the home screen's "Next up" card shows,
 * with a button that starts that workout, or resumes the one that runs.
 */
class NextWorkoutWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // The process may have been started only for the widget.
        Store.init(context.applicationContext)
        val content = content()
        for (id in ids) render(context, manager, id, content)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        Store.init(context.applicationContext)
        render(context, manager, id, content())
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, NextWorkoutWidget::class.java))
            if (ids.isEmpty()) return
            val content = content()
            for (id in ids) render(context, manager, id, content)
        }
    }
}

private fun render(context: Context, manager: AppWidgetManager, id: Int, content: Content) {
    val options = manager.getAppWidgetOptions(id)
    val sizes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BundleCompat.getParcelableArrayList<SizeF>(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java).orEmpty()
    } else {
        emptyList()
    }
    val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.isNotEmpty()) {
        // One layout per size the launcher may draw the widget at. It picks the one that fits, also on rotation.
        RemoteViews(sizes.distinct().take(16).associateWith { build(context, content, it.height) })
    } else {
        build(context, content, legacyHeight(context, options))
    }
    manager.updateAppWidget(id, views)
}

/** Before Android 12 the launcher only reports a range: the tall end in portrait, the short end in landscape. */
private fun legacyHeight(context: Context, options: Bundle): Float {
    val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    val height = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    return if (height > 0) height.toFloat() else 110f
}

private fun build(context: Context, content: Content, heightDp: Float): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_next_workout)
    views.setOnClickPendingIntent(android.R.id.background, openApp(context))
    views.setTextViewText(R.id.widget_eyebrow, (if (content.running) "Workout running" else "Next up").uppercase())
    views.setTextViewText(R.id.widget_name, content.name)

    if (content.running) {
        views.setTextViewText(R.id.widget_status, "${content.logged} of ${content.total} sets logged")
        views.setViewVisibility(R.id.widget_status, View.VISIBLE)
    } else {
        views.setViewVisibility(R.id.widget_status, View.GONE)
    }

    // A workout without exercises cannot start, like the disabled button on the home screen.
    val canStart = content.running || (content.routineId != null && content.rows.isNotEmpty())
    if (canStart) {
        views.setTextViewText(R.id.widget_button, if (content.running) "Resume" else "Start")
        views.setOnClickPendingIntent(R.id.widget_button, startWorkout(context, content.routineId))
        views.setViewVisibility(R.id.widget_button, View.VISIBLE)
    } else {
        views.setViewVisibility(R.id.widget_button, View.GONE)
    }

    // The launcher may reuse the views it shows, so old rows have to go before new ones are added.
    views.removeAllViews(R.id.widget_rows)
    views.setViewVisibility(R.id.widget_more, View.GONE)
    views.setViewVisibility(R.id.widget_empty, View.GONE)
    if (content.rows.isEmpty() && !content.running) {
        views.setTextViewText(R.id.widget_empty, "This workout has no exercises yet. Add some in the Workouts tab.")
        views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
        return views
    }
    val space = RowSpace(context, content.running)
    val shown = space.rowsThatFit(heightDp, content.rows.size)
    for (row in content.rows.take(shown)) {
        val item = RemoteViews(context.packageName, R.layout.widget_row)
        item.setTextViewText(R.id.widget_row_name, row.name)
        item.setTextViewText(R.id.widget_row_sets, "${row.sets}×${row.reps ?: "max"}")
        item.setTextViewText(R.id.widget_row_weight, row.load)
        views.addView(R.id.widget_rows, item)
    }
    val hidden = content.rows.size - shown
    if (hidden > 0 && space.moreFits(heightDp, shown)) {
        views.setTextViewText(R.id.widget_more, "+$hidden more")
        views.setViewVisibility(R.id.widget_more, View.VISIBLE)
    }
    return views
}

/** Heights from the widget_* dimens the layouts use, in dp, grown with the system font size. */
private class RowSpace(context: Context, running: Boolean) {
    private val resources = context.resources
    private val scale = resources.configuration.fontScale.coerceAtLeast(1f)
    private fun dp(id: Int) = resources.getDimension(id) / resources.displayMetrics.density

    private val fixed = 2 * dp(R.dimen.widget_padding_vertical) + dp(R.dimen.widget_gap) +
        dp(R.dimen.widget_header_height) * scale + (if (running) dp(R.dimen.widget_status_height) * scale else 0f)
    private val row = dp(R.dimen.widget_row_height) * scale
    private val more = dp(R.dimen.widget_more_height) * scale

    /** All rows, or as many as leave room for the "+2 more" line. */
    fun rowsThatFit(heightDp: Float, total: Int): Int {
        val free = heightDp - fixed
        if (total * row <= free) return total
        return ((free - more) / row).toInt().coerceIn(0, total)
    }

    fun moreFits(heightDp: Float, shown: Int) = heightDp - fixed - shown * row >= more
}

private fun immutable() = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

private fun openApp(context: Context): PendingIntent {
    // The launcher's own intent, so a running app comes to the front as it was instead of restarting.
    val intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    return PendingIntent.getActivity(context, 0, intent, immutable())
}

private fun startWorkout(context: Context, routineId: String?): PendingIntent =
    PendingIntent.getActivity(context, 1, WorkoutLaunch.intent(context, routineId), immutable())
