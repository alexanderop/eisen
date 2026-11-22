package io.github.alexanderop.eisen.widget

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.alexanderop.eisen.R
import io.github.alexanderop.eisen.data.Store

/** The launcher shortcuts on a long press of the app icon: one per workout, or "Resume workout". */
internal object Shortcuts {
    private const val RESUME_ID = "resume"
    private const val MAX = 4

    /** What was published last, as id and label per shortcut. Null until the first sync of this process. */
    private var published: List<String>? = null

    /**
     * Publishes only when the list changed. Android limits how often an app in the background may
     * publish, and most changes (a logged set, a new weight) do not touch the shortcuts.
     */
    fun sync(context: Context) {
        val wanted = wanted(context)
        val key = wanted.map { "${it.id}\n${it.shortLabel}" }
        // After a restart the launcher still has the last list. Compare against that instead of publishing again.
        val current = published ?: ShortcutManagerCompat.getDynamicShortcuts(context)
            .sortedBy { it.rank }
            .map { "${it.id}\n${it.shortLabel}" }
        if (key == current) {
            published = key
            return
        }
        if (ShortcutManagerCompat.setDynamicShortcuts(context, wanted)) published = key
    }

    private fun wanted(context: Context): List<ShortcutInfoCompat> {
        val state = Store.state
        if (state.active != null) {
            return listOf(
                ShortcutInfoCompat.Builder(context, RESUME_ID)
                    .setShortLabel("Resume")
                    .setLongLabel("Resume workout")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_resume))
                    .setIntent(WorkoutLaunch.intent(context, null))
                    .build(),
            )
        }
        val routines = state.routines
        val next = routines.firstOrNull { it.id == state.nextRoutineId } ?: routines.firstOrNull() ?: return emptyList()
        val limit = minOf(MAX, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))
        return (listOf(next) + routines.filter { it.id != next.id }).take(limit).mapIndexed { rank, routine ->
            // Android refuses a shortcut without a label.
            val name = routine.name.ifBlank { "Workout" }
            ShortcutInfoCompat.Builder(context, "routine-${routine.id}")
                .setShortLabel(name)
                .setLongLabel("Start $name")
                .setRank(rank)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_start))
                .setIntent(WorkoutLaunch.intent(context, routine.id))
                .build()
        }
    }
}
