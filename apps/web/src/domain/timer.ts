import { holdText } from './program'
import { boxingCues, boxingPlan, type BoxingPlan } from './boxing'
import type { Wod } from './wod'

/**
 * Interval timers next to the strength workouts.
 * tabata: `rounds` times `work`, with `rest` between them (none after the last).
 * emom: `rounds` intervals of `work` back to back, no rest.
 * amrap: one block of `work`. The lifter counts rounds by tapping.
 * forTime: counts up to a cap of `work`, 0 for no cap. The lifter stops it when done.
 * boxing: `rounds` rounds of `work` with `rest` between them, and a coach who calls combinations.
 */
export type TimerMode = 'tabata' | 'emom' | 'amrap' | 'forTime' | 'boxing'

export const TIMER_MODES: TimerMode[] = ['tabata', 'emom', 'amrap', 'forTime', 'boxing']

/** The modes a WOD can be built on. Boxing has its own plan instead of a list of movements. */
export const WOD_MODES: TimerMode[] = ['forTime', 'amrap', 'emom', 'tabata']

export const TIMER_NAMES: Record<TimerMode, string> = {
  tabata: 'Tabata',
  emom: 'EMOM',
  amrap: 'AMRAP',
  forTime: 'For Time',
  boxing: 'Boxing',
}

/** Seconds. What each number means depends on the mode, see {@link TimerMode}. */
export interface TimerConfig {
  mode: TimerMode
  work: number
  rest: number
  rounds: number
  /** Boxing only: what each round is about. Left out in the other modes. */
  boxing?: BoxingPlan
}

/** What the voice says while a timer runs. */
export interface TimerCalls {
  /** "Five minutes left" every this many minutes. 0 = never. */
  minutes: 0 | 1 | 2 | 5
  /** "Halfway" at half of the whole timer. */
  halfway: boolean
  /** "Thirty seconds" and "Ten seconds" before the end. */
  final: boolean
  /** "Get ready", "Round three", "Rest", "Last round", "Workout complete". */
  phases: boolean
  /** Three short beeps and a long one into every change of phase. */
  beeps: boolean
}

export const MINUTE_CALLS: TimerCalls['minutes'][] = [0, 1, 2, 5]

export const DEFAULT_TIMER_CALLS: TimerCalls = {
  minutes: 1,
  halfway: true,
  final: true,
  phases: true,
  beeps: true,
}

export const DEFAULT_TIMERS: Record<TimerMode, TimerConfig> = {
  tabata: { mode: 'tabata', work: 20, rest: 10, rounds: 8 },
  emom: { mode: 'emom', work: 60, rest: 0, rounds: 10 },
  amrap: { mode: 'amrap', work: 600, rest: 0, rounds: 1 },
  forTime: { mode: 'forTime', work: 1200, rest: 0, rounds: 1 },
  boxing: {
    mode: 'boxing',
    work: 180,
    rest: 60,
    rounds: 6,
    boxing: {
      style: 'names',
      rounds: [
        { theme: 'jab', every: 4, split: 0, pick: [] },
        { theme: 'two', every: 4, split: 0, pick: [] },
        { theme: 'three', every: 6, split: 0, pick: [] },
        { theme: 'four', every: 6, split: 0, pick: [] },
        { theme: 'mix', every: 6, split: 30, pick: [] },
        { theme: 'freestyle', every: 6, split: 0, pick: [] },
      ],
    },
  },
}

export const TIMER_LEAD_IN_SECONDS = 10
export const MIN_TIMER_SECONDS = 5
export const MAX_TIMER_SECONDS = 3600
export const MAX_TIMER_REST_SECONDS = 600
export const MAX_TIMER_ROUNDS = 60
/** An open For Time stops itself here, and the voice counts minutes up to it. */
export const MAX_OPEN_TIMER_SECONDS = 3600
/** The highest number the voice has a recording for. */
export const MAX_SPOKEN_NUMBER = 60

const SECOND = 1000
const MINUTE = 60 * SECOND
const LEAD_IN = TIMER_LEAD_IN_SECONDS * SECOND
/** A spoken line waits for the long beep at a change of phase to end. */
export const VOICE_AFTER_BEEP = 700
/** Room one spoken line needs before the next one starts. */
const VOICE_GAP = 2500

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, Math.round(value)))
}

