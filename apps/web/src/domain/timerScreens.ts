import { holdText, type Workout } from './program'
import { runSeed } from './boxingScreens'
import {
  MAX_BOXING_REST_SECONDS,
  MAX_BOXING_ROUND_SECONDS,
  MAX_BOXING_ROUNDS,
  MIN_BOXING_REST_SECONDS,
  MIN_BOXING_ROUND_SECONDS,
} from './boxing'
import {
  MAX_TIMER_ROUNDS,
  TIMER_MODES,
  TIMER_NAMES,
  timerConfig,
  timerSeconds,
  type TimerCalls,
  type TimerConfig,
  type TimerMode,
  type TimerRun,
  type TimerSession,
  type TimerView,
} from './timer'
import { wod } from './wod'

/** What each mode is, in one line under its name. */
export const TIMER_DESCRIPTIONS: Record<TimerMode, string> = {
  tabata: 'Work and rest in rounds',
  emom: 'Every minute on the minute',
  amrap: 'As many rounds as possible',
  forTime: 'As fast as you can',
  boxing: 'Rounds with a coach calling combinations',
}

/**
 * Seconds a time wheel offers from `min` to `max`: 5 s steps up to a minute, 15 s steps up to five,
 * then whole minutes. The current value is always one of them, even off those steps.
 */
export function timeWheel(min: number, max: number, current: number): number[] {
  const values: number[] = []
  for (let value = min; value <= max; value += value < 60 ? 5 : value < 300 ? 15 : 60) {
    values.push(value)
  }
  if (!values.includes(current)) {
    values.push(current)
    values.sort((a, b) => a - b)
  }
  return values
}

/** 1 to 60 rounds, and the current count when it is outside them. */
export function roundsWheel(current: number): number[] {
  const values = Array.from({ length: MAX_TIMER_ROUNDS }, (_, index) => index + 1)
  return values.includes(current) ? values : [...values, current].sort((a, b) => a - b)
}

export type TimerField = 'work' | 'rest' | 'rounds'

/** One wheel of the setup: which number it changes and what it offers. */
export interface TimerWheel {
  field: TimerField
  header: string
  values: number[]
  /** A row of the wheel. */
  format: (value: number) => string
  /** What a screen reader says for a value. */
  spoken: (value: number) => string
}

const seconds = (value: number) => holdText(value)
function spokenTime(value: number): string {
  const minutes = Math.floor(value / 60)
  const rest = value % 60
  const parts = [minutes > 0 ? `${minutes} min` : '', rest > 0 || minutes === 0 ? `${rest} s` : '']
  return parts.filter(Boolean).join(' ')
}
const roundsWheelOf = (config: TimerConfig): TimerWheel => ({
  field: 'rounds',
  header: 'Rounds',
  values: roundsWheel(config.rounds),
  format: String,
  spoken: (value) => `${value} rounds`,
})

/** The numbers a mode needs, each as a wheel, in the order they are set. */
export function timerWheels(config: TimerConfig): TimerWheel[] {
  if (config.mode === 'boxing') {
    const rounds = Array.from({ length: MAX_BOXING_ROUNDS }, (_, index) => index + 1)
    return [
      { ...roundsWheelOf(config), values: rounds },
      {
        field: 'work',
        header: 'Round',
        values: timeWheel(MIN_BOXING_ROUND_SECONDS, MAX_BOXING_ROUND_SECONDS, config.work),
        format: seconds,
        spoken: spokenTime,
      },
      {
        field: 'rest',
        header: 'Rest',
        values: timeWheel(MIN_BOXING_REST_SECONDS, MAX_BOXING_REST_SECONDS, config.rest),
        format: seconds,
        spoken: spokenTime,
      },
    ]
  }
  if (config.mode === 'tabata') {
    return [
      { field: 'work', header: 'Work', values: timeWheel(5, 600, config.work), format: seconds, spoken: spokenTime },
      { field: 'rest', header: 'Rest', values: timeWheel(5, 300, config.rest), format: seconds, spoken: spokenTime },
      roundsWheelOf(config),
    ]
  }
  if (config.mode === 'emom') {
    return [
      { field: 'work', header: 'Every', values: timeWheel(10, 600, config.work), format: seconds, spoken: spokenTime },
      roundsWheelOf(config),
    ]
  }
  if (config.mode === 'amrap') {
    return [
      { field: 'work', header: 'Time', values: timeWheel(30, 3600, config.work), format: seconds, spoken: spokenTime },
    ]
  }
  return [
    {
      field: 'work',
      header: 'Time cap',
      values: [0, ...timeWheel(60, 3600, config.work === 0 ? 60 : config.work)],
      format: (value) => (value === 0 ? 'Off' : holdText(value)),
      spoken: (value) => (value === 0 ? 'No time cap' : spokenTime(value)),
    },
  ]
}

