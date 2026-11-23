import { longestHold, outcome, totalReps, type ExerciseLog, type Workout } from './program'
import { convert, type Unit } from './units'

export interface Logged {
  workout: Workout
  log: ExerciseLog
}

function logsOf(history: Workout[], id: string): Logged[] {
  return history.flatMap((workout) =>
    workout.exercises.filter((log) => log.id === id).map((log) => ({ workout, log })),
  )
}

/** The newest finished workout with a logged set of this exercise. History is newest first. */
export function lastLogged(history: Workout[], id: string): Logged | null {
  return logsOf(history, id).find(({ log }) => log.sets.some((reps) => reps !== null)) ?? null
}

/**
 * What a logged exercise counts as for records, comparable across units.
 * Bodyweight exercises count their total reps, timed ones their longest hold in seconds.
 * Weighted ones count the weight in kg, and only when every rep was done:
 * a weight with missed reps is not a record.
 */
export function recordValue(log: ExerciseLog, unit: Unit): number | null {
  if (log.equipment === 'bodyweight') return (log.timed ? longestHold(log) : totalReps(log)) || null
  return outcome(log) === 'success' ? convert(log.weight, unit, 'kg') : null
}

/** Converting between units leaves rounding noise. A record has to beat it. */
const NOISE = 0.01

/** The log that holds the record of this exercise: the earliest one with the highest value. */
export function recordOf(history: Workout[], id: string): Logged | null {
  let best: Logged | null = null
  let bestValue = 0
  for (const entry of logsOf(history, id).reverse()) {
    const value = recordValue(entry.log, entry.workout.unit)
    if (value !== null && value > bestValue + NOISE) {
      best = entry
      bestValue = value
    }
  }
  return best
}

/**
 * Ids of the exercises that set a record in the workout at this position of the history.
 * A first log has nothing to beat, so it is not a record.
 */
export function recordIds(history: Workout[], index: number): Set<string> {
  const workout = history[index]
  const earlier = history.slice(index + 1)
  const ids = new Set<string>()
  for (const log of workout?.exercises ?? []) {
    const value = recordValue(log, workout.unit)
    const before = recordOf(earlier, log.id)
    if (value === null || !before) continue
    const beaten = recordValue(before.log, before.workout.unit) ?? 0
    if (value > beaten + NOISE) ids.add(log.id)
  }
  return ids
}

/** What this set of the exercise got the last time it was logged. Null when it never was. */
export function lastSetReps(history: Workout[], id: string, setIndex: number): number | null {
  for (const workout of history) {
    const reps = workout.exercises.find((log) => log.id === id)?.sets[setIndex]
    if (reps !== null && reps !== undefined) return reps
  }
  return null
}
