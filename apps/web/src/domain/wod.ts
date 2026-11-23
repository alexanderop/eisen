import type { Exercise } from './exercises'
import { holdText } from './program'
import {
  REST_EXERCISE,
  tapsPerRound,
  timerConfig,
  timerOutcome,
  wodSteps,
  type TimerConfig,
  type TimerSession,
} from './timer'
import { convert, formatWeight, roundTo, type Unit } from './units'

/** What one line of a WOD counts: reps, seconds held or metres covered. */
export type WodUnit = 'reps' | 'seconds' | 'meters'

export const WOD_UNITS: WodUnit[] = ['reps', 'seconds', 'meters']

export interface WodItem {
  exerciseId: string
  /** How much of it, in `unit`. 0 = as many as possible. */
  amount: number
  unit: WodUnit
  /** Weight in kg, whatever unit the app shows. 0 = none. */
  load: number
}

/**
 * A workout of the day: a timer and what to do while it runs.
 * forTime: `rounds` rounds of the items, as fast as possible. With a `scheme` like 21-15-9 every
 * round has its own count for the items counted in reps, and there are as many rounds as numbers.
 * amrap: the items over and over until the time is up. The lifter counts rounds by tapping.
 * emom: all items at the start of every interval.
 * tabata: one item per round, taking turns.
 */
export interface Wod {
  id: string
  /** Empty while the lifter has not named it: then it goes by its format, see {@link wodName}. */
  name: string
  timer: TimerConfig
  /** For Time only. 1 in the other modes. */
  rounds: number
  /** For Time only: reps per round. Empty = every round as the items say. */
  scheme: number[]
  items: WodItem[]
}

export const MAX_WOD_NAME = 40
export const MAX_WOD_ITEMS = 12
export const MAX_WOD_AMOUNT = 10000
export const MAX_WOD_LOAD = 500
export const MAX_WOD_ROUNDS = 60
export const MAX_SCHEME_LENGTH = 12
export const MAX_SCHEME_REPS = 999

/** Schemes the builder offers with one tap. */
export const SCHEMES: number[][] = [
  [21, 15, 9],
  [10, 9, 8, 7, 6, 5, 4, 3, 2, 1],
  [50, 40, 30, 20, 10],
  [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
]

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, Math.round(value)))
}

/** A WOD from the builder, a backup or the library, brought into its limits. */
export function wod(draft: Wod): Wod {
  const timer = timerConfig(draft.timer)
  const forTime = timer.mode === 'forTime'
  const scheme = forTime
    ? draft.scheme.slice(0, MAX_SCHEME_LENGTH).map((reps) => clamp(reps, 1, MAX_SCHEME_REPS))
    : []
  return {
    id: draft.id,
    name: draft.name.trim().slice(0, MAX_WOD_NAME),
    timer,
    rounds: !forTime ? 1 : scheme.length > 0 ? scheme.length : clamp(draft.rounds, 1, MAX_WOD_ROUNDS),
    scheme,
    items: draft.items.slice(0, MAX_WOD_ITEMS).map((item) => ({
      exerciseId: item.exerciseId,
      amount: clamp(item.amount, 0, MAX_WOD_AMOUNT),
      unit: WOD_UNITS.includes(item.unit) ? item.unit : 'reps',
      load: roundTo(Math.min(MAX_WOD_LOAD, Math.max(0, item.load)), 0.5),
    })),
  }
}

/** Rounds the WOD has. Null in an AMRAP: there the lifter counts them. */
export function wodRounds(workout: Wod): number | null {
  if (workout.timer.mode === 'amrap') return null
  return workout.timer.mode === 'forTime' ? workout.rounds : workout.timer.rounds
}

/**
 * The round to show while it runs, 1 for the first. For Time and AMRAP go by the lifter's taps,
 * the other two by the clock (`clockRound`, from the timer's view).
 */
export function wodRound(workout: Wod, taps: number, clockRound: number): number {
  if (workout.timer.mode === 'amrap') return taps + 1
  if (workout.timer.mode === 'forTime') {
    return Math.min(Math.floor(taps / tapsPerRound(workout)) + 1, workout.rounds)
  }
  return clockRound
}

