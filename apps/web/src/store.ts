import { computed, reactive, ref, toRaw, watch } from 'vue'
import {
  CATALOG,
  catalogExercise,
  customExercise,
  type Equipment,
  type Exercise,
} from './domain/exercises'
import {
  applyProgression,
  convertProgress,
  createLog,
  createWorkout,
  defaultSlot,
  nextReps,
  nextRoutineId,
  outcome,
  startProgress,
  UNIT_DEFAULTS,
  withAddedWeight,
  type Change,
  type ExerciseLog,
  type ExerciseProgress,
  type Routine,
} from './domain/program'
import { lastSetReps } from './domain/records'
import { withCorrectedReps } from './domain/rest'
import { freshState, isAppState, migrate, type AppState } from './domain/state'
import { timerConfig, type TimerConfig, type TimerPreset, type TimerSession } from './domain/timer'
import type { Unit } from './domain/units'

const STORAGE_KEY = 'eisen:v1'

function load(): AppState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = migrate(JSON.parse(raw))
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

/** Warmup sets ticked off in the running workout, as `exerciseId:setIndex`. Not saved. */
export const warmupDone = ref(new Set<string>())

/** What the last finished workout changed. Shown once on the home screen. */
export const lastChanges = ref<Change[] | null>(null)

export function newId(): string {
  return crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

/** The built-in exercises followed by the lifter's own. */
export const exercises = computed<Exercise[]>(() => [...CATALOG, ...state.customExercises])

export function findExercise(id: string): Exercise | undefined {
  return catalogExercise(id) ?? state.customExercises.find((exercise) => exercise.id === id)
}

const NO_PROGRESS: ExerciseProgress = { weight: 0, fails: 0, increment: 0 }

/**
 * The working weight of an exercise, or its starting weight when it was never used.
 * For a bodyweight exercise, the weight added on top of the body (0 = none).
 */
export function progressOf(id: string): ExerciseProgress {
  const stored = state.progress[id]
  if (stored) return stored
  const exercise = findExercise(id)
  if (!exercise || exercise.equipment === 'bodyweight') return NO_PROGRESS
  return startProgress(exercise, state.settings.unit)
}

/** Bodyweight exercises get no entry here: one is made only when the lifter adds weight. */
function ensureProgress(id: string): ExerciseProgress | null {
  const exercise = findExercise(id)
  if (!exercise || exercise.equipment === 'bodyweight') return null
  state.progress[id] ??= startProgress(exercise, state.settings.unit)
  return state.progress[id]
}

export const nextRoutine = computed<Routine>(
  () => state.routines.find((routine) => routine.id === state.nextRoutineId) ?? state.routines[0],
)

export function selectRoutine(id: string): void {
  if (!state.active) state.nextRoutineId = id
}

export function startWorkout(): boolean {
  if (state.active) return true
  const routine = nextRoutine.value
  for (const slot of routine.slots) ensureProgress(slot.exerciseId)
  const workout = createWorkout(
    routine,
    findExercise,
    state.progress,
    state.settings.unit,
    Date.now(),
    newId(),
  )
  if (workout.exercises.length === 0) return false
  lastChanges.value = null
  warmupDone.value = new Set()
  state.active = workout
  return true
}

/** Adds an exercise to the running workout only. The workout it was started from stays as it is. */
export function addActiveExercise(id: string): void {
  const workout = state.active
  const exercise = findExercise(id)
  if (!workout || !exercise || workout.exercises.some((log) => log.id === id)) return
  ensureProgress(id)
  workout.exercises.push(
    createLog(exercise, defaultSlot(exercise), state.progress, state.settings.unit),
  )
}

/** The last exercise stays: a workout without exercises has nothing to finish. */
export function removeActiveExercise(exerciseIndex: number): void {
  const workout = state.active
  if (workout && workout.exercises.length > 1) workout.exercises.splice(exerciseIndex, 1)
}

/** Moves an exercise behind the others, for when its machine or rack is taken. */
export function deferActiveExercise(exerciseIndex: number): void {
  const workout = state.active
  if (!workout || exerciseIndex < 0 || exerciseIndex >= workout.exercises.length) return
  workout.exercises.push(...workout.exercises.splice(exerciseIndex, 1))
}

export function tapSet(exerciseIndex: number, setIndex: number): number | null {
  const log = state.active?.exercises[exerciseIndex]
  if (!log || log.targetReps === null) return null
  const reps = nextReps(log.sets[setIndex], log.targetReps)
  log.sets[setIndex] = reps
  return reps
}

/** The weight added to a bodyweight exercise. It is kept for the next workouts, never below none. */
function setAddedWeight(id: string, weight: number): number {
  const added = Math.max(0, weight)
  state.progress = withAddedWeight(state.progress, id, added)
  return added
}

export function setActiveWeight(exerciseIndex: number, weight: number): void {
  const log = state.active?.exercises[exerciseIndex]
  if (log?.equipment === 'bodyweight') {
    log.weight = setAddedWeight(log.id, weight)
    return
  }
  const entry = log && ensureProgress(log.id)
  if (!log || !entry) return
  log.weight = weight
  entry.weight = weight
}

/** Logs a max-reps set, where the lifter enters the count instead of tapping it down, or clears a set. */
export function logReps(exerciseIndex: number, setIndex: number, reps: number | null): void {
  const log = state.active?.exercises[exerciseIndex]
  if (log) log.sets[setIndex] = reps
}

/**
 * Corrects the count of a logged set from the rest screen. Unlike a tap it leaves the rest alone
 * and never clears the set.
 */
export function correctReps(exerciseIndex: number, setIndex: number, reps: number): void {
  const log = state.active?.exercises[exerciseIndex]
  if (log) log.sets = withCorrectedReps(log.sets, setIndex, reps)
}

/** What this set of the exercise got the last time it was logged. Null when it never was. */
export function lastReps(id: string, setIndex: number): number | null {
  return lastSetReps(state.history, id, setIndex)
}

export function setProgressWeight(id: string, weight: number): void {
  if (findExercise(id)?.equipment === 'bodyweight') {
    weight = setAddedWeight(id, weight)
  } else {
    const entry = ensureProgress(id)
    if (!entry) return
    entry.weight = weight
    entry.fails = 0
  }
  for (const log of state.active?.exercises ?? []) {
    if (log.id === id) log.weight = weight
  }
}

export function setIncrement(id: string, increment: number): void {
  const entry = ensureProgress(id)
  if (entry) entry.increment = increment
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
  // Spread from the raw object: a spread of the reactive one would carry reactive exercises
  // into the history, and the history edit sheet cannot copy those until the next reload.
  state.history.unshift({ ...toRaw(workout), finishedAt: Date.now() })
  state.nextRoutineId = nextRoutineId(state.routines, workout.routineId, nextRoutine.value.id)
  state.active = null
  lastChanges.value = result.changes
}

/** Corrects what a finished workout logged. Working weights stay as they are. */
export function updateWorkout(id: string, exercises: ExerciseLog[]): void {
  const workout = state.history.find((entry) => entry.id === id)
  if (workout) workout.exercises = exercises
}

export function deleteWorkout(id: string): void {
  state.history = state.history.filter((workout) => workout.id !== id)
}

/** Starting a timer makes its config the one its mode opens with next time. */
export function rememberTimerConfig(config: TimerConfig): void {
  state.timerConfigs[config.mode] = timerConfig(config)
}

export function addTimerSession(session: TimerSession): void {
  if (state.timerSessions.some((entry) => entry.id === session.id)) return
  state.timerSessions.unshift(session)
}

/** The rounds of a finished timer, corrected after the fact. */
export function setTimerSessionRounds(id: string, rounds: number): void {
  const session = state.timerSessions.find((entry) => entry.id === id)
  if (session) session.rounds = Math.max(0, Math.round(rounds))
}

export function deleteTimerSession(id: string): void {
  state.timerSessions = state.timerSessions.filter((session) => session.id !== id)
}

export function addTimerPreset(name: string, config: TimerConfig): TimerPreset {
  const preset = { id: newId(), name: name.trim().slice(0, 40) || 'Timer', config: timerConfig(config) }
  state.timerPresets.push(preset)
  return preset
}

export function updateTimerPreset(id: string, name: string, config: TimerConfig): void {
  const preset = state.timerPresets.find((entry) => entry.id === id)
  if (!preset) return
  preset.name = name.trim().slice(0, 40) || preset.name
  preset.config = timerConfig(config)
}

export function deleteTimerPreset(id: string): void {
  state.timerPresets = state.timerPresets.filter((preset) => preset.id !== id)
}

export function addRoutine(): Routine {
  const taken = new Set(state.routines.map((routine) => routine.name))
  let number = state.routines.length + 1
  while (taken.has(`Workout ${number}`)) number++
  state.routines.push({ id: newId(), name: `Workout ${number}`, slots: [] })
  return state.routines[state.routines.length - 1]
}

/** The last workout stays: the home screen always needs one to offer. */
export function removeRoutine(id: string): void {
  if (state.routines.length <= 1) return
  state.routines = state.routines.filter((routine) => routine.id !== id)
  if (state.nextRoutineId === id) state.nextRoutineId = state.routines[0].id
}

export function addSlot(routine: Routine, exerciseId: string): void {
  const exercise = findExercise(exerciseId)
  if (!exercise || routine.slots.some((slot) => slot.exerciseId === exerciseId)) return
  ensureProgress(exerciseId)
  routine.slots.push(defaultSlot(exercise))
}

export function moveSlot(routine: Routine, index: number, direction: 1 | -1): void {
  const target = index + direction
  if (target < 0 || target >= routine.slots.length) return
  const [slot] = routine.slots.splice(index, 1)
  routine.slots.splice(target, 0, slot)
}

export function removeSlot(routine: Routine, index: number): void {
  routine.slots.splice(index, 1)
}

export function addCustomExercise(name: string, equipment: Equipment, timed = false): Exercise | null {
  const trimmed = name.trim().slice(0, 40)
  if (!trimmed) return null
  const lower = trimmed.toLowerCase()
  const existing = exercises.value.find((exercise) => exercise.name.toLowerCase() === lower)
  if (existing) return existing
  const exercise = customExercise(`custom-${newId()}`, trimmed, equipment, timed)
  state.customExercises.push(exercise)
  return exercise
}

/** Removes the exercise from every workout. Finished workouts keep their logs of it. */
export function deleteCustomExercise(id: string): void {
  state.customExercises = state.customExercises.filter((exercise) => exercise.id !== id)
  for (const routine of state.routines) {
    routine.slots = routine.slots.filter((slot) => slot.exerciseId !== id)
  }
  delete state.progress[id]
}

/** Converts the working weights. Blocked during a workout so its log stays in one unit. */
export function setUnit(unit: Unit): void {
  if (state.active || unit === state.settings.unit) return
  const from = state.settings.unit
  for (const [id, entry] of Object.entries(state.progress)) {
    const equipment = findExercise(id)?.equipment ?? 'barbell'
    state.progress[id] = convertProgress(entry, equipment, from, unit)
  }
  state.settings.unit = unit
  state.settings.bar = UNIT_DEFAULTS[unit].bar
}

export function exportData(): string {
  return JSON.stringify(state, null, 2)
}

export function importData(json: string): boolean {
  try {
    const parsed = migrate(JSON.parse(json))
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
