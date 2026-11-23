import {
  TIMER_LEAD_IN_SECONDS,
  timerView,
  VOICE_AFTER_BEEP,
  type TimerCalls,
  type TimerConfig,
  type TimerCue,
} from './timer'

/**
 * A boxing workout: rounds on the timer, each with its own theme, and a coach who calls the
 * combinations. The round length, the rest and the number of rounds are the timer's
 * `work`, `rest` and `rounds`; what happens in each round is the {@link BoxingPlan}.
 */

/** What a round is about: which combinations the coach calls. */
export type BoxingTheme =
  | 'jab'
  | 'two'
  | 'three'
  | 'four'
  | 'body'
  | 'hooks'
  | 'defense'
  | 'mix'
  | 'freestyle'

export const BOXING_THEMES: BoxingTheme[] = [
  'jab',
  'two',
  'three',
  'four',
  'body',
  'hooks',
  'defense',
  'mix',
  'freestyle',
]

export const THEME_NAMES: Record<BoxingTheme, string> = {
  jab: 'Jab only',
  two: '2-punch combos',
  three: '3-punch combos',
  four: '4+ punch combos',
  body: 'Body shots',
  hooks: 'Hooks and uppercuts',
  defense: 'Defense',
  mix: 'Mix',
  freestyle: 'Freestyle',
}

/** One line under the name, for the picker. */
export const THEME_HINTS: Record<BoxingTheme, string> = {
  jab: 'Single, double and triple jabs, head and body',
  two: 'Jab-cross, jab-hook, cross-hook',
  three: 'Jab-cross-hook and its cousins',
  four: 'Long combinations to chain',
  body: 'Head and body, changing levels',
  hooks: 'Hooks and uppercuts in close',
  defense: 'Slip or roll, then answer',
  mix: 'The coach picks from every theme',
  freestyle: 'No calls: you box, the clock runs',
}

/** One colour per theme, the same in both apps: a round is recognised by it in the plan and while it runs. */
export const THEME_COLORS: Record<BoxingTheme, string> = {
  jab: '#C8F031',
  two: '#7EE081',
  three: '#FFD23F',
  four: '#FF8A3D',
  body: '#FF4D4D',
  hooks: '#C77DFF',
  defense: '#4FE0B5',
  mix: '#FF7AD9',
  freestyle: '#9DB4FF',
}

export interface BoxingRound {
  theme: BoxingTheme
  /** Seconds between two calls. */
  every: number
  /** Seconds a speed or a power stretch lasts; they take turns, speed first. 0 = no split. */
  split: number
  /** Ids of the only combinations of the theme to call. Empty = all of them. */
  pick: string[]
}

/** How the coach says a combination: "Jab, cross, hook" or "One, two, three". */
export type BoxingStyle = 'names' | 'numbers'

export interface BoxingPlan {
  /** One entry per round of the timer. */
  rounds: BoxingRound[]
  style: BoxingStyle
}

export const MIN_BOXING_EVERY = 3
export const MAX_BOXING_EVERY = 15
export const DEFAULT_BOXING_EVERY = 6
export const BOXING_SPLITS = [0, 15, 20, 30, 60]
export const MAX_BOXING_ROUNDS = 15
export const MIN_BOXING_ROUND_SECONDS = 30
export const MAX_BOXING_ROUND_SECONDS = 600
export const MIN_BOXING_REST_SECONDS = 10
export const MAX_BOXING_REST_SECONDS = 300

export const DEFAULT_BOXING_ROUND: BoxingRound = { theme: 'mix', every: DEFAULT_BOXING_EVERY, split: 0, pick: [] }

/** The paces the setup offers by name. `every` is free in the data; a pace is the nearest one. */
export const BOXING_PACES: { name: string; every: number }[] = [
  { name: 'Easy', every: 8 },
  { name: 'Steady', every: 6 },
  { name: 'Fast', every: 4 },
  { name: 'All out', every: 3 },
]

/** The named pace closest to a number of seconds. */
export function paceOf(every: number): string {
  return BOXING_PACES.reduce((best, pace) =>
    Math.abs(pace.every - every) < Math.abs(best.every - every) ? pace : best,
  ).name
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, Math.round(value)))
}

