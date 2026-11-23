import type { Equipment, Exercise } from './exercises'
import { convert, formatWeight, roundTo, type Unit } from './units'

export interface Slot {
  exerciseId: string
  sets: number
  /** null = as many reps as possible. For a timed exercise the hold in seconds, null = as long as possible. */
  reps: number | null
}

/** A workout the lifter has set up: a name and the exercises to do. */
export interface Routine {
  id: string
  name: string
  slots: Slot[]
}

export const MAX_SETS = 10
export const MAX_REPS = 30

/** Longest hold a timed exercise can have as its target. */
export const MAX_HOLD_SECONDS = 600
export const HOLD_STEP_SECONDS = 5
export const MIN_HOLD_SECONDS = 5
export const DEFAULT_HOLD_SECONDS = 60
/** Time to put the phone down and get into position before a hold starts. */
export const HOLD_LEAD_IN_SECONDS = 5
/** A max hold nobody stopped ends itself here. */
export const MAX_HELD_SECONDS = 3600

export function defaultRoutines(): Routine[] {
  return [
    {
      id: 'A',
      name: 'Workout A',
      slots: [
        { exerciseId: 'hackSquat', sets: 5, reps: 5 },
        { exerciseId: 'bench', sets: 5, reps: 5 },
        { exerciseId: 'row', sets: 5, reps: 5 },
        { exerciseId: 'dips', sets: 3, reps: null },
      ],
    },
    {
      id: 'B',
      name: 'Workout B',
      slots: [
        { exerciseId: 'hackSquat', sets: 5, reps: 5 },
        { exerciseId: 'ohp', sets: 5, reps: 5 },
        { exerciseId: 'deadlift', sets: 5, reps: 5 },
        { exerciseId: 'pullups', sets: 3, reps: null },
      ],
    },
  ]
}

/** Sets and reps a newly added exercise starts with. */
export function defaultSlot(exercise: Exercise): Slot {
  if (exercise.timed) return { exerciseId: exercise.id, sets: 3, reps: DEFAULT_HOLD_SECONDS }
  if (exercise.equipment === 'bodyweight') return { exerciseId: exercise.id, sets: 3, reps: null }
  if (exercise.equipment === 'weight') return { exerciseId: exercise.id, sets: 3, reps: 10 }
  return { exerciseId: exercise.id, sets: 5, reps: 5 }
}

interface UnitDefaults {
  bar: number
  /** Smallest jump the bar can make: two of the smallest plates. */
  step: number
  plates: number[]
}

export const UNIT_DEFAULTS: Record<Unit, UnitDefaults> = {
  kg: { bar: 20, step: 2.5, plates: [20, 15, 10, 5, 2.5, 1.25] },
  lb: { bar: 45, step: 5, plates: [45, 35, 25, 10, 5, 2.5] },
}

const FINE_STEP: Record<Unit, number> = { kg: 1, lb: 2.5 }
const INCREMENT_STEP: Record<Unit, number> = { kg: 0.5, lb: 2.5 }
const INCREMENTS: Record<Unit, number[]> = {
  kg: [0.5, 1, 1.25, 2, 2.5, 5, 7.5, 10],
  lb: [2.5, 5, 10, 15, 20],
}

/**
 * Smallest weight change: plate pairs on a bar, finer for dumbbells and weight stacks.
 * Weight added to a bodyweight exercise hangs from a dip belt or sits in a vest, so it steps like a dumbbell.
 */
export function stepFor(equipment: Equipment, unit: Unit): number {
  return equipment === 'weight' || equipment === 'bodyweight' ? FINE_STEP[unit] : UNIT_DEFAULTS[unit].step
}

/** Warmup weights on a machine use whole plates: nobody loads 1.25s onto a sled to warm up. */
export function warmupStep(equipment: Equipment, unit: Unit): number {
  const step = UNIT_DEFAULTS[unit].step
  return equipment === 'machine' ? step * 2 : step
}

export function incrementStep(unit: Unit): number {
  return INCREMENT_STEP[unit]
}

export function isPlateLoaded(equipment: Equipment): boolean {
  return equipment === 'barbell' || equipment === 'machine'
}

/** Weight that is on the exercise before any plates are loaded. */
export function emptyWeight(equipment: Equipment, bar: number): number {
  return equipment === 'barbell' ? bar : 0
}

export const DELOAD_AFTER_FAILS = 3
export const DELOAD_FACTOR = 0.9

/**
 * For a bodyweight exercise the entry holds the weight added on top of the body,
 * with no fails and no increment. No entry means no added weight.
 */
export interface ExerciseProgress {
  weight: number
  /** Workouts in a row where this weight was not completed. */
  fails: number
  /** Weight a fully completed workout adds. */
  increment: number
}

export type Progress = Record<string, ExerciseProgress>

function nearest(value: number, options: number[]): number {
  return options.reduce((best, option) =>
    Math.abs(option - value) < Math.abs(best - value) ? option : best,
  )
}

function convertWeight(weight: number, equipment: Equipment, from: Unit, to: Unit): number {
  // No added weight stays none. Rounding it up to one step would hang a belt on the lifter.
  if (equipment === 'bodyweight' && weight === 0) return 0
  const step = stepFor(equipment, to)
  return Math.max(step, roundTo(convert(weight, from, to), step))
}

