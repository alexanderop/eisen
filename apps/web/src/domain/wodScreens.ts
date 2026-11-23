import { CATALOG, type Equipment, type Exercise } from './exercises'
import { holdText } from './program'
import {
  DEFAULT_TIMERS,
  sessionSummary,
  TIMER_MODES,
  type TimerConfig,
  type TimerMode,
  type TimerRun,
  type TimerSession,
  type TimerView,
} from './timer'
import { timeWheel } from './timerScreens'
import { formatWeight, type Unit } from './units'
import {
  bestText,
  distance,
  isBodyweightWod,
  isWodRecord,
  itemAmount,
  itemText,
  loadIn,
  MAX_SCHEME_LENGTH,
  MAX_WOD_NAME,
  roundLines,
  SCHEMES,
  sameWork,
  wod,
  wodFormat,
  wodLines,
  wodName,
  wodRound,
  wodRounds,
  wodStep,
  WOD_LIBRARY,
  type Wod,
  type WodItem,
  type WodUnit,
} from './wod'

type Find = (id: string) => Exercise | undefined

export type WodFilter = 'all' | 'bodyweight' | 'forTime' | 'amrap'

export const WOD_FILTERS: { value: WodFilter; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'bodyweight', label: 'No weights' },
  { value: 'forTime', label: 'For Time' },
  { value: 'amrap', label: 'AMRAP' },
]

export function filterWods(list: Wod[], filter: WodFilter, find: Find): Wod[] {
  if (filter === 'all') return list
  if (filter === 'bodyweight') return list.filter((entry) => isBodyweightWod(entry, find))
  return list.filter((entry) => entry.timer.mode === filter)
}

/**
 * What a card reads to a screen reader, with the pauses a sighted lifter gets from the layout:
 * "Fran. 21-15-9, For Time. Thruster 43kg, Pull-ups. Best 4:52."
 */
export function cardLabel(workout: Wod, find: Find, unit: Unit, best: TimerSession | null): string {
  const name = wodName(workout)
  const format = wodFormat(workout)
  const parts = [name, ...(format === name ? [] : [format.replaceAll(' · ', ', ')])]
  const lines = wodLines(workout, find, unit)
  if (lines.length > 0) parts.push(lines.join(', '))
  if (best) parts.push(`Best ${bestText(best)}`)
  return `${parts.join('. ')}.`
}

/** The exercises a WOD is usually made of: those of the benchmarks and the Conditioning group. */
export const WOD_EXERCISE_IDS: ReadonlySet<string> = new Set([
  ...WOD_LIBRARY.flatMap((entry) => entry.items.map((item) => item.exerciseId)),
  ...CATALOG.filter((exercise) => exercise.group === 'Conditioning').map((exercise) => exercise.id),
])

/**
 * How well a name matches a search: 0 when a word of it starts with the text ("run" in "Run"),
 * 1 when the text is inside a word ("run" in "Cable Crunch"), null when it is not in it.
 */
export function searchRank(name: string, query: string): number | null {
  const needle = query.trim().toLowerCase()
  const lower = name.toLowerCase()
  if (!needle) return 0
  const at = lower.indexOf(needle)
  if (at === -1) return null
  return lower.split(/[\s-]+/).some((word) => word.startsWith(needle)) || lower.startsWith(needle) ? 0 : 1
}

/** The builder's names for the schemes in {@link SCHEMES}, in the same order. */
export const SCHEME_LABELS = ['21-15-9', '10 down to 1', '50-40-30-20-10', '1 up to 10']

export const WOD_UNIT_LABELS: Record<WodUnit, string> = { reps: 'Reps', seconds: 'Seconds', meters: 'Metres' }

/** What an item starts with when its unit changes or it stops being Max. */
export const DEFAULT_AMOUNTS: Record<WodUnit, number> = { reps: 10, seconds: 30, meters: 400 }

/** Under the Max switch, which only a Tabata has. */
export const MAX_HINTS: Record<WodUnit, string> = {
  reps: 'As many reps as possible each round',
  seconds: 'As long as possible each round',
  meters: 'As far as possible each round',
}