function boxingRound(round: Partial<BoxingRound> | undefined): BoxingRound {
  const theme = BOXING_THEMES.includes(round?.theme as BoxingTheme) ? (round?.theme as BoxingTheme) : 'mix'
  return {
    theme,
    every: clamp(round?.every ?? DEFAULT_BOXING_EVERY, MIN_BOXING_EVERY, MAX_BOXING_EVERY),
    // A freestyle round has no coach: nobody to call speed and power.
    split: theme !== 'freestyle' && BOXING_SPLITS.includes(round?.split ?? 0) ? (round?.split ?? 0) : 0,
    pick: (round?.pick ?? []).filter((id) => themeCombos(theme).some((combo) => combo.id === id)),
  }
}

/**
 * A plan with exactly `rounds` rounds, each within its limits. A round that is missing copies the
 * last one there is, so raising the number of rounds repeats what the lifter set up last.
 */
export function boxingPlan(plan: Partial<BoxingPlan> | undefined, rounds: number): BoxingPlan {
  const given = plan?.rounds ?? []
  const list: BoxingRound[] = []
  for (let index = 0; index < rounds; index += 1) {
    list.push(boxingRound(given[index] ?? given[given.length - 1]))
  }
  return { rounds: list, style: plan?.style === 'numbers' ? 'numbers' : 'names' }
}

/**
 * A combination the coach can call. `id` names its two recordings in `assets/voice/timer`:
 * `box-<id>-n` spoken with names and `box-<id>-d` with numbers.
 * Numbers: 1 jab, 2 cross, 3 lead hook, 4 rear hook, 5 lead uppercut, 6 rear uppercut; B = to the body.
 */
export interface BoxingCombo {
  id: string
  /** The themes it belongs to. Every combination is also part of the mix. */
  themes: BoxingTheme[]
  /** As shown: "Jab · Cross · Hook". */
  names: string
  /** As shown: "1 – 2 – 3". */
  numbers: string
  /** As spoken with names. */
  say: string
  /** As spoken with numbers. */
  sayNumbers: string
}

type Row = [id: string, themes: BoxingTheme[], names: string, numbers: string, say: string, sayNumbers: string]

