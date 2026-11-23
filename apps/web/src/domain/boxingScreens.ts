import {
  BOXING_COMBOS,
  BOXING_THEMES,
  boxingCues,
  boxingPlan,
  comboCount,
  DRILL_FROM,
  duplicatedRound,
  isAgain,
  movedRound,
  roundCombos,
  roundSummary,
  soundCombo,
  templatePlan,
  THEME_COLORS,
  THEME_EXAMPLES,
  THEME_NAMES,
  themeCombos,
  withoutRound,
  withRound,
  type BoxingCombo,
  type BoxingFocus,
  type BoxingPlan,
  type BoxingRound,
  type BoxingStyle,
  type BoxingTheme,
} from './boxing'
import { holdText } from './program'
import {
  TIMER_LEAD_IN_SECONDS,
  timerConfig,
  timerSeconds,
  type TimerCalls,
  type TimerConfig,
  type TimerCue,
} from './timer'

/** The plan of a boxing config, one round per round of the timer. */
export function planOf(config: TimerConfig): BoxingPlan {
  return boxingPlan(config.boxing, config.rounds)
}

/** A copy that shares nothing with the config it came from, so a setup can edit it freely. */
export function copyConfig(config: TimerConfig): TimerConfig {
  const { boxing, ...numbers } = config
  if (!boxing) return { ...numbers }
  const rounds = boxing.rounds.map((round) => ({ ...round, pick: [...(round.pick ?? [])] }))
  return { ...numbers, boxing: { style: boxing.style, rounds } }
}

/**
 * Another number of rounds. Rounds that fall off the end are `kept`, and come back when the
 * number goes up again; only beyond them is the last round repeated.
 */
export function withRounds(
  config: TimerConfig,
  rounds: number,
  kept: BoxingRound[] = [],
): { config: TimerConfig; kept: BoxingRound[] } {
  const plan = planOf(config)
  const all = [...plan.rounds, ...kept]
  return {
    config: { ...config, rounds, boxing: boxingPlan({ ...plan, rounds: all.slice(0, rounds) }, rounds) },
    kept: all.slice(rounds),
  }
}

/** A template over the rounds the timer has. The way the coach calls stays as it was. */
export function withTemplate(config: TimerConfig, rounds: BoxingRound[]): TimerConfig {
  return { ...config, boxing: templatePlan(rounds, config.rounds, planOf(config).style) }
}

/** True when the plan is what the template gives for this many rounds. */
export function isTemplate(config: TimerConfig, rounds: BoxingRound[]): boolean {
  const plan = planOf(config)
  return JSON.stringify(plan.rounds) === JSON.stringify(templatePlan(rounds, config.rounds, plan.style).rounds)
}

/** Round `index` changed: a new theme starts with all of its combinations. */
export function withRoundChange(config: TimerConfig, index: number, change: Partial<BoxingRound>): TimerConfig {
  const plan = planOf(config)
  const round = plan.rounds[index]
  const pick = change.theme !== undefined && change.theme !== round.theme ? [] : (change.pick ?? round.pick)
  return { ...config, boxing: withRound(plan, index, { ...round, ...change, pick }) }
}

export type RoundAction = 'earlier' | 'later' | 'duplicate' | 'remove'

/**
 * A round moved, copied or taken out, and the round the sheet shows afterwards: the same round
 * where it went, the copy, or the one that took the removed one's place. The timer's number of
 * rounds follows the plan.
 */
export function withRoundAction(config: TimerConfig, index: number, action: RoundAction): { config: TimerConfig; index: number } {
  const plan = planOf(config)
  const next =
    action === 'earlier'
      ? movedRound(plan, index, -1)
      : action === 'later'
        ? movedRound(plan, index, 1)
        : action === 'duplicate'
          ? duplicatedRound(plan, index)
          : withoutRound(plan, index)
  if (next === plan) return { config, index }
  const moved = { earlier: index - 1, later: index + 1, duplicate: index + 1, remove: Math.min(index, next.rounds.length - 1) }
  return { config: { ...config, rounds: next.rounds.length, boxing: next }, index: moved[action] }
}