/** A config from a form, a preset or a backup, brought into the limits of its mode. */
export function timerConfig(config: TimerConfig): TimerConfig {
  const { mode } = config
  if (mode === 'tabata') {
    return {
      mode,
      work: clamp(config.work, MIN_TIMER_SECONDS, MAX_TIMER_SECONDS),
      rest: clamp(config.rest, MIN_TIMER_SECONDS, MAX_TIMER_REST_SECONDS),
      rounds: clamp(config.rounds, 1, MAX_TIMER_ROUNDS),
    }
  }
  if (mode === 'emom') {
    return {
      mode,
      work: clamp(config.work, MIN_TIMER_SECONDS, MAX_TIMER_SECONDS),
      rest: 0,
      rounds: clamp(config.rounds, 1, MAX_TIMER_ROUNDS),
    }
  }
  if (mode === 'amrap') {
    return { mode, work: clamp(config.work, MIN_TIMER_SECONDS, MAX_TIMER_SECONDS), rest: 0, rounds: 1 }
  }
  if (mode === 'boxing') {
    const rounds = clamp(config.rounds, 1, 15)
    return {
      mode,
      work: clamp(config.work, 30, 600),
      rest: clamp(config.rest, 10, 300),
      rounds,
      boxing: boxingPlan(config.boxing, rounds),
    }
  }
  const cap = config.work <= 0 ? 0 : clamp(config.work, MIN_TIMER_SECONDS, MAX_TIMER_SECONDS)
  return { mode, work: cap, rest: 0, rounds: 1 }
}

/** True for the modes where the lifter counts rounds by tapping. */
export function countsRoundsByTap(mode: TimerMode): boolean {
  return mode === 'amrap' || mode === 'forTime'
}

/** Length of the timer without the lead-in, in seconds. Null for a For Time without a cap. */
export function timerSeconds(config: TimerConfig): number | null {
  if (config.mode === 'tabata' || config.mode === 'boxing') {
    return config.rounds * config.work + (config.rounds - 1) * config.rest
  }
  if (config.mode === 'emom') return config.rounds * config.work
  if (config.mode === 'amrap') return config.work
  return config.work > 0 ? config.work : null
}

/** "8 × 20s / 10s", "10 × 1:00", "10:00", "Cap 20:00", "No cap". */
export function timerSummary(config: TimerConfig): string {
  if (config.mode === 'tabata' || config.mode === 'boxing') {
    return `${config.rounds} × ${holdText(config.work)} / ${holdText(config.rest)}`
  }
  if (config.mode === 'emom') return `${config.rounds} × ${holdText(config.work)}`
  if (config.mode === 'amrap') return holdText(config.work)
  return config.work > 0 ? `Cap ${holdText(config.work)}` : 'No cap'
}

interface Segment {
  kind: 'work' | 'rest'
  /** Milliseconds from the start of the timer, after the lead-in. */
  start: number
  end: number
  /** 1 for the first round. A rest belongs to the round before it. */
  round: number
}

/** The work and rest blocks in order. An open For Time is one block up to the limit. */
function segments(config: TimerConfig): Segment[] {
  if (config.mode === 'amrap' || config.mode === 'forTime') {
    const seconds = timerSeconds(config) ?? MAX_OPEN_TIMER_SECONDS
    return [{ kind: 'work', start: 0, end: seconds * SECOND, round: 1 }]
  }
  const list: Segment[] = []
  let at = 0
  for (let round = 1; round <= config.rounds; round += 1) {
    list.push({ kind: 'work', start: at, end: at + config.work * SECOND, round })
    at += config.work * SECOND
    if (config.rest > 0 && round < config.rounds) {
      list.push({ kind: 'rest', start: at, end: at + config.rest * SECOND, round })
      at += config.rest * SECOND
    }
  }
  return list
}

export type TimerPhase = 'ready' | 'work' | 'rest' | 'done'