const ROWS: Row[] = [
  ['jab', ['jab'], 'Jab', '1', 'Jab.', 'One.'],
  ['double-jab', ['jab'], 'Double jab', '1 – 1', 'Double jab.', 'One, one.'],
  ['triple-jab', ['jab'], 'Triple jab', '1 – 1 – 1', 'Triple jab.', 'One, one, one.'],
  ['jab-body', ['jab', 'body'], 'Jab to the body', '1B', 'Jab to the body.', 'One to the body.'],
  ['jab-jab-body', ['jab', 'body'], 'Jab · Jab to the body', '1 – 1B', 'Jab, jab to the body.', 'One, one to the body.'],
  ['jab-body-jab', ['jab', 'body'], 'Jab to the body · Jab', '1B – 1', 'Jab to the body, jab to the head.', 'One to the body, one to the head.'],

  ['jab-cross', ['two'], 'Jab · Cross', '1 – 2', 'Jab, cross.', 'One, two.'],
  ['jab-hook', ['two'], 'Jab · Hook', '1 – 3', 'Jab, hook.', 'One, three.'],
  ['cross-hook', ['two'], 'Cross · Hook', '2 – 3', 'Cross, hook.', 'Two, three.'],
  ['hook-cross', ['two'], 'Hook · Cross', '3 – 2', 'Hook, cross.', 'Three, two.'],
  ['cross-uppercut', ['two'], 'Cross · Uppercut', '2 – 5', 'Cross, uppercut.', 'Two, five.'],
  ['jab-uppercut', ['two'], 'Jab · Uppercut', '1 – 6', 'Jab, uppercut.', 'One, six.'],

  ['jab-cross-hook', ['three'], 'Jab · Cross · Hook', '1 – 2 – 3', 'Jab, cross, hook.', 'One, two, three.'],
  ['jab-jab-cross', ['three'], 'Jab · Jab · Cross', '1 – 1 – 2', 'Jab, jab, cross.', 'One, one, two.'],
  ['cross-hook-cross', ['three'], 'Cross · Hook · Cross', '2 – 3 – 2', 'Cross, hook, cross.', 'Two, three, two.'],
  ['hook-cross-hook', ['three'], 'Hook · Cross · Hook', '3 – 2 – 3', 'Hook, cross, hook.', 'Three, two, three.'],
  ['jab-hook-cross', ['three'], 'Jab · Hook · Cross', '1 – 3 – 2', 'Jab, hook, cross.', 'One, three, two.'],
  ['jab-cross-uppercut', ['three'], 'Jab · Cross · Uppercut', '1 – 2 – 5', 'Jab, cross, uppercut.', 'One, two, five.'],
  ['jab-cross-jab', ['three'], 'Jab · Cross · Jab', '1 – 2 – 1', 'Jab, cross, jab.', 'One, two, one.'],

  ['jab-cross-hook-cross', ['four'], 'Jab · Cross · Hook · Cross', '1 – 2 – 3 – 2', 'Jab, cross, hook, cross.', 'One, two, three, two.'],
  ['jab-jab-cross-hook', ['four'], 'Jab · Jab · Cross · Hook', '1 – 1 – 2 – 3', 'Jab, jab, cross, hook.', 'One, one, two, three.'],
  ['jab-cross-uppercut-cross', ['four'], 'Jab · Cross · Uppercut · Cross', '1 – 2 – 5 – 2', 'Jab, cross, uppercut, cross.', 'One, two, five, two.'],
  ['cross-hook-cross-hook', ['four'], 'Cross · Hook · Cross · Hook', '2 – 3 – 2 – 3', 'Cross, hook, cross, hook.', 'Two, three, two, three.'],
  ['jab-uppercut-hook-cross', ['four'], 'Jab · Uppercut · Hook · Cross', '1 – 6 – 3 – 2', 'Jab, uppercut, hook, cross.', 'One, six, three, two.'],
  ['five', ['four'], 'Jab · Cross · Hook · Cross · Jab', '1 – 2 – 3 – 2 – 1', 'Jab, cross, hook, cross, jab.', 'One, two, three, two, one.'],
  ['six', ['four'], 'Jab · Cross · Hook · Uppercut · Hook · Cross', '1 – 2 – 3 – 6 – 3 – 2', 'Jab, cross, hook, uppercut, hook, cross.', 'One, two, three, six, three, two.'],

  ['cross-body', ['body'], 'Cross to the body', '2B', 'Cross to the body.', 'Two to the body.'],
  ['hook-body', ['body', 'hooks'], 'Hook to the body', '3B', 'Hook to the body.', 'Three to the body.'],
  ['jab-cross-body', ['body'], 'Jab · Cross to the body', '1 – 2B', 'Jab, cross to the body.', 'One, two to the body.'],
  ['jab-body-cross', ['body'], 'Jab to the body · Cross', '1B – 2', 'Jab to the body, cross to the head.', 'One to the body, two to the head.'],
  ['jab-cross-hook-body', ['body'], 'Jab · Cross · Hook to the body', '1 – 2 – 3B', 'Jab, cross, hook to the body.', 'One, two, three to the body.'],
  ['hook-body-hook', ['body', 'hooks'], 'Hook to the body · Hook', '3B – 3', 'Hook to the body, hook to the head.', 'Three to the body, three to the head.'],

  ['hook', ['hooks'], 'Hook', '3', 'Hook.', 'Three.'],
  ['double-hook', ['hooks'], 'Double hook', '3 – 3', 'Double hook.', 'Three, three.'],
  ['uppercut', ['hooks'], 'Uppercut', '6', 'Uppercut.', 'Six.'],
  ['uppercut-hook', ['hooks'], 'Uppercut · Hook', '6 – 3', 'Uppercut, hook.', 'Six, three.'],
  ['hook-uppercut-hook', ['hooks'], 'Hook · Uppercut · Hook', '3 – 6 – 3', 'Hook, uppercut, hook.', 'Three, six, three.'],

  ['slip-cross', ['defense'], 'Slip · Cross', 'Slip – 2', 'Slip, cross.', 'Slip, two.'],
  ['slip-slip-cross', ['defense'], 'Slip · Slip · Cross', 'Slip – Slip – 2', 'Slip, slip, cross.', 'Slip, slip, two.'],
  ['roll-hook', ['defense'], 'Roll · Hook', 'Roll – 3', 'Roll, hook.', 'Roll, three.'],
  ['jab-slip-cross', ['defense'], 'Jab · Slip · Cross', '1 – Slip – 2', 'Jab, slip, cross.', 'One, slip, two.'],
  ['jab-cross-roll-cross', ['defense'], 'Jab · Cross · Roll · Cross', '1 – 2 – Roll – 2', 'Jab, cross, roll, cross.', 'One, two, roll, two.'],
  ['step-back-jab-cross', ['defense'], 'Step back · Jab · Cross', 'Back – 1 – 2', 'Step back, jab, cross.', 'Step back, one, two.'],
]