/** "6 rounds · 22:00 total". */
export function planTotal(config: TimerConfig): string {
  const rounds = `${config.rounds} ${config.rounds === 1 ? 'round' : 'rounds'}`
  return `${rounds} · ${holdText(timerSeconds(config) ?? 0)} total`
}

/** The combinations to pick from, in groups: one group for a theme, one per theme for the mix. */
export function comboGroups(theme: BoxingTheme): { theme: BoxingTheme; combos: BoxingCombo[] }[] {
  if (theme !== 'mix') return [{ theme, combos: themeCombos(theme) }]
  return BOXING_THEMES.filter((entry) => entry !== 'mix' && entry !== 'freestyle').map((entry) => ({
    theme: entry,
    combos: BOXING_COMBOS.filter((combo) => combo.themes[0] === entry),
  }))
}

/** "Combinations: all 7", "Combinations: 2 of 7". */
export function pickText(round: BoxingRound): string {
  const all = themeCombos(round.theme).length
  return round.pick.length === 0 ? `Combinations: all ${all}` : `Combinations: ${round.pick.length} of ${all}`
}

/** The combinations shown as checked in the list: all of them while none is picked. */
export function checkedOf(round: BoxingRound): string[] {
  return round.pick.length > 0 ? round.pick : themeCombos(round.theme).map((combo) => combo.id)
}

/** The picks a set of checked combinations stands for, in list order. None or all checked means all: no picks. */
export function pickOf(theme: BoxingTheme, checked: string[]): string[] {
  const all = themeCombos(theme).map((combo) => combo.id)
  const pick = all.filter((id) => checked.includes(id))
  return pick.length === all.length ? [] : pick
}

export function withStyle(config: TimerConfig, style: BoxingStyle): TimerConfig {
  return { ...config, boxing: { ...planOf(config), style } }
}

/** The plan and numbers of two configs are the same once both are in their limits. */
export function sameConfig(a: TimerConfig, b: TimerConfig): boolean {
  return JSON.stringify(timerConfig(a)) === JSON.stringify(timerConfig(b))
}

/** A round's line under its theme name: `roundSummary` without the theme. "Fast · 2 picked", "no calls". */
export function roundDetail(round: BoxingRound): string {
  if (round.theme === 'freestyle') return 'no calls'
  return roundSummary(round).slice(THEME_NAMES[round.theme].length + ' · '.length)
}

/** What a theme's tile shows: "Double jab" or "1 – 1", "Any combination" for the mix, "No calls" for freestyle. */
export function themeExample(theme: BoxingTheme, style: BoxingStyle): string {
  if (theme === 'mix') return 'Any combination'
  const combo = BOXING_COMBOS.find((entry) => entry.id === THEME_EXAMPLES[theme])
  if (!combo) return 'No calls'
  return style === 'numbers' ? combo.numbers : combo.names
}

/** "6 combinations", "43 combinations", "No combinations". */
export function comboTotal(theme: BoxingTheme): string {
  const count = themeCombos(theme).length
  if (count === 0) return 'No combinations'
  return `${count} ${count === 1 ? 'combination' : 'combinations'}`
}

/** The combination the setup shows as an example of each style. */
export const STYLE_EXAMPLE = BOXING_COMBOS.find((combo) => combo.id === 'jab-cross-hook')!

/** What the numbers mean, in one line. */
export const NUMBERS_KEY = '1 jab · 2 cross · 3 hook · 5 and 6 uppercut · B to the body'

/**
 * Combinations the coach calls in round `index` (0 for the first) of this config. The other lines
 * of the voice push combinations aside, so the count depends on what it says.
 */
export function roundCalls(config: TimerConfig, index: number, calls: TimerCalls): number {
  const normal = timerConfig(config)
  const start = (TIMER_LEAD_IN_SECONDS + index * (normal.work + normal.rest)) * 1000
  const end = start + normal.work * 1000
  const cues = boxingCues(normal, calls, 1)
  return comboCount(cues.filter((cue) => cue.at >= start && cue.at <= end))
}

/**
 * Which of a round's combinations the coach drills, "Again!" after the first go: all of them,
 * only the long ones, or none.
 */