/** Movements that are counted in metres, not reps. */
const DISTANCE_IDS = ['run', 'rowErg']

/**
 * A movement as it is added in the builder: 10 reps, 30 seconds held or 400 metres, at its start
 * weight. In a Tabata it starts as Max: there the work time is the limit, not a count.
 */
export function newItem(exercise: Exercise, mode: TimerMode): WodItem {
  const unit: WodUnit = DISTANCE_IDS.includes(exercise.id) ? 'meters' : exercise.timed ? 'seconds' : 'reps'
  return {
    exerciseId: exercise.id,
    amount: mode === 'tabata' ? 0 : DEFAULT_AMOUNTS[unit],
    unit,
    load: exercise.equipment === 'bodyweight' ? 0 : exercise.start,
  }
}

/** The numbers of one type as the lifter left them, to bring back when the type is picked again. */
export interface TypeNumbers {
  timer: TimerConfig
  rounds: number
  scheme: number[]
}

export type TypeMemory = Partial<Record<TimerMode, TypeNumbers>>

/** Where the builder goes back to. */
export type WodBack = { kind: 'list' } | { kind: 'detail'; id: string }

/** A WOD in the builder: the draft, what it was when it opened, and the numbers of the other types. */
export interface WodEdit {
  draft: Wod
  /** The draft as it opened, as JSON: anything else is a change to ask about. */
  start: string
  back: WodBack
  memory: TypeMemory
}

function edit(draft: Wod, back: WodBack): WodEdit {
  return { draft, start: JSON.stringify(draft), back, memory: {} }
}

/** "New WOD". */
export function newEdit(id: string): WodEdit {
  return edit({ id, name: '', timer: { ...DEFAULT_TIMERS.forTime }, rounds: 1, scheme: [], items: [] }, { kind: 'list' })
}

/** "Edit": the WOD itself, saved under its id unless the work changes. */
export function editOf(source: Wod): WodEdit {
  return edit(structuredClone(source), { kind: 'detail', id: source.id })
}

/**
 * "Edit a copy": a new WOD with the same content, named "Fran (mine)". It is a draft like any new
 * WOD: nothing is stored until Save, and backing out leaves nothing behind.
 */
export function copyEdit(source: Wod, id: string): WodEdit {
  return edit({ ...structuredClone(source), id, name: copyName(wodName(source)) }, { kind: 'detail', id: source.id })
}

export function isChanged(current: WodEdit): boolean {
  return JSON.stringify(current.draft) !== current.start
}

/**
 * The draft with another type. The movements stay. The numbers of the type it leaves are kept
 * for later, and the new type gets back the numbers it had, or its defaults. Max only exists in a
 * Tabata: leaving it, a Max movement gets the usual amount of its unit.
 */