export const BOXING_COMBOS: BoxingCombo[] = ROWS.map(([id, themes, names, numbers, say, sayNumbers]) => ({
  id,
  themes,
  names,
  numbers,
  say,
  sayNumbers,
}))

/** The combinations a round of this theme draws from. Freestyle has none. */
export function themeCombos(theme: BoxingTheme): BoxingCombo[] {
  if (theme === 'freestyle') return []
  if (theme === 'mix') return BOXING_COMBOS
  return BOXING_COMBOS.filter((combo) => combo.themes.includes(theme))
}

/** What a round calls: the picked combinations of its theme, or all of them when none is picked. */
export function roundCombos(round: BoxingRound): BoxingCombo[] {
  const all = themeCombos(round.theme)
  const picked = all.filter((combo) => round.pick.includes(combo.id))
  return picked.length > 0 ? picked : all
}

/** The combination a theme's tile shows as its example. Null where there is nothing to show. */
export const THEME_EXAMPLES: Record<BoxingTheme, string | null> = {
  jab: 'double-jab',
  two: 'jab-cross',
  three: 'jab-cross-hook',
  four: 'jab-cross-hook-cross',
  body: 'jab-cross-body',
  hooks: 'uppercut-hook',
  defense: 'slip-cross',
  mix: null,
  freestyle: null,
}

/** What the coach says besides combinations: the file name without extension and its text. */
export const BOXING_LINES: Record<string, string> = {
  'box-theme-jab': 'Jab only.',
  'box-theme-two': 'Two punch combos.',
  'box-theme-three': 'Three punch combos.',
  'box-theme-four': 'Long combos.',
  'box-theme-body': 'Body shots.',
  'box-theme-hooks': 'Hooks and uppercuts.',
  'box-theme-defense': 'Defense.',
  'box-theme-mix': 'Mix it up.',
  'box-theme-freestyle': 'Freestyle.',
  'box-speed': 'Speed!',
  'box-power': 'Power!',
  'box-again': 'Again!',
  'box-one-more': 'One more!',
}

const COMBO_SOUND = /^box-(.+)-[nd]$/

/** The recording of a combination in a style. */
export function comboSound(combo: BoxingCombo, style: BoxingStyle): string {
  return `box-${combo.id}-${style === 'numbers' ? 'd' : 'n'}`
}

/** The combination a sound is the recording of, or undefined for any other sound. */
export function soundCombo(sound: string): BoxingCombo | undefined {
  const id = COMBO_SOUND.exec(sound)?.[1]
  return id === undefined ? undefined : BOXING_COMBOS.find((combo) => combo.id === id)
}

/**
 * Every recording the boxing coach needs, file name to spoken text.
 * The script that records the voice reads this, so the app and the recordings cannot drift apart.
 */
export function boxingRecordings(): Record<string, string> {
  const list: Record<string, string> = { ...BOXING_LINES }
  for (const combo of BOXING_COMBOS) {
    list[comboSound(combo, 'names')] = combo.say
    list[comboSound(combo, 'numbers')] = combo.sayNumbers
  }
  return list
}

const SECOND = 1000
/** How long after the round is called the coach says what it is about. */
const THEME_AFTER_ROUND = 1700
/** The first combination of a round comes this long after its start: the round and theme are said first. */
const FIRST_CALL = 6 * SECOND
/** No combination is called this close to the end of a round: the bell is coming. */
const LAST_CALL = 4 * SECOND
/** Room one spoken line needs before the next one starts. */
const VOICE_GAP = 2500
/** After a short combination is said, the lifter gets this long to throw it before the next call. */
const AFTER_CALL = 600
/** From this many pieces on a combination is drilled: thrown two or three times before the next one. */
export const DRILL_FROM = 3
/** How long "Again!" takes to say. */
const AGAIN_LENGTH = 1000

/** Milliseconds one go at a combination of this many pieces takes: half a second a punch and a breath. */
export function throwTime(pieces: number): number {
  return pieces * 500 + 500
}