export function drillOf(round: BoxingRound): 'all' | 'long' | 'none' {
  const pool = round.theme === 'freestyle' ? [] : roundCombos(round)
  const long = pool.filter((combo) => combo.say.split(',').length >= DRILL_FROM).length
  if (long === 0) return 'none'
  return long === pool.length ? 'all' : 'long'
}

/**
 * "About 28 calls this round, one every 6s or so", and with a drill "About 18 combinations this
 * round, each thrown 2 or 3 times" or "..., long ones thrown 2 or 3 times".
 */
export function callsText(count: number, work: number, drill: 'all' | 'long' | 'none' = 'none'): string {
  if (count === 0) return 'No calls this round'
  if (drill !== 'none') {
    const combos = count === 1 ? 'One combination this round' : `About ${count} combinations this round`
    return `${combos}, ${drill === 'all' ? 'each' : 'long ones'} thrown 2 or 3 times`
  }
  if (count === 1) return 'One call this round'
  return `About ${count} calls this round, one every ${Math.round(work / count)}s or so`
}

/** One block of the timeline, as a share of the whole workout. */
export interface TimelineBlock {
  kind: 'work' | 'rest'
  /** 0 for the first round. A rest belongs to the round before it. */
  index: number
  /** Seconds, which the strip draws to scale. */
  seconds: number
  color: string
  theme: BoxingTheme
  /** Speed and power stretches as shares of the round, speed first. Empty without a split. */
  stretches: { focus: BoxingFocus; share: number }[]
}

/** The whole workout as rounds and rests in order, for the strip that draws it to scale. */
export function timeline(config: TimerConfig): TimelineBlock[] {
  const plan = planOf(config)
  const blocks: TimelineBlock[] = []
  plan.rounds.forEach((round, index) => {
    const stretches: TimelineBlock['stretches'] = []
    if (round.split > 0) {
      for (let at = 0; at < config.work; at += round.split) {
        const share = Math.min(round.split, config.work - at) / config.work
        stretches.push({ focus: stretches.length % 2 === 0 ? 'speed' : 'power', share })
      }
    }
    blocks.push({ kind: 'work', index, seconds: config.work, color: THEME_COLORS[round.theme], theme: round.theme, stretches })
    if (index < plan.rounds.length - 1 && config.rest > 0) {
      blocks.push({ kind: 'rest', index, seconds: config.rest, color: '', theme: round.theme, stretches: [] })
    }
  })
  return blocks
}

/** "Up next · Round 3", for the rest before a round. */
export function upNext(roundNumber: number): string {
  return `Up next · Round ${roundNumber}`
}

/**
 * For the bar that fills until the coach says the next thing to throw: from the last combination or
 * "Again!" to the next one, in milliseconds after Start, or to the end of the round when none
 * comes. `call` is when the combination on screen was called. Null before the first call of the round.
 */
export function callSpan(
  cues: TimerCue[],
  active: number,
  roundStart: number,
  roundEnd: number,
): { from: number; to: number; call: number } | null {
  let from: number | null = null
  let call: number | null = null
  let to = roundEnd
  for (const cue of cues) {
    const combo = soundCombo(cue.sound) !== undefined
    if (cue.at < roundStart || cue.at >= roundEnd || !(combo || isAgain(cue.sound))) continue
    if (cue.at <= active) {
      from = cue.at
      if (combo) call = cue.at
    } else {
      to = cue.at
      break
    }
  }
  return from === null || call === null ? null : { from, to, call }
}

/** Combinations a run called up to `active` milliseconds after Start. */
export function combosCalled(cues: TimerCue[], active: number): number {
  return comboCount(cues.filter((cue) => cue.at <= active))
}

/** "159 combinations called", "One combination called". */
export function combosText(count: number): string {
  return count === 1 ? 'One combination called' : `${count} combinations called`
}

/** A random 31-bit seed for the combinations of a new boxing run. Other modes get none. */
export function runSeed(config: TimerConfig, random: () => number = Math.random): number | undefined {
  return config.mode === 'boxing' ? Math.floor(random() * 2 ** 31) : undefined
}