export function startProgress(exercise: Exercise, unit: Unit): ExerciseProgress {
  return {
    weight: convertWeight(exercise.start, exercise.equipment, 'kg', unit),
    fails: 0,
    increment: nearest(convert(exercise.increment, 'kg', unit), INCREMENTS[unit]),
  }
}

export function convertProgress(
  entry: ExerciseProgress,
  equipment: Equipment,
  from: Unit,
  to: Unit,
): ExerciseProgress {
  return {
    weight: convertWeight(entry.weight, equipment, from, to),
    fails: entry.fails,
    // Added weight never goes up by itself, so it has no increment to convert.
    increment: equipment === 'bodyweight' ? 0 : nearest(convert(entry.increment, from, to), INCREMENTS[to]),
  }
}

/**
 * Sets the weight added to a bodyweight exercise. Going back to none removes the entry,
 * so an install that never added weight and one that took it off again look the same.
 */
export function withAddedWeight(progress: Progress, id: string, weight: number): Progress {
  const { [id]: _, ...rest } = progress
  return weight > 0 ? { ...rest, [id]: { weight, fails: 0, increment: 0 } } : rest
}

export interface ExerciseLog {
  id: string
  /** Name and equipment are copied in, so the log still reads right after the exercise is deleted. */
  name: string
  equipment: Equipment
  /** For bodyweight exercises the weight added on top of the body: 0 = none. */
  weight: number
  /** null = as many reps as possible. */
  targetReps: number | null
  /** null = set not logged yet. */
  sets: (number | null)[]
  /** Copied from the exercise: target and sets are seconds held. Left out when false. */
  timed?: boolean
  /**
   * Reps in reserve per set, parallel to `sets`: 0 to 4, where 4 means "four or more".
   * null or no entry = not rated. Left out until a set is rated.
   */
  rir?: (number | null)[]
}

export interface Workout {
  id: string
  routineId: string
  name: string
  unit: Unit
  startedAt: number
  finishedAt: number | null
  exercises: ExerciseLog[]
}

export type Outcome = 'success' | 'fail' | 'skipped'

export interface Change {
  id: string
  name: string
  kind: 'up' | 'repeat' | 'deload'
  from: number
  to: number
  /** The last set had reps in reserve, so the weight went up twice the increment. Left out otherwise. */
  boosted?: boolean
}

/** The highest rating: "four or more". */
export const MAX_RIR = 4
/** A last set with this many reps or more in reserve doubles the next weight jump. */
export const RIR_BOOST_FROM = 3
export const RIR_BOOST_FACTOR = 2

/**
 * Only a logged set with a rep target can be rated. A max-reps set goes to failure by definition,
 * and a hold has no reps to have left.
 */
export function canRate(log: ExerciseLog, setIndex: number): boolean {
  const reps = log.sets[setIndex]
  return log.targetReps !== null && !log.timed && reps !== null && reps !== undefined
}

export function rirOf(log: ExerciseLog, setIndex: number): number | null {
  return log.rir?.[setIndex] ?? null
}

/** The log with one set rated, or unrated with null. A set that cannot be rated stays as it is. */
export function withRir(log: ExerciseLog, setIndex: number, rir: number | null): ExerciseLog {
  if (rir !== null && !canRate(log, setIndex)) return log
  const value = rir === null ? null : Math.min(MAX_RIR, Math.max(0, Math.round(rir)))
  const ratings = Array.from(
    { length: Math.max(log.rir?.length ?? 0, setIndex + 1) },
    (_, index) => (index === setIndex ? value : (log.rir?.[index] ?? null)),
  )
  const { rir: _, ...rest } = log
  return ratings.some((entry) => entry !== null) ? { ...rest, rir: ratings } : rest
}

/** The log with one set's count changed. A cleared set loses its rating, a corrected one keeps it. */
export function withReps(log: ExerciseLog, setIndex: number, reps: number | null): ExerciseLog {
  const next = { ...log, sets: log.sets.map((value, index) => (index === setIndex ? reps : value)) }
  return reps === null ? withRir(next, setIndex, null) : next
}

/** A rating as text: "0" to "3", and "4+". */
export function rirText(rir: number): string {
  return rir >= MAX_RIR ? `${MAX_RIR}+` : String(rir)
}

/** What a screen reader says for a rating: "2", or "4 or more". */
export function rirSpoken(rir: number): string {
  return rir >= MAX_RIR ? `${MAX_RIR} or more` : String(rir)
}

/** "RIR 2 2 1 0", with "–" for a set not rated. Null when no set is rated. */
export function rirLine(log: ExerciseLog): string | null {
  const ratings = log.sets.map((_, index) => rirOf(log, index))
  if (ratings.every((rir) => rir === null)) return null
  return `RIR ${ratings.map((rir) => (rir === null ? '–' : rirText(rir))).join(' ')}`
}

/** " · RIR 2 2 1 0" to put after the reps. Empty when no set is rated. */
export function rirSuffix(log: ExerciseLog): string {
  const line = rirLine(log)
  return line === null ? '' : ` · ${line}`
}