/** True once the last step of a For Time is tapped off: that tap stops the clock. */
export function wodDone(workout: Wod, taps: number): boolean {
  const steps = wodSteps(workout)
  return steps !== null && taps >= steps
}

/**
 * In a For Time the items of a round are ticked off one by one: the index in `items` of the one
 * to do now. Rests are skipped: they take no tap. The last one stays current once everything is done.
 */
export function wodStep(workout: Wod, taps: number): number {
  const tappable = workout.items.flatMap((item, index) => (isRest(item) ? [] : [index]))
  if (tappable.length === 0) return 0
  return tappable[wodDone(workout, taps) ? tappable.length - 1 : taps % tappable.length]
}

/** True for a pause between the work: the clock counts it down, it takes no tap. */
export function isRest(item: WodItem): boolean {
  return item.exerciseId === REST_EXERCISE
}

/**
 * The pause a For Time is in, if any: it starts by itself with the tap on the item before it and
 * runs for the seconds of the rest items that follow that item. `left` and `total` in seconds.
 * A rest after the very last item is none: the WOD is over with that tap.
 * `splits` are the taps so far, `elapsed` the seconds into the WOD now.
 */
export function wodRest(workout: Wod, splits: number[], elapsed: number): { left: number; total: number } | null {
  const taps = splits.length
  if (workout.timer.mode !== 'forTime' || taps === 0 || wodDone(workout, taps)) return null
  const tappable = workout.items.flatMap((item, index) => (isRest(item) ? [] : [index]))
  if (tappable.length === 0) return null
  // The item just ticked off, and the rests between it and the next one to tick.
  const last = tappable[(taps - 1) % tappable.length]
  let total = 0
  for (let index = last + 1; index < workout.items.length && isRest(workout.items[index]); index += 1) {
    total += workout.items[index].unit === 'seconds' ? workout.items[index].amount : 0
  }
  const left = total - (elapsed - splits[taps - 1])
  return total > 0 && left > 0 ? { left: Math.ceil(left), total } : null
}

/**
 * How a For Time run stands against the best one, in seconds: negative is ahead, positive behind.
 * It is the gap at the last tap, and it grows live once the current item takes longer than it did
 * in the best run. Null when there is no best with splits to measure against.
 * `splits` are this run's taps so far, `elapsed` the seconds into the WOD now.
 */
export function liveGap(best: TimerSession | null, splits: number[], elapsed: number): number | null {
  const pace = best?.splits
  if (!pace || pace.length === 0) return null
  const taps = splits.length
  if (taps > pace.length) return null
  // Whole seconds the way the clock and the "Best" next to it show them, so the three never disagree.
  const atLastTap = taps === 0 ? -Infinity : Math.round(splits[taps - 1]) - Math.round(pace[taps - 1])
  const onThisItem = taps < pace.length ? Math.floor(elapsed) - Math.round(pace[taps]) : -Infinity
  const gap = Math.max(atLastTap, onThisItem)
  // Before the first tap there is only the live part: until the best run's first split passes, it is level.
  return taps === 0 ? Math.max(0, gap) : gap
}

/**
 * How an AMRAP stands against the best one, in rounds: positive is ahead.
 * Null when the best run has no splits.
 */
export function roundsGap(best: TimerSession | null, taps: number, elapsed: number): number | null {
  const pace = best?.splits
  if (!pace || pace.length === 0) return null
  return taps - pace.filter((split) => split <= elapsed).length
}

/** "12s ahead", "1:05 behind", "On pace". */
export function gapText(seconds: number): string {
  if (seconds === 0) return 'On pace'
  return seconds < 0 ? `${holdText(-seconds)} ahead` : `${holdText(seconds)} behind`
}

/** "2 rounds ahead", "1 round behind", "On pace". */
export function roundsGapText(rounds: number): string {
  if (rounds === 0) return 'On pace'
  const count = Math.abs(rounds)
  return `${count} ${count === 1 ? 'round' : 'rounds'} ${rounds > 0 ? 'ahead' : 'behind'}`
}