/** Where a timer stands. All times in milliseconds. */
export interface TimerView {
  phase: TimerPhase
  /** The round the lifter is in, 1 for the first. In AMRAP and For Time always 1: they count by tapping. */
  round: number
  /** Rounds the timer has, null where the lifter counts them. */
  rounds: number | null
  /** Rounds of tabata or emom whose work is over. 0 in the tapped modes. */
  roundsDone: number
  /** Left in the lead-in, the work or the rest. Null in an open For Time. 0 when done. */
  phaseLeft: number | null
  phaseElapsed: number
  /** Length of the current phase. Null in an open For Time. */
  phaseTotal: number | null
  /** Since the lead-in ended. 0 during the lead-in, the full length when done. */
  elapsed: number
  /** Left of the whole timer. Null in an open For Time. */
  totalLeft: number | null
  /** 0 to 1 through the current phase, for the ring. 0 in an open For Time. */
  progress: number
}

/**
 * Where the timer stands `active` milliseconds after Start was tapped, pauses not counted.
 * The first {@link TIMER_LEAD_IN_SECONDS} of that are the lead-in.
 */
export function timerView(config: TimerConfig, active: number): TimerView {
  const list = segments(config)
  const structured = !countsRoundsByTap(config.mode)
  const rounds = structured ? config.rounds : null
  const seconds = timerSeconds(config)
  const total = (seconds ?? MAX_OPEN_TIMER_SECONDS) * SECOND
  if (active < LEAD_IN) {
    return {
      phase: 'ready',
      round: 1,
      rounds,
      roundsDone: 0,
      phaseLeft: LEAD_IN - Math.max(0, active),
      phaseElapsed: Math.max(0, active),
      phaseTotal: LEAD_IN,
      elapsed: 0,
      totalLeft: seconds === null ? null : total,
      progress: Math.max(0, active) / LEAD_IN,
    }
  }
  const elapsed = active - LEAD_IN
  const segment = list.find((entry) => elapsed < entry.end)
  if (!segment) {
    return {
      phase: 'done',
      round: structured ? config.rounds : 1,
      rounds,
      roundsDone: structured ? config.rounds : 0,
      phaseLeft: 0,
      phaseElapsed: 0,
      phaseTotal: 0,
      elapsed: total,
      totalLeft: 0,
      progress: 1,
    }
  }
  const open = seconds === null
  const length = segment.end - segment.start
  return {
    phase: segment.kind,
    round: segment.round,
    rounds,
    roundsDone: structured ? (segment.kind === 'rest' ? segment.round : segment.round - 1) : 0,
    phaseLeft: open ? null : segment.end - elapsed,
    phaseElapsed: elapsed - segment.start,
    phaseTotal: open ? null : length,
    elapsed,
    totalLeft: open ? null : total - elapsed,
    progress: open ? 0 : (elapsed - segment.start) / length,
  }
}

/**
 * A sound the timer makes. `at` is in milliseconds after Start was tapped, pauses not counted,
 * the same clock as {@link timerView}. `sound` is "beep", "beep-long" or the name of a recording
 * in `assets/voice/timer`, without extension.
 */
export interface TimerCue {
  at: number
  sound: string
}

/**
 * Every sound of a timer from Start to the end, in order.
 * Worked out once up front, so a phone can play them with the screen off and a test can read them.
 *
 * Spoken lines never talk over each other: a time call that would land on a line about the
 * phase waits until that one is over, and of two time calls at the same moment only one is said.
 * In an EMOM a time call on a round start is left out: the round number says the same.
 * A boxing timer has its own coach, see `boxingCues`; `seed` picks its combinations.
 */
