import { roundTo, type Unit } from './units'

export type ExerciseId = 'squat' | 'bench' | 'row' | 'ohp' | 'deadlift'
export type WorkoutType = 'A' | 'B'

export const EXERCISE_IDS: ExerciseId[] = ['squat', 'bench', 'row', 'ohp', 'deadlift']

export const EXERCISE_NAMES: Record<ExerciseId, string> = {
  squat: 'Squat',
  bench: 'Bench Press',
  row: 'Barbell Row',
  ohp: 'Overhead Press',
  deadlift: 'Deadlift',
}

interface Slot {
  id: ExerciseId
  sets: number
  reps: number
}

export const PROGRAM: Record<WorkoutType, Slot[]> = {
  A: [
    { id: 'squat', sets: 5, reps: 5 },
    { id: 'bench', sets: 5, reps: 5 },
    { id: 'row', sets: 5, reps: 5 },
  ],
  B: [
    { id: 'squat', sets: 5, reps: 5 },
    { id: 'ohp', sets: 5, reps: 5 },
    { id: 'deadlift', sets: 1, reps: 5 },
  ],
}

interface UnitDefaults {
  bar: number
  /** Smallest jump the bar can make: two of the smallest plates. */
  step: number
  plates: number[]
  start: Record<ExerciseId, number>
  increment: Record<ExerciseId, number>
}

export const UNIT_DEFAULTS: Record<Unit, UnitDefaults> = {
  kg: {
    bar: 20,
    step: 2.5,
    plates: [25, 20, 15, 10, 5, 2.5, 1.25],
    start: { squat: 20, bench: 20, row: 30, ohp: 20, deadlift: 40 },
    increment: { squat: 2.5, bench: 2.5, row: 2.5, ohp: 2.5, deadlift: 5 },
  },
  lb: {
    bar: 45,
    step: 5,
    plates: [45, 35, 25, 10, 5, 2.5],
    start: { squat: 45, bench: 45, row: 65, ohp: 45, deadlift: 95 },
    increment: { squat: 5, bench: 5, row: 5, ohp: 5, deadlift: 10 },
  },
}

export const DELOAD_AFTER_FAILS = 3
export const DELOAD_FACTOR = 0.9

export interface ExerciseProgress {
  weight: number
  /** Workouts in a row where this weight was not completed. */
  fails: number
}

export type Progress = Record<ExerciseId, ExerciseProgress>

export interface ExerciseLog {
  id: ExerciseId
  weight: number
  targetReps: number
  /** null = set not logged yet. */
  sets: (number | null)[]
}

export interface Workout {
  id: string
  type: WorkoutType
  unit: Unit
  startedAt: number
  finishedAt: number | null
  exercises: ExerciseLog[]
}

export type Outcome = 'success' | 'fail' | 'skipped'

export interface Change {
  id: ExerciseId
  kind: 'up' | 'repeat' | 'deload'
  from: number
  to: number
}

export function initialProgress(unit: Unit): Progress {
  const start = UNIT_DEFAULTS[unit].start
  return Object.fromEntries(
    EXERCISE_IDS.map((id) => [id, { weight: start[id], fails: 0 }]),
  ) as Progress
}

export function createWorkout(
  type: WorkoutType,
  progress: Progress,
  unit: Unit,
  now: number,
  id: string,
): Workout {
  return {
    id,
    type,
    unit,
    startedAt: now,
    finishedAt: null,
    exercises: PROGRAM[type].map((slot) => ({
      id: slot.id,
      weight: progress[slot.id].weight,
      targetReps: slot.reps,
      sets: Array.from({ length: slot.sets }, () => null),
    })),
  }
}

/** One tap on a set: empty -> all reps -> one fewer each tap -> 0 -> empty. */
export function nextReps(current: number | null, target: number): number | null {
  if (current === null) return target
  if (current === 0) return null
  return current - 1
}

export function outcome(log: ExerciseLog): Outcome {
  if (log.sets.every((reps) => reps === null)) return 'skipped'
  return log.sets.every((reps) => reps === log.targetReps) ? 'success' : 'fail'
}

export function isComplete(workout: Workout): boolean {
  return workout.exercises.every((log) => log.sets.every((reps) => reps !== null))
}

export function otherType(type: WorkoutType): WorkoutType {
  return type === 'A' ? 'B' : 'A'
}

/**
 * Progressive overload: all reps done -> add weight next time.
 * Missed reps -> repeat the weight. Three misses in a row -> drop 10%.
 */
export function applyProgression(
  progress: Progress,
  workout: Workout,
  bar: number,
): { progress: Progress; changes: Change[] } {
  const defaults = UNIT_DEFAULTS[workout.unit]
  const next: Progress = { ...progress }
  const changes: Change[] = []

  for (const log of workout.exercises) {
    const result = outcome(log)
    if (result === 'skipped') continue

    if (result === 'success') {
      const to = log.weight + defaults.increment[log.id]
      next[log.id] = { weight: to, fails: 0 }
      changes.push({ id: log.id, kind: 'up', from: log.weight, to })
      continue
    }

    const fails = progress[log.id].fails + 1
    if (fails >= DELOAD_AFTER_FAILS) {
      const to = Math.max(bar, roundTo(log.weight * DELOAD_FACTOR, defaults.step))
      next[log.id] = { weight: to, fails: 0 }
      changes.push({ id: log.id, kind: 'deload', from: log.weight, to })
    } else {
      next[log.id] = { weight: log.weight, fails }
      changes.push({ id: log.id, kind: 'repeat', from: log.weight, to: log.weight })
    }
  }

  return { progress: next, changes }
}