/** What there is to do in this round. A Tabata takes its items in turns, one per round. */
export function roundItems(workout: Wod, round: number): WodItem[] {
  if (workout.timer.mode !== 'tabata' || workout.items.length === 0) return workout.items
  return [workout.items[(round - 1) % workout.items.length]]
}

/** How much of an item this round asks for: the scheme's number for reps, else its own amount. */
export function itemAmount(workout: Wod, item: WodItem, round: number): number {
  if (item.unit !== 'reps' || workout.scheme.length === 0) return item.amount
  return workout.scheme[Math.min(round, workout.scheme.length) - 1]
}

/** A WOD's weight in the unit the app shows: half kilos, or the 5 lb steps of a plate rack. */
export function loadIn(kg: number, unit: Unit): number {
  return unit === 'kg' ? roundTo(kg, 0.5) : roundTo(convert(kg, 'kg', 'lb'), 5)
}

/** A weight typed in the app's unit, as the kg a WOD stores. */
export function loadFrom(value: number, unit: Unit): number {
  return roundTo(convert(value, unit, 'kg'), 0.5)
}

/** "400 m", "1.6 km". */
export function distance(meters: number): string {
  return meters >= 1000 && meters % 100 === 0 ? `${meters / 1000} km` : `${meters} m`
}

/**
 * "21 × Thruster 43kg", "400 m Run", "30s Plank", "Max Push-ups".
 * The "×" keeps a count from reading as bad grammar next to a name that is singular or plural.
 */
export function itemText(item: WodItem, amount: number, name: string, unit: Unit): string {
  const load = item.load > 0 ? ` ${formatWeight(loadIn(item.load, unit), unit)}` : ''
  if (amount === 0) return `Max ${name}${load}`
  if (item.unit === 'meters') return `${distance(amount)} ${name}${load}`
  if (item.unit === 'seconds') return `${holdText(amount)} ${name}${load}`
  return `${amount} × ${name}${load}`
}

type Find = (id: string) => Exercise | undefined

/** The lines of one round, ready to show. An exercise that no longer exists is left out. */
export function roundLines(workout: Wod, round: number, find: Find, unit: Unit): string[] {
  return roundItems(workout, round).flatMap((item) => {
    const exercise = find(item.exerciseId)
    return exercise ? [itemText(item, itemAmount(workout, item, round), exercise.name, unit)] : []
  })
}

/**
 * The lines of the whole WOD for its card and its detail. With a scheme the reps are left to
 * the headline: "Thruster 43kg" under "21-15-9".
 */
export function wodLines(workout: Wod, find: Find, unit: Unit): string[] {
  return workout.items.flatMap((item) => {
    const exercise = find(item.exerciseId)
    if (!exercise) return []
    if (item.unit === 'reps' && workout.scheme.length > 0) {
      const load = item.load > 0 ? ` ${formatWeight(loadIn(item.load, unit), unit)}` : ''
      return [`${exercise.name}${load}`]
    }
    return [itemText(item, item.amount, exercise.name, unit)]
  })
}

/** "21-15-9 · For Time", "5 rounds · For Time", "AMRAP 20:00", "EMOM 30 × 1:00", "Tabata 8 × 20s / 10s". */
export function wodFormat(workout: Wod): string {
  const { timer } = workout
  if (timer.mode === 'amrap') return `AMRAP ${holdText(timer.work)}`
  if (timer.mode === 'emom') return `EMOM ${timer.rounds} × ${holdText(timer.work)}`
  if (timer.mode === 'tabata') {
    return `Tabata ${timer.rounds} × ${holdText(timer.work)} / ${holdText(timer.rest)}`
  }
  if (workout.scheme.length > 0) return `${workout.scheme.join('-')} · For Time`
  return workout.rounds > 1 ? `${workout.rounds} rounds · For Time` : 'For Time'
}

/** What a WOD is called: its name, or its format while the lifter has not given it one. */
export function wodName(workout: Wod): string {
  return workout.name || wodFormat(workout)
}

