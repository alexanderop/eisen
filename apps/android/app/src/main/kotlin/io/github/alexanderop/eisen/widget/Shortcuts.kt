package io.github.alexanderop.eisen.widget

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.alexanderop.eisen.R
import io.github.alexanderop.eisen.domain.elm.ShortcutSpec

/**
 * The launcher shortcuts on a long press of the app icon. Which ones and in what order is decided by
 * `shortcutsOf` in `domain/elm/HomeSurfaces.kt`; this publishes them.
 */
internal object Shortcuts {
    /** What was published last, as id and label per shortcut. Null until the first sync of this process. */
    private var published: List<String>? = null

    /**
     * Publishes only when the list changed. Android limits how often an app in the background may
     * publish, and most changes (a logged set, a new weight) do not touch the shortcuts.
     */
    fun sync(context: Context, specs: List<ShortcutSpec>) {
        // The phone may allow fewer than the app asks for.
        val wanted = specs.take(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)).mapIndexed { rank, spec -> build(context, spec, rank) }
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

    private fun build(context: Context, spec: ShortcutSpec, rank: Int): ShortcutInfoCompat {
        val icon = if (spec.routineId == null) R.drawable.ic_shortcut_resume else R.drawable.ic_shortcut_start
        return ShortcutInfoCompat.Builder(context, spec.id)
            .setShortLabel(spec.shortLabel)
            .setLongLabel(spec.longLabel)
            .setRank(rank)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(WorkoutLaunch.intent(context, spec.routineId))
            .build()
    }
}
