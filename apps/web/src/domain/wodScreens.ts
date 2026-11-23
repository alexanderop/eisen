import { CATALOG, type Equipment, type Exercise } from './exercises'
import { motionOf } from './motions'
import { holdText } from './program'
import {
  DEFAULT_TIMERS,
  sessionSummary,
  TIMER_LEAD_IN_SECONDS,
  TIMER_MODES,
  tapsPerRound,
  timerOutcome,
  VOICE_AFTER_BEEP,
  wodSteps,
  type TimerConfig,
  type TimerCue,
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
  gapText,
  isBodyweightWod,
  isRest,
  isWodRecord,
  itemAmount,
  itemText,
  liveGap,
  loadIn,
  MAX_SCHEME_LENGTH,
  MAX_WOD_NAME,
  roundItems,
  roundLines,
  roundsGap,
  roundsGapText,
  SCHEMES,
  sameWork,
  wod,
  wodFormat,
  wodLines,
  wodName,
  wodDone,
  wodRest,
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
 * The WODs page when a search finds nothing: "No WODs match “fra”", and with a chip on, which one
 * narrowed it: "No WODs match “fra” in No weights".
 */
export function noMatchText(query: string, filter: WodFilter): string {
  const text = `No WODs match “${query.trim()}”`
  if (filter === 'all') return text
  return `${text} in ${WOD_FILTERS.find((entry) => entry.value === filter)?.label ?? filter}`
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

/** Most figures a banner shows: more would get too small to read on a phone. */
export const MAX_BANNER_FIGURES = 4

/** One place in a WOD's banner: the figure of a movement, or its still picture where it has none. */
export interface BannerSlot {
  exerciseId: string
  /** The motion strip, null for a movement without one. */
  strip: string | null
}

/**
 * The places of a WOD's banner: its movements in order, each once, at most
 * {@link MAX_BANNER_FIGURES}. Rests are no movement. Empty when not one of them has a strip:
 * the card keeps its still tile then.
 */
export function bannerMotions(workout: Wod): BannerSlot[] {
  const slots: BannerSlot[] = []
  for (const item of workout.items) {
    if (isRest(item) || slots.some((slot) => slot.exerciseId === item.exerciseId)) continue
    slots.push({ exerciseId: item.exerciseId, strip: motionOf(item.exerciseId) ?? null })
  }
  const shown = slots.slice(0, MAX_BANNER_FIGURES)
  return shown.some((slot) => slot.strip) ? shown : []
}

/**
 * A suggestion's text cut around what was typed, so that part can be bold: "Pu" in "Push-ups"
 * gives ["", "Pu", "sh-ups"]. A match at the start of a word wins over one inside a word.
 * Without a match the whole text is the first part.
 */
export function typedParts(text: string, typed: string): [string, string, string] {
  const needle = typed.trim().toLowerCase()
  const lower = text.toLowerCase()
  if (!needle) return [text, '', '']
  let at = -1
  for (let index = lower.indexOf(needle); index !== -1; index = lower.indexOf(needle, index + 1)) {
    if (index === 0 || !/[a-z0-9]/.test(lower[index - 1])) {
      at = index
      break
    }
    if (at === -1) at = index
  }
  if (at === -1) return [text, '', '']
  return [text.slice(0, at), text.slice(at, at + needle.length), text.slice(at + needle.length)]
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
 * Over the round card of a running WOD: "Round 3 of 30" in an EMOM or Tabata, and in a rest
 * "Up next · Round 3 of 8". A For Time says its round next to the phase word instead, so here only
 * in a rest that ends a round. Nothing in an AMRAP, whose button counts the rounds.
 */
export function roundHeading(workout: Wod, taps: number, view: TimerView, resting = false): string {
  const rounds = wodRounds(workout)
  if (rounds === null) return ''
  if (workout.timer.mode === 'forTime') {
    if (!resting) return ''
    const next = taps % tapsPerRound(workout) === 0
    if (!next) return 'Up next'
    return rounds > 1 ? `Up next · Round ${wodRound(workout, taps, 1)} of ${rounds}` : 'Up next'
  }
  if (rounds <= 1) return ''
  if (view.phase === 'rest') return `Up next · Round ${Math.min(view.round + 1, rounds)} of ${rounds}`
  return `Round ${wodRound(workout, taps, view.round)} of ${rounds}`
}

/** "2/4" after the phase word of a For Time of several rounds: the round being done. */
export function roundTag(workout: Wod, taps: number): string {
  if (workout.timer.mode !== 'forTime' || workout.rounds <= 1) return ''
  return `${wodRound(workout, taps, 1)}/${workout.rounds}`
}

/** True when the tap that made it `taps` finished a round and another one follows. */
export function endsRound(workout: Wod, taps: number): boolean {
  if (workout.timer.mode !== 'forTime' || workout.rounds <= 1 || taps === 0) return false
  return taps % tapsPerRound(workout) === 0 && !wodDone(workout, taps)
}

/** One line of the round card. `done` and `current` only where the items are ticked off, in a For Time. */
export interface CardLine {
  text: string
  state: 'plain' | 'done' | 'current'
  /** For the line's picture. */
  exerciseId: string
}

/**
 * What the round card shows: the lines of the round the lifter is in, in a rest those of the
 * coming round. A For Time ticks off the items of its round in order, one tap each. A rest line is
 * ticked off with the item before it and is never the one to do. In a rest nothing is to do: a
 * rest that ends the round shows the next round untouched.
 */
export function roundCard(
  workout: Wod,
  taps: number,
  view: TimerView,
  find: Find,
  unit: Unit,
  resting = false,
): CardLine[] {
  const plain = (round: number): CardLine[] => {
    const items = roundItems(workout, round).filter((item) => find(item.exerciseId))
    return roundLines(workout, round, find, unit).map((text, index) => ({
      text,
      state: 'plain',
      exerciseId: items[index].exerciseId,
    }))
  }
  if (view.phase === 'rest') return plain(view.round + 1)
  const round = wodRound(workout, taps, view.round)
  if (workout.timer.mode !== 'forTime') return plain(round)
  // Every item is a tap, so one whose exercise was deleted keeps its line.
  const step = wodStep(workout, taps)
  const fresh = resting && taps % tapsPerRound(workout) === 0
  return workout.items.map((item, index) => ({
    text: itemLine(workout, item, round, find, unit),
    state: fresh || index > step ? 'plain' : index < step ? 'done' : resting ? 'plain' : 'current',
    exerciseId: item.exerciseId,
  }))
}

function itemLine(workout: Wod, item: WodItem, round: number, find: Find, unit: Unit): string {
  return itemText(item, itemAmount(workout, item, round), find(item.exerciseId)?.name ?? 'Deleted exercise', unit)
}

/** The big button of a For Time WOD: the line a tap ticks off, and what comes after it. */
export interface WodButton {
  /** "50 × Push-ups". */
  line: string
  /** "tap when done · then 20 × Sit-ups", "tap when done · finishes the WOD". */
  hint: string
  /** For a screen reader: "50 × Push-ups done. Next: 20 × Sit-ups." */
  label: string
}

/** What follows the item a tap ticks off: "20 × Sit-ups", "rest 2:00", "round 3". Null on the very last. */
function nextThing(workout: Wod, taps: number, find: Find, unit: Unit): string | null {
  if (taps >= (wodSteps(workout) ?? 0) - 1) return null
  const step = wodStep(workout, taps)
  let rest = 0
  for (let index = step + 1; index < workout.items.length && isRest(workout.items[index]); index += 1) {
    rest += workout.items[index].unit === 'seconds' ? workout.items[index].amount : 0
  }
  if (rest > 0) return `rest ${holdText(rest)}`
  const round = wodRound(workout, taps, 1)
  const next = workout.items.findIndex((item, index) => index > step && !isRest(item))
  if (next !== -1) return itemLine(workout, workout.items[next], round, find, unit)
  return `round ${round + 1}`
}

export function wodButton(workout: Wod, taps: number, find: Find, unit: Unit): WodButton {
  if (workout.items.every(isRest)) return { line: 'Done', hint: 'tap when finished', label: 'Done' }
  const step = wodStep(workout, taps)
  const line = itemLine(workout, workout.items[step], wodRound(workout, taps, 1), find, unit)
  const next = nextThing(workout, taps, find, unit)
  if (next === null) {
    return { line, hint: 'tap when done · finishes the WOD', label: `${line} done. Finishes the WOD.` }
  }
  return { line, hint: `tap when done · then ${next}`, label: `${line} done. Next: ${next}.` }
}

/** "Not finished? Undo" and "Back": the run as it was before its last tap. A skipped rest comes back. */
export function undoneRun(run: TimerRun): TimerRun {
  const { restSkipped: _, ...kept } = run
  const taps = Math.max(0, run.taps - 1)
  return { ...kept, taps, ...(run.splits ? { splits: run.splits.slice(0, taps) } : {}) }
}

/** The rest a running WOD is in at `elapsed` seconds into it, unless the lifter skipped it. */
export function runRest(run: TimerRun, elapsed: number): { left: number; total: number } | null {
  if (!run.wod || run.restSkipped === run.taps) return null
  return wodRest(run.wod, run.splits ?? [], elapsed)
}

/**
 * The sounds at the end of the rest the last tap started: three short beeps and a long one, then
 * "Go", as the run's calls allow. Timed like {@link TimerCue}: milliseconds of the run, lead-in
 * included, pauses not counted. None once the rest is skipped.
 */
export function restCues(run: TimerRun): TimerCue[] {
  const splits = run.splits ?? []
  if (splits.length === 0) return []
  const start = splits[splits.length - 1]
  const rest = runRest(run, start)
  if (!rest) return []
  const begin = (TIMER_LEAD_IN_SECONDS + start) * 1000
  const end = begin + rest.total * 1000
  const cues: TimerCue[] = []
  if (run.calls.beeps) {
    for (const before of [3, 2, 1]) {
      if (end - before * 1000 > begin) cues.push({ at: end - before * 1000, sound: 'beep' })
    }
    cues.push({ at: end, sound: 'beep-long' })
  }
  if (run.calls.phases) cues.push({ at: end + VOICE_AFTER_BEEP, sound: 'go' })
  return cues
}

export type PaceTone = 'ahead' | 'behind' | 'level'

/** The line between the clock and the round card of a For Time or an AMRAP. */
export interface PaceLine {
  /** "Best 12:40", "Best 18 rounds", or "First run: this sets your best". */
  best: string
  /** "12s ahead", "2 rounds behind", "On pace". Null while there is nothing to measure against. */
  gap: string | null
  tone: PaceTone
  /** "▲" ahead, "▼" behind, "" on pace: so colour is not the only sign. */
  arrow: string
}

const ARROWS: Record<PaceTone, string> = { ahead: '▲', behind: '▼', level: '' }

function paced(line: PaceLine, gap: string, tone: PaceTone): PaceLine {
  return { ...line, gap, tone, arrow: ARROWS[tone] }
}

/**
 * How the run stands against the best one, live. Null in an EMOM or a Tabata, which have
 * nothing to beat. In the lead-in only the best: nothing has started yet.
 */
export function paceLine(run: TimerRun, best: TimerSession | null, view: TimerView): PaceLine | null {
  const mode = run.wod?.timer.mode
  if (mode !== 'forTime' && mode !== 'amrap') return null
  if (!best) return { best: 'First run: this sets your best', gap: null, tone: 'level', arrow: '' }
  const line: PaceLine = { best: `Best ${bestText(best)}`, gap: null, tone: 'level', arrow: '' }
  if (view.phase === 'ready') return line
  const elapsed = view.elapsed / 1000
  if (mode === 'forTime') {
    const gap = liveGap(best, run.splits ?? [], elapsed)
    if (gap === null) return line
    return paced(line, gapText(gap), gap < 0 ? 'ahead' : gap > 0 ? 'behind' : 'level')
  }
  const rounds = roundsGap(best, run.taps, elapsed)
  if (rounds === null) return line
  return paced(line, roundsGapText(rounds), rounds > 0 ? 'ahead' : rounds < 0 ? 'behind' : 'level')
}

/**
 * Under the result of a finished run, measured against the best before it: "12s faster than your
 * best", "1 round fewer than your best". Null without a best, or for a run that was not finished.
 */
export function resultGap(
  result: TimerSession,
  before: TimerSession | null,
): { text: string; better: boolean } | null {
  if (!before || timerOutcome(result) !== 'completed') return null
  if (result.config.mode === 'forTime') {
    const diff = result.seconds - before.seconds
    if (diff === 0) return { text: 'Same time as your best', better: false }
    return { text: `${holdText(Math.abs(diff))} ${diff < 0 ? 'faster' : 'slower'} than your best`, better: diff < 0 }
  }
  if (result.config.mode !== 'amrap') return null
  const diff = result.rounds - before.rounds
  if (diff === 0) return { text: 'As many rounds as your best', better: false }
  const count = Math.abs(diff)
  return {
    text: `${count} ${count === 1 ? 'round' : 'rounds'} ${diff > 0 ? 'more' : 'fewer'} than your best`,
    better: diff > 0,
  }
}
