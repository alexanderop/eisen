import type { Workout } from './program'
import { formatWeight } from './units'

export function durationMinutes(workout: Workout): number | null {
  if (workout.finishedAt === null) return null
  return Math.max(1, Math.round((workout.finishedAt - workout.startedAt) / 60000))
}

/**
 * A workout as Markdown for pasting into a chat or a note.
 * A list, not a table: chat apps show lists fine and tables as pipes.
 * Exercises without a logged set are left out.
 */
export function workoutMarkdown(workout: Workout, date: string): string {
  const minutes = durationMinutes(workout)
  const lines = [`## ${workout.name}`, '', minutes === null ? date : `${date} · ${minutes} min`, '']
  for (const log of workout.exercises) {
    if (log.sets.every((reps) => reps === null)) continue
    const load = log.equipment === 'bodyweight' ? 'BW' : formatWeight(log.weight, workout.unit)
    const full = log.targetReps !== null && log.sets.every((reps) => reps === log.targetReps)
    const reps = full
      ? `${log.sets.length}×${log.targetReps}`
      : log.sets.map((count) => count ?? '–').join(', ')
    lines.push(`- **${log.name}** ${load}: ${reps}`)
  }
  return lines.join('\n')
}
