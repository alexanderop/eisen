import { roundTo, type Unit } from './units'

export type WeightedId = 'hackSquat' | 'bench' | 'row' | 'ohp' | 'deadlift'
export type BodyweightId = 'dips' | 'pullups'
export type ExerciseId = WeightedId | BodyweightId
export type WorkoutType = 'A' | 'B'

export const WEIGHTED_IDS: WeightedId[] = ['hackSquat', 'bench', 'row', 'ohp', 'deadlift']
const BODYWEIGHT_IDS: BodyweightId[] = ['dips', 'pullups']
export const EXERCISE_IDS: ExerciseId[] = [...WEIGHTED_IDS, ...BODYWEIGHT_IDS]

export const EXERCISE_NAMES: Record<ExerciseId, string> = {
  hackSquat: 'Hack Squat',
  bench: 'Bench Press',
  row: 'Barbell Row',
  ohp: 'Overhead Press',
  deadlift: 'Deadlift',
  dips: 'Dips',
  pullups: 'Pull-ups',
}

/** Bodyweight exercises carry no load: only the reps are logged. */
export function isBodyweight(id: ExerciseId): id is BodyweightId {
  return (BODYWEIGHT_IDS as ExerciseId[]).includes(id)
}

/** Machine exercises have no bar: the logged weight is only the plates on the sled. */
const MACHINE_EXERCISES: ReadonlySet<ExerciseId> = new Set(['hackSquat'])

export function isMachine(id: ExerciseId): boolean {
  return MACHINE_EXERCISES.has(id)
}

/** Weight that is on the exercise before any plates are loaded. */
export function emptyWeight(id: ExerciseId, bar: number): number {
  return isMachine(id) ? 0 : bar
}

interface Slot {
  id: ExerciseId
  sets: number
  /** null = as many reps as possible. */
  reps: number | null
}

export const PROGRAM: Record<WorkoutType, Slot[]> = {
  A: [
    { id: 'hackSquat', sets: 5, reps: 5 },
    { id: 'bench', sets: 5, reps: 5 },
    { id: 'row', sets: 5, reps: 5 },
    { id: 'dips', sets: 3, reps: null },
  ],
  B: [
    { id: 'hackSquat', sets: 5, reps: 5 },
    { id: 'ohp', sets: 5, reps: 5 },
    { id: 'deadlift', sets: 1, reps: 5 },
    { id: 'pullups', sets: 3, reps: null },
  ],
}

interface UnitDefaults {
  bar: number
  /** Smallest jump the bar can make: two of the smallest plates. */
  step: number
  plates: number[]
  start: Record<WeightedId, number>
  increment: Record<WeightedId, number>
}

export const UNIT_DEFAULTS: Record<Unit, UnitDefaults> = {
  kg: {
    bar: 20,
    step: 2.5,
    plates: [25, 20, 15, 10, 5, 2.5, 1.25],
    start: { hackSquat: 20, bench: 20, row: 30, ohp: 20, deadlift: 40 },
    increment: { hackSquat: 2.5, bench: 2.5, row: 2.5, ohp: 2.5, deadlift: 5 },
  },
  lb: {
    bar: 45,
    step: 5,
    plates: [45, 35, 25, 10, 5, 2.5],
    start: { hackSquat: 50, bench: 45, row: 65, ohp: 45, deadlift: 95 },
    increment: { hackSquat: 5, bench: 5, row: 5, ohp: 5, deadlift: 10 },
  },
}

export const DELOAD_AFTER_FAILS = 3
export const DELOAD_FACTOR = 0.9

export interface ExerciseProgress {
  weight: number
  /** Workouts in a row where this weight was not completed. */
  fails: number
}

export type Progress = Record<WeightedId, ExerciseProgress>

export interface ExerciseLog {
  id: ExerciseId
  /** 0 for bodyweight exercises. */
  weight: number
  /** null = as many reps as possible. */
  targetReps: number | null
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
  id: WeightedId
  kind: 'up' | 'repeat' | 'deload'
  from: number
  to: number
}

export function initialProgress(unit: Unit): Progress {
  const start = UNIT_DEFAULTS[unit].start
  return Object.fromEntries(
    WEIGHTED_IDS.map((id) => [id, { weight: start[id], fails: 0 }]),
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
      weight: isBodyweight(slot.id) ? 0 : progress[slot.id].weight,
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

/** A max-reps exercise succeeds as soon as any set is logged: it has no target to miss. */
export function outcome(log: ExerciseLog): Outcome {
  if (log.sets.every((reps) => reps === null)) return 'skipped'
  if (log.targetReps === null) return 'success'
  return log.sets.every((reps) => reps === log.targetReps) ? 'success' : 'fail'
}

export function isComplete(workout: Workout): boolean {
  return workout.exercises.every((log) => log.sets.every((reps) => reps !== null))
}

export function otherType(type: WorkoutType): WorkoutType {
  return type === 'A' ? 'B' : 'A'
}

export function totalReps(log: ExerciseLog): number {
  return log.sets.reduce<number>((sum, reps) => sum + (reps ?? 0), 0)
}

/**
 * Progressive overload: all reps done -> add weight next time.
 * Missed reps -> repeat the weight. Three misses in a row -> drop 10%.
 * Bodyweight exercises have no weight to progress.
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
    const id = log.id
    if (isBodyweight(id)) continue
    const result = outcome(log)
    if (result === 'skipped') continue

    if (result === 'success') {
      const to = log.weight + defaults.increment[id]
      next[id] = { weight: to, fails: 0 }
      changes.push({ id, kind: 'up', from: log.weight, to })
      continue
    }

    const fails = progress[id].fails + 1
    if (fails >= DELOAD_AFTER_FAILS) {
      const floor = Math.max(defaults.step, emptyWeight(id, bar))
      const to = Math.max(floor, roundTo(log.weight * DELOAD_FACTOR, defaults.step))
      next[id] = { weight: to, fails: 0 }
      changes.push({ id, kind: 'deload', from: log.weight, to })
    } else {
      next[id] = { weight: log.weight, fails }
      changes.push({ id, kind: 'repeat', from: log.weight, to: log.weight })
    }
  }

  return { progress: next, changes }
}
