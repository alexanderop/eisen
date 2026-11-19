import { reactive, ref, watch } from 'vue'
import {
  applyProgression,
  createWorkout,
  EXERCISE_IDS,
  initialProgress,
  nextReps,
  otherType,
  outcome,
  UNIT_DEFAULTS,
  type Change,
  type ExerciseId,
  type Progress,
  type Workout,
  type WorkoutType,
} from './domain/program'
import { convert, roundTo, type Unit } from './domain/units'

const STORAGE_KEY = 'eisen:v1'

export interface Settings {
  unit: Unit
  bar: number
  /** Rest in seconds after a set with all reps. */
  restDone: number
  /** Rest in seconds after a set with missed reps. */
  restMissed: number
  sound: boolean
}

export interface AppState {
  version: 1
  settings: Settings
  progress: Progress
  nextType: WorkoutType
  active: Workout | null
  /** Finished workouts, newest first. */
  history: Workout[]
}

function freshState(unit: Unit = 'kg'): AppState {
  return {
    version: 1,
    settings: { unit, bar: UNIT_DEFAULTS[unit].bar, restDone: 90, restMissed: 300, sound: true },
    progress: initialProgress(unit),
    nextType: 'A',
    active: null,
    history: [],
  }
}

function isAppState(value: unknown): value is AppState {
  const candidate = value as Partial<AppState> | null
  return (
    typeof candidate === 'object' &&
    candidate !== null &&
    candidate.version === 1 &&
    Array.isArray(candidate.history) &&
    typeof candidate.settings === 'object' &&
    typeof candidate.progress === 'object'
  )
}

function load(): AppState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed: unknown = JSON.parse(raw)
      if (isAppState(parsed)) return parsed
    }
  } catch {
    // Unreadable storage: start fresh instead of blocking the app.
  }
  return freshState()
}

export const state = reactive<AppState>(load())

watch(
  state,
  (current) => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(current))
    } catch {
      // Storage full or blocked: the session keeps working in memory.
    }
  },
  { deep: true },
)

/** What the last finished workout changed. Shown once on the home screen. */
export const lastChanges = ref<Change[] | null>(null)

export function startWorkout(): void {
  if (state.active) return
  lastChanges.value = null
  state.active = createWorkout(
    state.nextType,
    state.progress,
    state.settings.unit,
    Date.now(),
    crypto.randomUUID(),
  )
}

export function tapSet(exerciseIndex: number, setIndex: number): number | null {
  const log = state.active?.exercises[exerciseIndex]
  if (!log) return null
  const reps = nextReps(log.sets[setIndex], log.targetReps)
  log.sets[setIndex] = reps
  return reps
}

export function setActiveWeight(exerciseIndex: number, weight: number): void {
  const log = state.active?.exercises[exerciseIndex]
  if (!log) return
  log.weight = weight
  state.progress[log.id].weight = weight
}

export function setProgressWeight(id: ExerciseId, weight: number): void {
  state.progress[id] = { weight, fails: 0 }
  const log = state.active?.exercises.find((entry) => entry.id === id)
  if (log) log.weight = weight
}

export function discardWorkout(): void {
  state.active = null
}

export function finishWorkout(): void {
  const workout = state.active
  if (!workout) return
  if (workout.exercises.every((log) => outcome(log) === 'skipped')) {
    state.active = null
    return
  }
  const result = applyProgression(state.progress, workout, state.settings.bar)
  state.progress = result.progress
  state.history.unshift({ ...workout, finishedAt: Date.now() })
  state.nextType = otherType(workout.type)
  state.active = null
  lastChanges.value = result.changes
}

export function deleteWorkout(id: string): void {
  state.history = state.history.filter((workout) => workout.id !== id)
}

/** Converts the working weights. Blocked during a workout so its log stays in one unit. */
export function setUnit(unit: Unit): void {
  if (state.active || unit === state.settings.unit) return
  const from = state.settings.unit
  const defaults = UNIT_DEFAULTS[unit]
  for (const id of EXERCISE_IDS) {
    const converted = roundTo(convert(state.progress[id].weight, from, unit), defaults.step)
    state.progress[id].weight = Math.max(defaults.step, converted)
  }
  state.settings.unit = unit
  state.settings.bar = defaults.bar
}

export function exportData(): string {
  return JSON.stringify(state, null, 2)
}

export function importData(json: string): boolean {
  try {
    const parsed: unknown = JSON.parse(json)
    if (!isAppState(parsed)) return false
    Object.assign(state, parsed)
    lastChanges.value = null
    return true
  } catch {
    return false
  }
}

export function resetAll(): void {
  Object.assign(state, freshState(state.settings.unit))
  lastChanges.value = null
}