/**
 * True when two versions of a WOD ask for the same work, so a time or a round count of one can be
 * compared with the other. The name and the cap of a For Time do not change the work.
 */
export function sameWork(a: Wod, b: Wod): boolean {
  const work = (workout: Wod) =>
    JSON.stringify([
      workout.timer.mode,
      workout.timer.mode === 'forTime' ? 0 : [workout.timer.work, workout.timer.rest, workout.timer.rounds],
      workout.rounds,
      workout.scheme,
      workout.items.map((item) => [item.exerciseId, item.amount, item.unit, item.load]),
    ])
  return work(a) === work(b)
}

/** Up to three different pictures for the card, in the order of the items. */
export function wodImages(workout: Wod, find: Find): string[] {
  const images: string[] = []
  for (const item of workout.items) {
    const image = find(item.exerciseId)?.image
    if (image && !images.includes(image)) images.push(image)
  }
  return images.slice(0, 3)
}

/** True when nothing in it needs a weight: every item is a bodyweight exercise without load. */
export function isBodyweightWod(workout: Wod, find: Find): boolean {
  return workout.items.every(
    (item) => item.load === 0 && find(item.exerciseId)?.equipment === 'bodyweight',
  )
}

/**
 * The best finished run of a WOD: the fastest For Time that was finished (not capped, not
 * stopped), or the AMRAP with the most rounds. EMOM and Tabata have nothing to beat.
 */
export function wodBest(sessions: TimerSession[], wodId: string): TimerSession | null {
  let best: TimerSession | null = null
  for (const session of sessions) {
    if (session.wodId !== wodId || timerOutcome(session) !== 'completed') continue
    const { mode } = session.config
    if (mode === 'forTime') {
      if (!best || session.seconds < best.seconds) best = session
    } else if (mode === 'amrap' && session.rounds > 0) {
      if (!best || session.rounds > best.rounds) best = session
    }
  }
  return best
}

/** "4:52" for a For Time, "18 rounds" for an AMRAP. */
export function bestText(session: TimerSession): string {
  if (session.config.mode === 'forTime') return holdText(session.seconds)
  return `${session.rounds} ${session.rounds === 1 ? 'round' : 'rounds'}`
}

/**
 * True when this session beat every earlier run of its WOD. `others` are the sessions without it.
 * A first run has nothing to beat, so it is not a record.
 */
export function isWodRecord(others: TimerSession[], session: TimerSession): boolean {
  if (!session.wodId || timerOutcome(session) !== 'completed') return false
  const before = wodBest(others, session.wodId)
  if (!before) return false
  if (session.config.mode === 'forTime') return session.seconds < before.seconds
  return session.config.mode === 'amrap' && session.rounds > before.rounds
}

const reps = (exerciseId: string, amount: number, load = 0): WodItem => ({
  exerciseId,
  amount,
  unit: 'reps',
  load,
})
const meters = (exerciseId: string, amount: number): WodItem => ({
  exerciseId,
  amount,
  unit: 'meters',
  load: 0,
})
const rest = (amount: number): WodItem => ({ exerciseId: 'rest', amount, unit: 'seconds', load: 0 })

function forTime(id: string, name: string, cap: number, items: WodItem[], rounds = 1, scheme: number[] = []): Wod {
  return wod({ id, name, timer: { mode: 'forTime', work: cap, rest: 0, rounds: 1 }, rounds, scheme, items })
}

function amrap(id: string, name: string, seconds: number, items: WodItem[]): Wod {
  return wod({ id, name, timer: { mode: 'amrap', work: seconds, rest: 0, rounds: 1 }, rounds: 1, scheme: [], items })
}

/**
 * Well-known benchmark workouts, ready to start. Weights are the usual ones in kg.
 * They are not stored in the state: the lifter starts them as they are, or edits a copy.
 */