/** True for the coach's "Again!" and "One more!": the combination called last is thrown once more. */
export function isAgain(sound: string): boolean {
  return sound === 'box-again' || sound === 'box-one-more'
}

/**
 * How long the coach takes to say a line, in milliseconds: an upper estimate from its parts and
 * words, a little above what the recordings measure. The next call waits for it, so a long
 * combination at a fast pace is never talked over.
 */
export function sayLength(text: string): number {
  const parts = text.split(',').length
  const words = text.replace(/[^a-zA-Z ]/g, '').trim().split(/\s+/).length
  return Math.max(parts * 1100, words * 460) + 400
}

/** Speed stretches call faster, and only the short combinations. Power keeps the round's pace. */
const SPEED_FACTOR = 0.75

/** Pieces a combination is said in: "Jab, cross, hook" has three. */
function parts(combo: BoxingCombo): number {
  return combo.say.split(',').length
}

/** The combinations for a speed stretch: the shortest of the round's and those one piece longer. */
export function speedCombos(pool: BoxingCombo[]): BoxingCombo[] {
  const shortest = Math.min(...pool.map(parts))
  return pool.filter((combo) => parts(combo) <= shortest + 1)
}

/**
 * A small random number generator with a seed (mulberry32), so a run can be worked out again
 * exactly: after a reload, in a test, and the same in both apps. Returns numbers from 0 up to 1.
 */
export function seededRandom(seed: number): () => number {
  let state = seed | 0
  return () => {
    state = (state + 0x6d2b79f5) | 0
    let value = Math.imul(state ^ (state >>> 15), 1 | state)
    value = (value + Math.imul(value ^ (value >>> 7), 61 | value)) ^ value
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296
  }
}

export type BoxingFocus = 'speed' | 'power'

/** The stretch a moment of a round is in, `into` milliseconds after the round started. Null without a split. */
export function focusAt(round: BoxingRound, into: number): BoxingFocus | null {
  if (round.split <= 0) return null
  return Math.floor(into / (round.split * SECOND)) % 2 === 0 ? 'speed' : 'power'
}

/**
 * Every sound of a boxing timer from Start to the end, in order: the beeps and the lines about
 * rounds and rest like any timer, what each round is about, speed and power where a round is
 * split, the last thirty and ten seconds of a round, and the combinations.
 * The same `seed` gives the same combinations. No combination is called twice in a row.
 */