export function timerCues(config: TimerConfig, calls: TimerCalls, seed = 1): TimerCue[] {
  if (config.mode === 'boxing') return boxingCues(config, calls, seed)
  const list = segments(config)
  const seconds = timerSeconds(config)
  const total = seconds === null ? null : seconds * SECOND
  const beeps: TimerCue[] = []
  const phases: TimerCue[] = []
  const times: TimerCue[] = []

  const changes = list.map((segment) => segment.start)
  if (total !== null) changes.push(total)
  if (calls.beeps) {
    for (const change of changes) {
      for (const before of [3, 2, 1]) beeps.push({ at: LEAD_IN + change - before * SECOND, sound: 'beep' })
      beeps.push({ at: LEAD_IN + change, sound: 'beep-long' })
    }
  }

  if (calls.phases) {
    phases.push({ at: 0, sound: 'get-ready' })
    for (const segment of list) {
      const at = LEAD_IN + segment.start + VOICE_AFTER_BEEP
      if (segment.kind === 'rest') phases.push({ at, sound: 'rest' })
      else if (countsRoundsByTap(config.mode) || config.rounds === 1) phases.push({ at, sound: 'go' })
      else if (segment.round === config.rounds) phases.push({ at, sound: 'last-round' })
      else if (segment.round <= MAX_SPOKEN_NUMBER) phases.push({ at, sound: `round-${segment.round}` })
    }
    if (total !== null) phases.push({ at: LEAD_IN + total + VOICE_AFTER_BEEP, sound: 'complete' })
  }

  // Most important first: of two calls at the same moment the earlier one in this list is said.
  if (total !== null) {
    if (calls.halfway && total >= MINUTE) times.push({ at: LEAD_IN + total / 2, sound: 'halfway' })
    if (calls.final && total >= 90 * SECOND) {
      times.push({ at: LEAD_IN + total - 30 * SECOND, sound: 'thirty-seconds' })
    }
    if (calls.final && total >= 30 * SECOND) {
      times.push({ at: LEAD_IN + total - 10 * SECOND, sound: 'ten-seconds' })
    }
    if (calls.minutes > 0) {
      for (let left = calls.minutes; left * MINUTE < total && left <= MAX_SPOKEN_NUMBER; left += calls.minutes) {
        times.push({ at: LEAD_IN + total - left * MINUTE, sound: `minutes-left-${left}` })
      }
    }
  } else if (calls.minutes > 0) {
    for (let done = calls.minutes; done <= MAX_SPOKEN_NUMBER; done += calls.minutes) {
      if (done * MINUTE < MAX_OPEN_TIMER_SECONDS * SECOND) {
        times.push({ at: LEAD_IN + done * MINUTE, sound: `minutes-${done}` })
      }
    }
  }

  const spoken: TimerCue[] = []
  for (const call of times) {
    const phase = phases.find((line) => Math.abs(line.at - call.at) < VOICE_GAP)
    if (phase && config.mode === 'emom') continue
    const moved = phase ? { at: phase.at + VOICE_GAP, sound: call.sound } : call
    if (!spoken.some((earlier) => Math.abs(earlier.at - moved.at) < VOICE_GAP)) spoken.push(moved)
  }

  const end = total === null ? Infinity : LEAD_IN + total
  return [...beeps, ...phases, ...spoken.filter((call) => call.at < end)].sort((a, b) => a.at - b.at)
}

/** A timer the lifter saved to start again. */
export interface TimerPreset {
  id: string
  name: string
  config: TimerConfig
}

/** A finished timer in the history. */
export interface TimerSession {
  id: string
  name: string
  config: TimerConfig
  startedAt: number
  finishedAt: number
  /** Seconds the timer ran, without the lead-in and without pauses. */
  seconds: number
  /** Rounds done: finished work blocks in tabata and emom, taps in the other two. */
  rounds: number
  /** False when the lifter stopped it before the end. A For Time the lifter finished is complete. */
  completed: boolean
  /** The WOD this was a run of. Left out for a plain timer. */
  wodId?: string
  /**
   * Seconds into the WOD at each tap, to one decimal, pauses and lead-in not counted.
   * A later run is measured against them while it is still going. Left out when nothing was tapped.
   */
  splits?: number[]
}

/** A timer that is running or paused. Timestamps, so it stays right while the app sleeps. */
export interface TimerRun {
  id: string
  name: string
  config: TimerConfig
  calls: TimerCalls
  startedAt: number
  /** When it was paused. Null while it runs. */
  pausedAt: number | null
  /** Milliseconds spent in pauses that are over. */
  paused: number
  /** Rounds the lifter tapped, in AMRAP and For Time. */
  taps: number
  /** The WOD being done, copied in so the run still reads right when the WOD is edited or deleted. */
  wod?: Wod
  /** Seconds into the WOD at each tap so far, one entry per tap. Only kept for a WOD. */
  splits?: number[]
  /** Picks the combinations of a boxing run, so a reload calls the same ones. */
  seed?: number
  /** The taps the rest was skipped at: the rest that started with that tap is over early. */
  restSkipped?: number
}

