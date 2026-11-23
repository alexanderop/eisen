import { catalogExercise, type Exercise } from './exercises'
import {
  defaultRoutines,
  startProgress,
  UNIT_DEFAULTS,
  type Progress,
  type Routine,
  type Workout,
} from './program'
import {
  DEFAULT_TIMER_CALLS,
  DEFAULT_TIMERS,
  type TimerCalls,
  type TimerConfig,
  type TimerMode,
  type TimerPreset,
  type TimerSession,
} from './timer'
import type { Unit } from './units'

export interface Settings {
  unit: Unit
  bar: number
  /** Rest in seconds after a set with all reps. */
  restDone: number
  /** Rest in seconds after a set with missed reps. */
  restMissed: number
  sound: boolean
  /** Short vibration when a set, a stepper or a finished workout is tapped. */
  haptics: boolean
  /**
   * The rest opens as a full screen with the wheels. Off: only the small bar, which still opens
   * it on a tap. Missing means on: states saved before the setting existed have no entry.
   */
  restFullScreen?: boolean
  /** What the voice says during an interval timer. One setting for every timer. */
  timerCalls: TimerCalls
}

export interface AppState {
  version: 7
  settings: Settings
  /**
   * Working weight per exercise id. A bodyweight exercise has an entry only while weight is
   * added to it. Older builds skip those entries, so they still read this state.
   */
  progress: Progress
  routines: Routine[]
  /** The workout the home screen offers next. */
  nextRoutineId: string
  customExercises: Exercise[]
  active: Workout | null
  /** Finished workouts, newest first. */
  history: Workout[]
  /** Saved interval timers, in the order they are shown. */
  timerPresets: TimerPreset[]
  /** Finished interval timers, newest first. */
  timerSessions: TimerSession[]
  /** The last config started per mode, so each mode opens the way the lifter left it. */
  timerConfigs: Record<TimerMode, TimerConfig>
}

const ORIGINAL_IDS = ['hackSquat', 'bench', 'row', 'ohp', 'deadlift'] as const

function catalogProgress(ids: readonly string[], unit: Unit): Progress {
  const progress: Progress = {}
  for (const id of ids) {
    const exercise = catalogExercise(id)
    if (exercise && exercise.equipment !== 'bodyweight') progress[id] = startProgress(exercise, unit)
  }
  return progress
}

export function freshState(unit: Unit = 'kg'): AppState {
  const routines = defaultRoutines()
  return {
    version: 7,
    settings: {
      unit,
      bar: UNIT_DEFAULTS[unit].bar,
      restDone: 90,
      restMissed: 300,
      sound: true,
      haptics: true,
      timerCalls: { ...DEFAULT_TIMER_CALLS },
    },
    progress: catalogProgress(
      routines.flatMap((routine) => routine.slots.map((slot) => slot.exerciseId)),
      unit,
    ),
    routines,
    nextRoutineId: routines[0].id,
    customExercises: [],
    active: null,
    history: [],
    timerPresets: [],
    timerSessions: [],
    timerConfigs: structuredClone(DEFAULT_TIMERS),
  }
}

type Json = Record<string, unknown>

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null
}

/** Starting weights that version 2 shipped with, to tell an untouched install from an edited one. */
const V2_START: Record<Unit, Record<string, number>> = {
  kg: { hackSquat: 20, bench: 20, row: 30, ohp: 20, deadlift: 40 },
  lb: { hackSquat: 50, bench: 45, row: 65, ohp: 45, deadlift: 95 },
}

/** Version 4 workouts carry their own name and copy each exercise's name and equipment. */
function upgradeWorkout(workout: unknown): unknown {
  if (!isObject(workout)) return workout
  const { type, exercises, ...rest } = workout
  return {
    ...rest,
    routineId: type,
    name: `Workout ${String(type)}`,
    exercises: (Array.isArray(exercises) ? exercises : []).filter(isObject).map((log) => {
      const exercise = catalogExercise(String(log.id))
      return {
        ...log,
        name: exercise?.name ?? String(log.id),
        equipment: exercise?.equipment ?? 'barbell',
      }
    }),
  }
}

