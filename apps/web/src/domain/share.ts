import { loadText, MAX_REPS, MAX_SETS, type Workout } from './program'
import type { Unit } from './units'

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
    const load = loadText(log.equipment === 'bodyweight', log.weight, workout.unit)
    lines.push(`- **${log.name}** ${load}: ${repsText(log)}`)
  }
  return lines.join('\n')
}

export interface SharedExercise {
  name: string
  bodyweight: boolean
  /** For bodyweight exercises the weight added on top of the body: 0 = none. */
  weight: number
  /** null = as many reps as possible. */
  targetReps: number | null
  /** null = set not logged. */
  sets: (number | null)[]
  /** Set a record on that day. */
  record: boolean
}

/**
 * What a shared workout carries: enough to show it, nothing else from the app.
 * It travels inside a link and is drawn on the share image.
 */
export interface SharedWorkout {
  name: string
  unit: Unit
  startedAt: number
  minutes: number | null
  exercises: SharedExercise[]
}

/** Exercises without a logged set are left out, as in the Markdown. */
export function toShared(workout: Workout, records: ReadonlySet<string> = new Set()): SharedWorkout {
  return {
    name: workout.name,
    unit: workout.unit,
    startedAt: workout.startedAt,
    minutes: durationMinutes(workout),
    exercises: workout.exercises
      .filter((log) => log.sets.some((reps) => reps !== null))
      .map((log) => ({
        name: log.name,
        bodyweight: log.equipment === 'bodyweight',
        weight: log.weight,
        targetReps: log.targetReps,
        sets: [...log.sets],
        record: records.has(log.id),
      })),
  }
}

export interface SharedStats {
  /**
   * Weight times reps over all exercises, in the workout's unit.
   * A bodyweight exercise adds only its added weight: the app does not know what the lifter weighs.
   */
  volume: number
  sets: number
  reps: number
}

export function sharedStats(shared: SharedWorkout): SharedStats {
  const stats = { volume: 0, sets: 0, reps: 0 }
  for (const exercise of shared.exercises) {
    for (const reps of exercise.sets) {
      if (reps === null) continue
      stats.sets += 1
      stats.reps += reps
      stats.volume += exercise.weight * reps
    }
  }
  return stats
}

/** "5×5" when every set hit the target, otherwise each set: "5, 5, 4, –, –". */
export function repsText(exercise: Pick<SharedExercise, 'sets' | 'targetReps'>): string {
  const full =
    exercise.targetReps !== null && exercise.sets.every((reps) => reps === exercise.targetReps)
  return full
    ? `${exercise.sets.length}×${exercise.targetReps}`
    : exercise.sets.map((count) => count ?? '–').join(', ')
}

const SHARE_VERSION = 1
const MINUTE_MS = 60000
/** Limits for a link someone else made: it is input from a stranger. */
const MAX_SHARED_EXERCISES = 40
const MAX_NAME = 80

function toBase64Url(text: string): string {
  const bytes = new TextEncoder().encode(text)
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '')
}

function fromBase64Url(code: string): string {
  const binary = atob(code.replaceAll('-', '+').replaceAll('_', '/'))
  return new TextDecoder('utf-8', { fatal: true }).decode(
    Uint8Array.from(binary, (char) => char.charCodeAt(0)),
  )
}

/**
 * A workout as text for a link. Arrays instead of named fields keep the link short:
 * [version, name, unit, start minute, minutes, [name, bodyweight, weight, target, record, sets][]].
 * 0 stands for "max reps" as a target and -1 for a set that was not logged.
 */
export function encodeShared(shared: SharedWorkout): string {
  return toBase64Url(
    JSON.stringify([
      SHARE_VERSION,
      shared.name,
      shared.unit === 'lb' ? 1 : 0,
      Math.round(shared.startedAt / MINUTE_MS),
      shared.minutes ?? 0,
      shared.exercises.map((exercise) => [
        exercise.name,
        exercise.bodyweight ? 1 : 0,
        Number(exercise.weight.toFixed(2)),
        exercise.targetReps ?? 0,
        exercise.record ? 1 : 0,
        exercise.sets.map((reps) => reps ?? -1),
      ]),
    ]),
  )
}

function isCount(value: unknown, max: number): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= max
}

function isName(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= MAX_NAME
}

function decodeExercise(value: unknown): SharedExercise | null {
  if (!Array.isArray(value)) return null
  const [name, bodyweight, weight, target, record, sets] = value as unknown[]
  if (!isName(name) || !isCount(target, MAX_REPS) || !Array.isArray(sets)) return null
  if (typeof weight !== 'number' || !Number.isFinite(weight) || weight < 0 || weight > 10000) return null
  if (sets.length === 0 || sets.length > MAX_SETS) return null
  if (!sets.every((reps) => reps === -1 || isCount(reps, 999))) return null
  return {
    name,
    bodyweight: bodyweight === 1,
    // Older builds sent 0 here for bodyweight exercises. Newer ones send the added weight.
    weight,
    targetReps: target === 0 ? null : target,
    sets: (sets as number[]).map((reps) => (reps === -1 ? null : reps)),
    record: record === 1,
  }
}

/** Reads a workout back out of a link. Anything that is not one gives null. */
export function decodeShared(code: string): SharedWorkout | null {
  let data: unknown
  try {
    data = JSON.parse(fromBase64Url(code))
  } catch {
    return null
  }
  if (!Array.isArray(data)) return null
  const [version, name, unit, startMinute, minutes, list] = data as unknown[]
  if (version !== SHARE_VERSION || !isName(name) || !Array.isArray(list)) return null
  if (!isCount(startMinute, 1e8) || !isCount(minutes, 100000)) return null
  if (list.length === 0 || list.length > MAX_SHARED_EXERCISES) return null
  const exercises = list.map(decodeExercise)
  if (exercises.some((exercise) => exercise === null)) return null
  return {
    name,
    unit: unit === 1 ? 'lb' : 'kg',
    startedAt: startMinute * MINUTE_MS,
    minutes: minutes === 0 ? null : minutes,
    exercises: exercises as SharedExercise[],
  }
}

const HASH_KEY = '#w='

/** The link that opens the app showing this workout. `base` is the app's address without a hash. */
export function sharedUrl(base: string, shared: SharedWorkout): string {
  return `${base.split('#')[0]}${HASH_KEY}${encodeShared(shared)}`
}

export function sharedFromHash(hash: string): SharedWorkout | null {
  return hash.startsWith(HASH_KEY) ? decodeShared(hash.slice(HASH_KEY.length)) : null
}