export function switchMode(draft: Wod, memory: TypeMemory, mode: TimerMode): { draft: Wod; memory: TypeMemory } {
  const from = draft.timer.mode
  if (from === mode) return { draft, memory }
  const kept: TypeMemory = {
    ...memory,
    [from]: { timer: { ...draft.timer }, rounds: draft.rounds, scheme: [...draft.scheme] },
  }
  const back = kept[mode] ?? { timer: { ...DEFAULT_TIMERS[mode] }, rounds: 1, scheme: [] }
  const items =
    from === 'tabata'
      ? draft.items.map((item) => (item.amount === 0 ? { ...item, amount: DEFAULT_AMOUNTS[item.unit] } : item))
      : draft.items
  return {
    draft: { ...draft, timer: { ...back.timer }, rounds: back.rounds, scheme: [...back.scheme], items },
    memory: kept,
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isDraft(value: unknown): value is Wod {
  return (
    isObject(value) &&
    typeof value.id === 'string' &&
    typeof value.name === 'string' &&
    isObject(value.timer) &&
    TIMER_MODES.includes(value.timer.mode as TimerMode) &&
    typeof value.rounds === 'number' &&
    Array.isArray(value.scheme) &&
    Array.isArray(value.items) &&
    value.items.every((item) => isObject(item) && typeof item.exerciseId === 'string')
  )
}

/** A builder draft kept in storage, or null when there is none or it cannot be read. */
export function readEdit(json: string | null): WodEdit | null {
  try {
    const value: unknown = JSON.parse(json ?? 'null')
    if (!isObject(value) || !isDraft(value.draft) || typeof value.start !== 'string') return null
    const back = value.back as WodBack | undefined
    return {
      draft: value.draft,
      start: value.start,
      back: back?.kind === 'detail' && typeof back.id === 'string' ? back : { kind: 'list' },
      memory: isObject(value.memory) ? (value.memory as TypeMemory) : {},
    }
  } catch {
    return null
  }
}

/**
 * The WOD as it is stored from the builder. An edit that changes the work gets a new id: its old
 * runs stay in the history but are no longer the time to beat. A new name or cap keeps the id.
 */
export function savedVersion(draft: Wod, saved: Wod | undefined, newId: string): Wod {
  const clean = wod(draft)
  return saved && !sameWork(wod(saved), clean) ? { ...clean, id: newId } : clean
}

/** "Fran (mine)", shortened so it fits the name limit. */
export function copyName(name: string): string {
  const suffix = ' (mine)'
  return `${name.slice(0, MAX_WOD_NAME - suffix.length).trimEnd()}${suffix}`
}

/** Reps typed as "30-20-10" or "30 20 10". Anything that is not a whole number above 0 is left out. */
export function parseScheme(text: string): number[] {
  return text
    .split(/[\s,–—-]+/)
    .filter((part) => /^\d+$/.test(part))
    .map(Number)
    .filter((reps) => reps > 0)
    .slice(0, MAX_SCHEME_LENGTH)
}

/** Which scheme chip is on: "same", the index of one of {@link SCHEMES}, or "custom". */
export function schemeChoice(scheme: number[]): string {
  if (scheme.length === 0) return 'same'
  const index = SCHEMES.findIndex((entry) => entry.join('-') === scheme.join('-'))
  return index === -1 ? 'custom' : String(index)
}

function withCurrent(values: number[], current: number): number[] {
  if (current <= 0 || values.includes(current)) return values
  return [...values, current].sort((a, b) => a - b)
}

const METRES = [100, 200, 400, 500, 800, 1000, 1600, 2000, 5000]

/**
 * What the amount wheel of an item offers: reps 1 to 30 one by one, then in steps of 5, 25 and
 * 100 up to 1000; seconds like the timer wheels; the usual distances. The current amount is
 * always one of them.
 */
export function amountWheel(unit: WodUnit, current: number): number[] {
  if (unit === 'seconds') return withCurrent(timeWheel(5, 600, 5), current)
  if (unit === 'meters') return withCurrent(METRES, current)
  const values: number[] = []
  for (let reps = 1; reps <= 1000; reps += reps < 30 ? 1 : reps < 100 ? 5 : reps < 500 ? 25 : 100) {
    values.push(reps)
  }
  return withCurrent(values, current)
}

/** "21", "1:30", "1.6 km": a row of the amount wheel. */
export function amountText(unit: WodUnit, amount: number): string {
  if (unit === 'meters') return distance(amount)
  if (unit === 'seconds') return holdText(amount)
  return String(amount)
}

/**
 * The loads a movement offers in the app's unit, from none up. Dumbbells and kettlebells go up a
 * kilo at a time to 40 kg, then like bars and machines in 2.5 kg steps to 200 kg. In pounds every
 * kind goes in 5 lb steps to 440 lb. The load the item had when its sheet opened stays in the list.
 */
export function loadWheel(equipment: Equipment, unit: Unit, opened: number): number[] {
  const values = [0]
  if (unit === 'lb') {
    for (let load = 5; load <= 440; load += 5) values.push(load)
  } else {
    const small = equipment === 'weight'
    for (let load = small ? 1 : 2.5; load <= 200; load += small && load < 40 ? 1 : 2.5) values.push(load)
  }
  return withCurrent(values, opened)
}

/** A row of the load wheel: "None" or "16kg". */
export function loadText(load: number, unit: Unit): string {
  return load === 0 ? 'None' : formatWeight(load, unit)
}

/** The builder's line under a movement: "21 reps · 43kg", "400 m", "30s", "Max", "by scheme". */
export function itemSummary(workout: Wod, item: WodItem, unit: Unit): string {
  const load = item.load > 0 ? ` · ${formatWeight(loadIn(item.load, unit), unit)}` : ''
  if (item.unit === 'reps' && workout.scheme.length > 0) return `by scheme${load}`
  if (item.amount === 0) return `Max${load}`
  if (item.unit === 'reps') return `${item.amount} ${item.amount === 1 ? 'rep' : 'reps'}${load}`
  return `${amountText(item.unit, item.amount)}${load}`
}

/** The newest run of a WOD, whatever its outcome. */
export function lastRun(sessions: TimerSession[], wodId: string): TimerSession | null {
  let last: TimerSession | null = null
  for (const session of sessions) {
    if (session.wodId === wodId && (!last || session.startedAt > last.startedAt)) last = session
  }
  return last
}

/** "4:52", "18 rounds", or for an EMOM or Tabata "30 rounds · 30:00". */
export function runText(session: TimerSession): string {
  const { mode } = session.config
  return mode === 'forTime' || mode === 'amrap' ? bestText(session) : sessionSummary(session)
}

/** The ids of the WOD runs that beat every run of their WOD before them. */
export function wodRecordIds(sessions: TimerSession[]): Set<string> {
  const ids = new Set<string>()
  for (const session of sessions) {
    if (!session.wodId) continue
    const earlier = sessions.filter(
      (other) => other.wodId === session.wodId && other.startedAt < session.startedAt,
    )
    if (isWodRecord(earlier, session)) ids.add(session.id)
  }
  return ids
}

/**
 * Over the round card of a running WOD, the one count on screen: "Round 2 of 3", and in a rest
 * "Up next · Round 3 of 8". Nothing in an AMRAP, whose button counts the rounds, and nothing for a
 * single round.
 */
export function roundHeading(workout: Wod, taps: number, view: TimerView): string {
  const rounds = wodRounds(workout)
  if (rounds === null || rounds <= 1) return ''
  if (view.phase === 'rest') return `Up next · Round ${Math.min(view.round + 1, rounds)} of ${rounds}`
  return `Round ${wodRound(workout, taps, view.round)} of ${rounds}`
}

/** One line of the round card. `done` and `current` only in a single round of several items. */
export interface CardLine {
  text: string
  state: 'plain' | 'done' | 'current'
}

/**
 * What the round card shows: the lines of the round the lifter is in, in a rest those of the
 * coming round. A single-round For Time shows every item, ticked off in order.
 */
export function roundCard(workout: Wod, taps: number, view: TimerView, find: Find, unit: Unit): CardLine[] {
  const plain = (text: string): CardLine => ({ text, state: 'plain' })
  if (view.phase === 'rest') return roundLines(workout, view.round + 1, find, unit).map(plain)
  if (workout.timer.mode === 'forTime' && workout.rounds === 1 && workout.items.length > 1) {
    return workout.items.map((item, index) => {
      const name = find(item.exerciseId)?.name ?? 'Deleted exercise'
      return {
        text: itemText(item, itemAmount(workout, item, 1), name, unit),
        state: index < taps ? 'done' : index === taps ? 'current' : 'plain',
      }
    })
  }
  return roundLines(workout, wodRound(workout, taps, view.round), find, unit).map(plain)
}

/** The big button of a For Time WOD: what a tap does now. */
export function wodButton(
  workout: Wod,
  taps: number,
  find: Find,
  unit: Unit,
): { big: string; small: string } {
  if (workout.rounds > 1) return { big: 'Round done', small: 'tap' }
  if (workout.items.length > 1) {
    const item = workout.items[wodStep(workout, taps)]
    const name = find(item.exerciseId)?.name ?? 'Deleted exercise'
    return { big: 'Next', small: itemText(item, itemAmount(workout, item, 1), name, unit) }
  }
  return { big: 'Done', small: 'tap when finished' }
}

/** "Not finished? Undo": the run as it was before the tap that ended it. */
export function undoneRun(run: TimerRun): TimerRun {
  return { ...run, taps: Math.max(0, run.taps - 1) }
}