/** Brings a stored state or backup from any older version up to the current one. */
export function migrate(value: unknown): unknown {
  if (!isObject(value)) return value
  let data = value

  // Version 1 had the barbell squat where the hack squat is now. Its logs carry over.
  if (data.version === 1) {
    data = JSON.parse(
      JSON.stringify({ ...data, version: 2 })
        .replaceAll('"id":"squat"', '"id":"hackSquat"')
        .replaceAll('"squat":{', '"hackSquat":{'),
    ) as Json
  }

  const unit: Unit = isObject(data.settings) && data.settings.unit === 'lb' ? 'lb' : 'kg'

  // Version 3 changed the starting weights. An install still on the old ones gets the new ones.
  if (data.version === 2) {
    const progress = isObject(data.progress) ? data.progress : {}
    const untouched =
      Array.isArray(data.history) &&
      data.history.length === 0 &&
      data.active === null &&
      ORIGINAL_IDS.every((id) => {
        const entry = progress[id]
        return isObject(entry) && entry.weight === V2_START[unit][id]
      })
    data = {
      ...data,
      version: 3,
      ...(untouched ? { progress: catalogProgress(ORIGINAL_IDS, unit) } : {}),
    }
  }

  // Version 4 made workouts editable: the fixed A/B program became two ordinary workouts.
  if (data.version === 3) {
    const defaults = catalogProgress(ORIGINAL_IDS, unit)
    const stored = isObject(data.progress) ? data.progress : {}
    const progress: Progress = {}
    for (const id of ORIGINAL_IDS) {
      const entry = stored[id]
      progress[id] = {
        ...defaults[id],
        ...(isObject(entry) && typeof entry.weight === 'number' ? { weight: entry.weight } : {}),
        ...(isObject(entry) && typeof entry.fails === 'number' ? { fails: entry.fails } : {}),
      }
    }
    const { nextType, ...rest } = data
    data = {
      ...rest,
      version: 4,
      progress,
      routines: defaultRoutines(),
      nextRoutineId: nextType === 'B' ? 'B' : 'A',
      customExercises: [],
      active: data.active === null ? null : upgradeWorkout(data.active),
      history: (Array.isArray(data.history) ? data.history : []).map(upgradeWorkout),
    }
  }

  // Version 5 made the deadlift 5×5 too. A workout still on the old single set gets all five.
  if (data.version === 4) {
    data = {
      ...data,
      version: 5,
      routines: Array.isArray(data.routines) ? data.routines.map(fiveDeadliftSets) : data.routines,
    }
  }

  // Version 6 added vibration on taps. It starts switched on.
  if (data.version === 5) {
    data = {
      ...data,
      version: 6,
      settings: isObject(data.settings) ? { ...data.settings, haptics: true } : data.settings,
    }
  }

  // Version 7 added the interval timers, their saved presets and their history.
  if (data.version === 6) {
    data = {
      ...data,
      version: 7,
      settings: isObject(data.settings)
        ? { ...data.settings, timerCalls: { ...DEFAULT_TIMER_CALLS } }
        : data.settings,
      timerPresets: [],
      timerSessions: [],
      timerConfigs: structuredClone(DEFAULT_TIMERS),
    }
  }

  return data
}

function fiveDeadliftSets(routine: unknown): unknown {
  if (!isObject(routine) || !Array.isArray(routine.slots)) return routine
  return {
    ...routine,
    slots: routine.slots.map((slot: unknown) =>
      isObject(slot) && slot.exerciseId === 'deadlift' && slot.sets === 1 && slot.reps === 5
        ? { ...slot, sets: 5 }
        : slot,
    ),
  }
}

export function isAppState(value: unknown): value is AppState {
  return (
    isObject(value) &&
    value.version === 7 &&
    Array.isArray(value.history) &&
    Array.isArray(value.timerPresets) &&
    Array.isArray(value.timerSessions) &&
    isObject(value.timerConfigs) &&
    Array.isArray(value.routines) &&
    value.routines.length > 0 &&
    Array.isArray(value.customExercises) &&
    isObject(value.settings) &&
    isObject(value.settings.timerCalls) &&
    isObject(value.progress)
  )
}
