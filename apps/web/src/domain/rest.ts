import type { Equipment } from './exercises'
import { emptyWeight, loadText, setText, stepFor, type ExerciseLog, type Workout } from './program'
import type { Unit } from './units'

/** The set whose rest is running. The rest screen shows it and corrects it. */
export interface RestSet {
  exerciseId: string
  exerciseIndex: number
  setIndex: number
}

/**
 * The exercise of the set that started the rest, or null once it no longer points at a logged set:
 * the workout ended, the exercise was removed or moved, or the set was cleared.
 */
export function restSetLog(workout: Workout | null, set: RestSet | null): ExerciseLog | null {
  const log = set ? workout?.exercises[set.exerciseIndex] : undefined
  if (!set || !log || log.id !== set.exerciseId) return null
  const value = log.sets[set.setIndex]
  return value === null || value === undefined ? null : log
}

/** The wheels reach past what anyone lifts on one exercise. */
const WHEEL_TOP: Record<Unit, number> = { kg: 400, lb: 900 }

/**
 * The weights the weight wheel offers: from the empty bar (one step for machines and weights,
 * none for a bodyweight exercise) up in steps. The current weight is always one of them,
 * even when it is off the steps or above the top.
 */
export function weightWheel(equipment: Equipment, unit: Unit, bar: number, current: number): number[] {
  const step = stepFor(equipment, unit)
  const first = equipment === 'bodyweight' ? 0 : Math.max(step, emptyWeight(equipment, bar))
  const top = Math.max(WHEEL_TOP[unit], current)
  const values: number[] = []
  for (let index = 0; first + index * step <= top + 1e-9; index++) {
    values.push(Number((first + index * step).toFixed(2)))
  }
  if (!values.some((value) => Math.abs(value - current) < 1e-9)) {
    values.push(current)
    values.sort((a, b) => a - b)
  }
  return values
}

/** Max-reps sets go up to here on the reps wheel. */
export const WHEEL_MAX_REPS = 100

/** Reps the reps wheel offers: 0 up to the target, or up to 100 for a max-reps set. */
export function repsWheel(targetReps: number | null, current: number): number[] {
  const top = Math.max(targetReps ?? WHEEL_MAX_REPS, current)
  return Array.from({ length: top + 1 }, (_, reps) => reps)
}

/** A row of the weight wheel: the number alone, its unit is in the header. No added weight reads "BW". */
export function wheelWeightText(bodyweight: boolean, weight: number): string {
  return bodyweight && weight === 0 ? 'BW' : String(Number(weight.toFixed(2)))
}

/** What a logged set was, in the app's load text: "80kg × 5", "BW + 10kg × 8", "BW × 1:00". */
export function setSummary(log: ExerciseLog, setIndex: number, unit: Unit): string {
  const load = loadText(log.equipment === 'bodyweight', log.weight, unit)
  return `${load} × ${setText(log.timed, log.sets[setIndex] ?? 0)}`
}

/** The set to do next: the first open one after this set, else the first open one. Null when all are logged. */
export function nextOpenSet(log: ExerciseLog, setIndex: number): number | null {
  const after = log.sets.findIndex((value, index) => index > setIndex && value === null)
  if (after !== -1) return after
  const before = log.sets.indexOf(null)
  return before === -1 ? null : before
}

/**
 * When a rest ends after it was made longer or shorter by some seconds.
 * Longer counts from now once the rest is over. Shorter never ends it before now,
 * and a rest that is already over stays over.
 */
export function adjustedRestEnd(endsAt: number, now: number, seconds: number): number {
  if (seconds >= 0) return Math.max(endsAt, now) + seconds * 1000
  if (endsAt <= now) return endsAt
  return Math.max(now, endsAt + seconds * 1000)
}

/**
 * The sets after a logged set's count was corrected. Only a logged set changes, and 0 stays
 * a logged set: correcting never clears one.
 */
export function withCorrectedReps(
  sets: (number | null)[],
  setIndex: number,
  reps: number,
): (number | null)[] {
  if (sets[setIndex] === null || sets[setIndex] === undefined) return sets
  return sets.map((value, index) => (index === setIndex ? Math.max(0, Math.round(reps)) : value))
}