/** "Total 3:50", or "No limit" for a For Time without a cap. */
export function totalText(config: TimerConfig): string {
  const total = timerSeconds(config)
  return total === null ? 'No limit' : `Total ${holdText(total)}`
}

/** The name a new preset starts with: the mode, numbered when that name is taken ("Tabata 2"). */
export function presetName(mode: TimerMode, taken: string[]): string {
  const base = TIMER_NAMES[mode]
  const names = new Set(taken.map((name) => name.trim().toLowerCase()))
  if (!names.has(base.toLowerCase())) return base
  let number = 2
  while (names.has(`${base} ${number}`.toLowerCase())) number += 1
  return `${base} ${number}`
}

export const MINUTE_CALL_LABELS: Record<TimerCalls['minutes'], string> = {
  0: 'Off',
  1: '1 min',
  2: '2 min',
  5: '5 min',
}

/**
 * "Every minute, halfway, last 30 s" for the row that opens the voice options. A boxing round has
 * no minutes or halfway called: its coach calls combinations.
 */
export function callsSummary(calls: TimerCalls, boxing = false): string {
  const parts = [
    !boxing && calls.minutes === 1 ? 'every minute' : !boxing && calls.minutes > 1 ? `every ${calls.minutes} min` : '',
    !boxing && calls.halfway ? 'halfway' : '',
    calls.final ? 'last 30 s' : '',
    calls.phases ? 'rounds and rest' : '',
    calls.beeps ? '3-2-1 beeps' : '',
  ].filter(Boolean)
  if (parts.length === 0) return boxing ? 'Only the coach' : 'Voice off'
  const text = parts.join(', ')
  return text[0].toUpperCase() + text.slice(1)
}

/**
 * What comes after the current phase: "Next: Rest 10s", "Next: Round 4", "Next: Last round",
 * "Last round". The lead-in names the first phase with its length, or "Next: Go" when it has none.
 */
export function nextText(config: TimerConfig, view: TimerView): string {
  if (view.phase === 'done') return ''
  if (view.phase === 'ready') return timerSeconds(config) === null ? 'Next: Go' : `Next: Work ${holdText(config.work)}`
  if (config.mode !== 'tabata' && config.mode !== 'emom') return ''
  if (view.phase === 'work' && view.round === config.rounds) return 'Last round'
  if (view.round + 1 === config.rounds) return 'Next: Last round'
  if (view.phase === 'work' && config.rest > 0) return `Next: Rest ${holdText(config.rest)}`
  return `Next: Round ${view.round + 1}`
}

export type HistoryItem =
  | { kind: 'workout'; workout: Workout; index: number; startedAt: number }
  | { kind: 'timer'; session: TimerSession; startedAt: number }

/**
 * Workouts and timers in one list, newest first. A workout keeps its index in the history,
 * which its records are worked out from.
 */
export function historyItems(history: Workout[], sessions: TimerSession[]): HistoryItem[] {
  return [
    ...history.map((workout, index) => ({ kind: 'workout' as const, workout, index, startedAt: workout.startedAt })),
    ...sessions.map((session) => ({ kind: 'timer' as const, session, startedAt: session.startedAt })),
  ].sort((a, b) => b.startedAt - a.startedAt)
}

function isSplits(value: unknown, taps: number): value is number[] {
  return Array.isArray(value) && value.length === taps && value.every((split) => typeof split === 'number')
}

function isTimerRun(value: unknown): value is TimerRun {
  const run = value as Partial<TimerRun> | null
  return (
    typeof run?.id === 'string' &&
    typeof run.name === 'string' &&
    typeof run.startedAt === 'number' &&
    (run.pausedAt === null || typeof run.pausedAt === 'number') &&
    typeof run.paused === 'number' &&
    typeof run.taps === 'number' &&
    typeof run.calls === 'object' &&
    run.calls !== null &&
    TIMER_MODES.includes(run.config?.mode as TimerMode)
  )
}

/**
 * A run saved before a reload, brought into its limits, or null when it cannot be read.
 * A boxing run keeps its seed, so the coach calls the same combinations; one without gets a new one.
 */
export function restoredRun(saved: unknown, random: () => number = Math.random): TimerRun | null {
  if (!isTimerRun(saved)) return null
  const { wod: workout, splits, restSkipped, seed, ...rest } = saved
  const isWod = workout && Array.isArray(workout.items)
  const config = timerConfig(saved.config)
  const kept = config.mode === 'boxing' && Number.isInteger(seed) ? seed : runSeed(config, random)
  return {
    ...rest,
    config,
    ...(isWod ? { wod: wod(workout) } : {}),
    ...(isWod && isSplits(splits, saved.taps) ? { splits } : {}),
    ...(typeof restSkipped === 'number' ? { restSkipped } : {}),
    ...(kept === undefined ? {} : { seed: kept }),
  }
}