export const WOD_LIBRARY: Wod[] = [
  forTime('wod-fran', 'Fran', 600, [reps('thruster', 21, 43), reps('pullups', 21)], 3, [21, 15, 9]),
  amrap('wod-cindy', 'Cindy', 1200, [reps('pullups', 5), reps('pushups', 10), reps('airSquat', 15)]),
  forTime('wod-helen', 'Helen', 1200, [meters('run', 400), reps('kettlebellSwing', 21, 24), reps('pullups', 12)], 3),
  forTime('wod-grace', 'Grace', 600, [reps('cleanAndJerk', 30, 61)]),
  forTime('wod-annie', 'Annie', 1200, [reps('doubleUnder', 50), reps('situps', 50)], 5, [50, 40, 30, 20, 10]),
  forTime('wod-karen', 'Karen', 1200, [reps('wallBall', 150, 9)]),
  forTime('wod-angie', 'Angie', 2400, [
    reps('pullups', 100),
    reps('pushups', 100),
    reps('situps', 100),
    reps('airSquat', 100),
  ]),
  forTime(
    'wod-barbara',
    'Barbara',
    3600,
    [reps('pullups', 20), reps('pushups', 30), reps('situps', 40), reps('airSquat', 50)],
    5,
  ),
  wod({
    id: 'wod-chelsea',
    name: 'Chelsea',
    timer: { mode: 'emom', work: 60, rest: 0, rounds: 30 },
    rounds: 1,
    scheme: [],
    items: [reps('pullups', 5), reps('pushups', 10), reps('airSquat', 15)],
  }),
  amrap('wod-mary', 'Mary', 1200, [reps('handstandPushup', 5), reps('pistolSquat', 10), reps('pullups', 15)]),
  forTime('wod-jackie', 'Jackie', 1200, [meters('rowErg', 1000), reps('thruster', 50, 20), reps('pullups', 30)]),
  forTime('wod-diane', 'Diane', 900, [reps('deadlift', 21, 102), reps('handstandPushup', 21)], 3, [21, 15, 9]),
  forTime('wod-isabel', 'Isabel', 600, [reps('snatch', 30, 61)]),
  forTime(
    'wod-dt',
    'DT',
    1200,
    [reps('deadlift', 12, 70), reps('hangPowerClean', 9, 70), reps('pushJerk', 6, 70)],
    5,
  ),
  forTime('wod-murph', 'Murph', 3600, [
    meters('run', 1600),
    reps('pullups', 100),
    reps('pushups', 200),
    reps('airSquat', 300),
    meters('run', 1600),
  ]),
  forTime('wod-venus', 'Venus', 2400, [reps('pushups', 50), reps('situps', 20), reps('airSquat', 50)], 4),
  forTime(
    'wod-aphrodite',
    'Aphrodite',
    2700,
    [reps('burpee', 50), reps('airSquat', 50), reps('situps', 50)],
    5,
    [50, 40, 30, 20, 10],
  ),
  forTime('wod-helios', 'Helios', 3600, [
    reps('burpee', 100),
    reps('bodyweightLunge', 125),
    reps('climber', 150),
    reps('situps', 125),
    reps('climber', 150),
    reps('bodyweightLunge', 125),
    reps('burpee', 100),
  ]),
  forTime(
    'wod-zeus',
    'Zeus',
    3600,
    [
      reps('handstandPushup', 10),
      reps('pullups', 20),
      reps('pushups', 30),
      reps('situps', 40),
      reps('airSquat', 50),
      rest(120),
    ],
    4,
  ),
  forTime(
    'wod-ares',
    'Ares',
    1800,
    [reps('pullups', 7), reps('situps', 7), meters('sprint', 40), meters('sprint', 40), rest(60)],
    5,
  ),
  forTime(
    'wod-gaia',
    'Gaia',
    3600,
    [reps('jumpingJack', 40), reps('jump', 30), reps('climber', 20), reps('standup', 10)],
    10,
  ),
]

/** A library WOD or one of the lifter's own with this id. */
export function findWod(own: Wod[], id: string): Wod | undefined {
  return own.find((entry) => entry.id === id) ?? WOD_LIBRARY.find((entry) => entry.id === id)
}

/** True for a WOD that ships with the app: it cannot be edited or deleted, only copied. */
export function isLibraryWod(id: string): boolean {
  return WOD_LIBRARY.some((entry) => entry.id === id)
}