export function boxingCues(config: TimerConfig, calls: TimerCalls, seed: number): TimerCue[] {
  const plan = boxingPlan(config.boxing, config.rounds)
  const lead = TIMER_LEAD_IN_SECONDS * SECOND
  const work = config.work * SECOND
  const rest = config.rest * SECOND
  const cues: TimerCue[] = []
  const beepInto = (at: number) => {
    if (!calls.beeps) return
    for (const before of [3, 2, 1]) cues.push({ at: at - before * SECOND, sound: 'beep' })
    cues.push({ at, sound: 'beep-long' })
  }
  if (calls.phases) cues.push({ at: 0, sound: 'get-ready' })

  for (let index = 0; index < config.rounds; index += 1) {
    const round = plan.rounds[index]
    const number = index + 1
    const start = lead + index * (work + rest)
    const end = start + work
    const last = number === config.rounds
    const spoken: number[] = []
    const say = (at: number, sound: string) => {
      cues.push({ at, sound })
      spoken.push(at)
    }

    beepInto(start)
    if (calls.phases) {
      const call = last && config.rounds > 1 ? 'last-round' : `round-${number}`
      say(start + VOICE_AFTER_BEEP, call)
      say(start + VOICE_AFTER_BEEP + THEME_AFTER_ROUND, `box-theme-${round.theme}`)
    }
    if (round.split > 0) {
      // The first stretch is announced after the theme, the others as they begin.
      for (let into = 0; into < work - LAST_CALL; into += round.split * SECOND) {
        const focus = focusAt(round, into)
        say(start + (into === 0 ? FIRST_CALL - 1500 : into), `box-${focus}`)
      }
    }
    // A time call that lands on "Speed" or "Power" waits until that one is over.
    const sayAfter = (at: number, sound: string) => {
      const clash = spoken.find((other) => Math.abs(other - at) < VOICE_GAP)
      say(clash === undefined ? at : clash + VOICE_GAP, sound)
    }
    if (calls.final) {
      if (work >= 90 * SECOND) sayAfter(end - 30 * SECOND, 'thirty-seconds')
      if (work >= 30 * SECOND) sayAfter(end - 10 * SECOND, 'ten-seconds')
    }

    const pool = round.theme === 'freestyle' ? [] : roundCombos(round)
    if (pool.length > 0) {
      const quick = speedCombos(pool)
      const random = seededRandom(seed + number * 7919)
      let previous = ''
      let into = round.split > 0 ? FIRST_CALL + 1000 : FIRST_CALL
      while (into <= work - LAST_CALL) {
        const from = focusAt(round, into) === 'speed' ? quick : pool
        let pick = Math.floor(random() * from.length)
        if (from[pick].id === previous && from.length > 1) pick = (pick + 1) % from.length
        const combo = from[pick]
        const length = sayLength(plan.style === 'numbers' ? combo.sayNumbers : combo.say)
        // A call never talks over another line: it waits until that one is over, and it is not
        // started when another line would begin before it is said.
        let at = start + into
        for (;;) {
          const clash = spoken.find((other) => other > at - VOICE_GAP && other < at + length)
          if (clash === undefined) break
          at = clash + VOICE_GAP
        }
        into = at - start
        if (into > work - LAST_CALL) break
        previous = combo.id
        cues.push({ at, sound: comboSound(combo, plan.style) })
        const pace = Math.round(round.every * (focusAt(round, into) === 'speed' ? SPEED_FACTOR : 1) * 2) * 500
        const pieces = parts(combo)
        // A long combination is drilled: said once, thrown, then "Again!" once or twice.
        let free = at + length + (pieces >= DRILL_FROM ? throwTime(pieces) : AFTER_CALL)
        if (pieces >= DRILL_FROM) {
          const goes = random() < 1 / 3 ? 3 : 2
          for (let go = 2; go <= goes; go += 1) {
            let again = free
            for (;;) {
              const clash = spoken.find((other) => other > again - VOICE_GAP && other < again + AGAIN_LENGTH + 500)
              if (clash === undefined) break
              again = clash + VOICE_GAP
            }
            if (again - start > work - LAST_CALL) break
            cues.push({ at: again, sound: go === 3 ? 'box-one-more' : 'box-again' })
            free = again + AGAIN_LENGTH + throwTime(pieces)
          }
        }
        into += Math.max(Math.ceil((free - at) / 500) * 500, pace)
      }
    }

    beepInto(end)
    if (calls.phases) cues.push({ at: end + VOICE_AFTER_BEEP, sound: last ? 'complete' : 'rest' })
  }
  return cues.sort((a, b) => a.at - b.at)
}

/** How many combinations a run calls. A combination that is drilled counts once. */
export function comboCount(cues: TimerCue[]): number {
  return cues.filter((cue) => soundCombo(cue.sound) !== undefined).length
}

/** What a boxing run shows at a moment: the round's plan, the stretch it is in and the combination to throw. */
export interface BoxingView {
  /** The plan of the round the timer is in, or of the one coming up during the lead-in and a rest. */
  round: BoxingRound
  /** Null without a split, during the lead-in and during a rest. */
  focus: BoxingFocus | null
  /** The combination called last in this round. Null before the first call and outside a round. */
  combo: BoxingCombo | null
  /** Which go at that combination this is, 1 for the first. 0 without a combination. */
  go: number
  /** How many goes the coach asks for: 1 for a short combination, 2 or 3 for one that is drilled. */
  goes: number
}

/**
 * Where a boxing run stands `active` milliseconds after Start, pauses not counted.
 * `cues` is what {@link boxingCues} gave for this run.
 */
export function boxingView(config: TimerConfig, cues: TimerCue[], active: number): BoxingView {
  const plan = boxingPlan(config.boxing, config.rounds)
  const view = timerView(config, active)
  const working = view.phase === 'work'
  // During a rest the round counted is the one just boxed: show the one that comes next.
  const index = view.phase === 'rest' ? view.round : view.round - 1
  const round = plan.rounds[Math.min(index, plan.rounds.length - 1)]
  if (!working) return { round, focus: null, combo: null, go: 0, goes: 0 }
  const start = active - view.phaseElapsed
  const end = start + (view.phaseTotal ?? 0)
  let combo: BoxingCombo | null = null
  let go = 0
  let goes = 0
  for (const cue of cues) {
    if (cue.at < start || cue.at >= end) continue
    const called = soundCombo(cue.sound)
    if (called) {
      if (cue.at > active) break
      combo = called
      go = 1
      goes = 1
    } else if (isAgain(cue.sound) && combo) {
      goes += 1
      if (cue.at <= active) go += 1
    }
  }
  return { round, focus: focusAt(round, view.phaseElapsed), combo, go, goes }
}