/** Seconds into the work of a run, to one decimal: the time a tap at `now` is filed under. */
export function splitTime(run: TimerRun, now: number): number {
  return Math.round(timerView(run.config, activeTime(run, now)).elapsed / 100) / 10
}

/** Milliseconds the run has been going, pauses not counted. */
export function activeTime(run: TimerRun, now: number): number {
  return Math.max(0, (run.pausedAt ?? now) - run.startedAt - run.paused)
}

/** True once the timer has run out by itself. An open For Time runs out at the limit. */
export function isTimerOver(run: TimerRun, now: number): boolean {
  return timerView(run.config, activeTime(run, now)).phase === 'done'
}

/** The id of the catalog exercise that stands for a pause inside a WOD. */
export const REST_EXERCISE = 'rest'

/**
 * Items of one round that are ticked off with a tap: all but the rests, which the clock counts.
 * At least 1, so a WOD of nothing but rests still has an end.
 */
export function tapsPerRound(workout: Wod): number {
  return Math.max(1, workout.items.filter((item) => item.exerciseId !== REST_EXERCISE).length)
}

/**
 * Taps a For Time WOD takes from start to finish: one per item of every round, ticked off in
 * order. Null where the clock ends the run or the lifter counts freely.
 */
export function wodSteps(workout: Wod): number | null {
  if (workout.timer.mode !== 'forTime') return null
  return workout.rounds * tapsPerRound(workout)
}

/**
 * The history entry of a run that ends at `now`. `finished` is true when the timer ran out or the
 * lifter finished a For Time, false when it was stopped early. Null when it ends in the lead-in:
 * nothing was done yet. A For Time WOD is only complete with every step tapped off: a clock stopped
 * after one of three rounds is not a time to beat.
 */
export function timerSession(run: TimerRun, now: number, finished: boolean): TimerSession | null {
  const view = timerView(run.config, activeTime(run, now))
  if (view.phase === 'ready') return null
  const steps = run.wod ? wodSteps(run.wod) : null
  const allDone = steps === null || run.taps >= steps
  // In a For Time WOD the taps count items: a round is done with its last item.
  const perRound = steps === null || !run.wod ? 0 : tapsPerRound(run.wod)
  const tapped = countsRoundsByTap(run.config.mode) ? run.taps : view.roundsDone
  const splits = run.wod && run.splits && run.splits.length > 0 ? run.splits : null
  return {
    id: run.id,
    name: run.name,
    config: run.config,
    startedAt: run.startedAt,
    finishedAt: now,
    seconds: Math.round(view.elapsed / SECOND),
    rounds: perRound > 0 ? Math.floor(Math.min(run.taps, steps ?? 0) / perRound) : tapped,
    completed: finished && allDone,
    ...(run.wod ? { wodId: run.wod.id } : {}),
    ...(splits ? { splits: [...splits] } : {}),
  }
}

export type TimerOutcome = 'completed' | 'capped' | 'stopped'

export const OUTCOME_LABELS: Record<TimerOutcome, string> = {
  completed: 'Completed',
  capped: 'Time cap',
  stopped: 'Stopped early',
}

/**
 * How a timer ended. A For Time that ran into its cap is its own case:
 * the clock ran out, but the work was not finished.
 */
export function timerOutcome(session: TimerSession): TimerOutcome {
  const { mode, work } = session.config
  if (mode === 'forTime' && work > 0 && session.seconds >= work) return 'capped'
  return session.completed ? 'completed' : 'stopped'
}

/** "8 rounds · 4:00" for the history and the result. */
export function sessionSummary(session: TimerSession): string {
  const time = holdText(session.seconds)
  if (session.rounds === 0 && countsRoundsByTap(session.config.mode)) return time
  return `${session.rounds} ${session.rounds === 1 ? 'round' : 'rounds'} · ${time}`
}
