package io.github.alexanderop.eisen.domain

import kotlin.math.max

fun durationMinutes(workout: Workout): Long? {
    val finished = workout.finishedAt ?: return null
    return max(1, Math.round((finished - workout.startedAt) / 60000.0))
}

/**
 * A workout as Markdown for pasting into a chat or a note.
 * A list, not a table: chat apps show lists fine and tables as pipes.
 * Exercises without a logged set are left out.
 */
fun workoutMarkdown(workout: Workout, date: String): String {
    val minutes = durationMinutes(workout)
    val lines = mutableListOf("## ${workout.name}", "", if (minutes == null) date else "$date · $minutes min", "")
    for (log in workout.exercises) {
        if (log.sets.all { it == null }) continue
        val load = if (log.equipment == Equipment.BODYWEIGHT) "BW" else formatWeight(log.weight, workout.unit)
        val full = log.targetReps != null && log.sets.all { it == log.targetReps }
        val reps = if (full) "${log.sets.size}×${log.targetReps}" else log.sets.joinToString(", ") { it?.toString() ?: "–" }
        lines += "- **${log.name}** $load: $reps"
    }
    return lines.joinToString("\n")
}