/** "Jab only · Fast", "Mix · Steady · speed and power 30s", "3-punch combos · Fast · 2 picked", "Freestyle". */
export function roundSummary(round: BoxingRound): string {
  if (round.theme === 'freestyle') return THEME_NAMES.freestyle
  const split = round.split > 0 ? ` · speed and power ${round.split}s` : ''
  const picked = round.pick.length > 0 ? ` · ${round.pick.length} picked` : ''
  return `${THEME_NAMES[round.theme]} · ${paceOf(round.every)}${split}${picked}`
}

/** A plan where round `index` is replaced. */
export function withRound(plan: BoxingPlan, index: number, round: BoxingRound): BoxingPlan {
  return { ...plan, rounds: plan.rounds.map((entry, at) => (at === index ? boxingRound(round) : entry)) }
}

/** A plan where every round is a copy of round `index`. */
export function copiedToAll(plan: BoxingPlan, index: number): BoxingPlan {
  const source = plan.rounds[index]
  return source ? { ...plan, rounds: plan.rounds.map(() => ({ ...source })) } : plan
}

/** A plan where round `index` swaps places with the one `by` rounds later (or earlier, negative). */
export function movedRound(plan: BoxingPlan, index: number, by: number): BoxingPlan {
  const to = index + by
  if (!plan.rounds[index] || !plan.rounds[to]) return plan
  const rounds = [...plan.rounds]
  ;[rounds[index], rounds[to]] = [rounds[to], rounds[index]]
  return { ...plan, rounds }
}

/** A plan with a copy of round `index` right after it. Unchanged at the most rounds a workout can have. */
export function duplicatedRound(plan: BoxingPlan, index: number): BoxingPlan {
  const source = plan.rounds[index]
  if (!source || plan.rounds.length >= MAX_BOXING_ROUNDS) return plan
  const rounds = [...plan.rounds]
  rounds.splice(index + 1, 0, { ...source, pick: [...source.pick] })
  return { ...plan, rounds }
}

/** A plan without round `index`. The last round there is cannot be removed. */
export function withoutRound(plan: BoxingPlan, index: number): BoxingPlan {
  if (!plan.rounds[index] || plan.rounds.length <= 1) return plan
  return { ...plan, rounds: plan.rounds.filter((_, at) => at !== index) }
}

/**
 * A template laid over a number of rounds: its rounds over and over, so a ladder of four on
 * eight rounds climbs twice. The style is the lifter's, not the template's.
 */
export function templatePlan(template: BoxingRound[], rounds: number, style: BoxingStyle): BoxingPlan {
  const list: BoxingRound[] = []
  for (let index = 0; index < rounds; index += 1) list.push(boxingRound(template[index % template.length]))
  return { rounds: list, style }
}

/** Workouts to start from. */
export const BOXING_TEMPLATES: { name: string; hint: string; rounds: BoxingRound[] }[] = [
  {
    name: 'Ladder',
    hint: 'One punch more every round',
    rounds: [
      { theme: 'jab', every: 4, split: 0, pick: [] },
      { theme: 'two', every: 4, split: 0, pick: [] },
      { theme: 'three', every: 6, split: 0, pick: [] },
      { theme: 'four', every: 6, split: 0, pick: [] },
    ],
  },
  {
    name: 'Speed and power',
    hint: '30 seconds fast, 30 seconds heavy',
    rounds: [{ theme: 'mix', every: 6, split: 30, pick: [] }],
  },
  {
    name: 'Head and body',
    hint: 'Change levels all the way',
    rounds: [
      { theme: 'jab', every: 4, split: 0, pick: [] },
      { theme: 'body', every: 6, split: 0, pick: [] },
      { theme: 'hooks', every: 6, split: 0, pick: [] },
    ],
  },
  {
    name: 'Classic',
    hint: 'Every round a mix',
    rounds: [{ theme: 'mix', every: 6, split: 0, pick: [] }],
  },
]