/** The set logged last in order: the highest one with reps. Null when none is. */
function lastLoggedSet(log: ExerciseLog): number | null {
  const index = log.sets.findLastIndex((reps) => reps !== null)
  return index === -1 ? null : index
}

/**
 * Reps in reserve are judged best close to failure and on later sets, so only the last set counts,
 * and it can only add to a jump, never take one away.
 */
function boosted(log: ExerciseLog): boolean {
  const last = lastLoggedSet(log)
  if (last === null || !canRate(log, last)) return false
  return (rirOf(log, last) ?? 0) >= RIR_BOOST_FROM
}

export function createLog(
  exercise: Exercise,
  slot: Slot,
  progress: Progress,
  unit: Unit,
): ExerciseLog {
  return {
    id: exercise.id,
    name: exercise.name,
    equipment: exercise.equipment,
    weight:
      exercise.equipment === 'bodyweight'
        ? (progress[exercise.id]?.weight ?? 0)
        : (progress[exercise.id] ?? startProgress(exercise, unit)).weight,
    targetReps: slot.reps,
    sets: Array.from({ length: slot.sets }, () => null),
    ...(exercise.timed ? { timed: true } : {}),
  }
}

/** Slots whose exercise no longer exists are left out. */
export function createWorkout(
  routine: Routine,
  find: (id: string) => Exercise | undefined,
  progress: Progress,
  unit: Unit,
  now: number,
  id: string,
): Workout {
  return {
    id,
    routineId: routine.id,
    name: routine.name,
    unit,
    startedAt: now,
    finishedAt: null,
    exercises: routine.slots.flatMap((slot) => {
      const exercise = find(slot.exerciseId)
      return exercise ? [createLog(exercise, slot, progress, unit)] : []
    }),
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

/** Workouts rotate in list order. A workout that was deleted meanwhile leaves the choice alone. */
export function nextRoutineId(routines: Routine[], finishedId: string, current: string): string {
  const index = routines.findIndex((routine) => routine.id === finishedId)
  if (index === -1) return current
  return routines[(index + 1) % routines.length].id
}

/**
 * How heavy an exercise is, as text: "80kg", "BW", or "BW + 10kg" with a dip belt or a vest.
 * Every screen and export that shows a load goes through here, so they all read the same.
 */
export function loadText(bodyweight: boolean, weight: number, unit: Unit): string {
  if (!bodyweight) return formatWeight(weight, unit)
  return weight > 0 ? `BW + ${formatWeight(weight, unit)}` : 'BW'
}

/** A hold as text: "45s" under a minute, "1:30" from a minute on. */
export function holdText(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/** One set or target as text: the reps, or the hold of a timed exercise. */
export function setText(timed: boolean | undefined, value: number): string {
  return timed ? holdText(value) : String(value)
}

/** The longest single hold of a timed exercise in seconds, 0 when none was logged. */
export function longestHold(log: ExerciseLog): number {
  return log.sets.reduce<number>((longest, seconds) => Math.max(longest, seconds ?? 0), 0)
}

export function totalReps(log: ExerciseLog): number {
  return log.sets.reduce<number>((sum, reps) => sum + (reps ?? 0), 0)
}

/**
 * Progressive overload: all reps done -> add weight next time.
 * Missed reps -> repeat the weight. Three misses in a row -> drop 10%.
 * Bodyweight and max-reps exercises have no target weight to progress.
 * Weight added to a bodyweight exercise only changes when the lifter changes it.
 * With `trackRir`, a last set with 3 or more reps in reserve doubles the jump.
 */
export function applyProgression(
  progress: Progress,
  workout: Workout,
  bar: number,
  trackRir = true,
): { progress: Progress; changes: Change[] } {
  const next: Progress = { ...progress }
  const changes: Change[] = []

  for (const log of workout.exercises) {
    const { id, name } = log
    const entry = progress[id]
    if (!entry || log.equipment === 'bodyweight' || log.targetReps === null) continue
    const result = outcome(log)
    if (result === 'skipped') continue

    if (result === 'success') {
      const boost = trackRir && boosted(log)
      const to = log.weight + entry.increment * (boost ? RIR_BOOST_FACTOR : 1)
      next[id] = { ...entry, weight: to, fails: 0 }
      changes.push({ id, name, kind: 'up', from: log.weight, to, ...(boost ? { boosted: true } : {}) })
      continue
    }

    const fails = entry.fails + 1
    if (fails >= DELOAD_AFTER_FAILS) {
      const step = stepFor(log.equipment, workout.unit)
      const floor = Math.max(step, emptyWeight(log.equipment, bar))
      const to = Math.max(floor, roundTo(log.weight * DELOAD_FACTOR, step))
      next[id] = { ...entry, weight: to, fails: 0 }
      changes.push({ id, name, kind: 'deload', from: log.weight, to })
    } else {
      next[id] = { ...entry, weight: log.weight, fails }
      changes.push({ id, name, kind: 'repeat', from: log.weight, to: log.weight })
    }
  }

  return { progress: next, changes }
}
