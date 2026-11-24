import { describe, expect, it } from 'vitest'
import {
  BOXING_TEMPLATES,
  boxingCues,
  boxingRecordings,
  boxingView,
  comboCount,
  copiedToAll,
  isAgain,
  soundCombo,
  withRound,
  type BoxingRound,
} from './boxing'
import {
  callSpan,
  callsText,
  checkedOf,
  comboGroups,
  combosCalled,
  combosText,
  copyConfig,
  drillOf,
  isTemplate,
  pickOf,
  pickText,
  planOf,
  planTotal,
  roundCalls,
  roundDetail,
  runSeed,
  sameConfig,
  themeExample,
  timeline,
  withRoundAction,
  withRoundChange,
  withRounds,
  withStyle,
  withTemplate,
} from './boxingScreens'
import { DEFAULT_TIMER_CALLS, DEFAULT_TIMERS, timerConfig, type TimerConfig } from './timer'
import { restoredRun } from './timerScreens'

const round = (theme: BoxingRound['theme'], every = 5, split = 0): BoxingRound => ({ theme, every, split, pick: [] })
const boxing = (rounds: BoxingRound[], style: 'names' | 'numbers' = 'names'): TimerConfig =>
  timerConfig({ mode: 'boxing', work: 180, rest: 60, rounds: rounds.length, boxing: { style, rounds } })
const themes = (config: TimerConfig) => planOf(config).rounds.map((entry) => entry.theme)

describe('boxing setup', () => {
  it('keeps the rounds that fall off when the count goes down, and brings them back when it goes up', () => {
    const config = boxing([round('jab'), round('two'), round('three'), round('four'), round('body', 7, 30), round('freestyle')])
    const four = withRounds(config, 4)
    expect(four.config.rounds).toBe(4)
    expect(themes(four.config)).toEqual(['jab', 'two', 'three', 'four'])
    expect(four.kept.map((entry) => entry.theme)).toEqual(['body', 'freestyle'])
    expect(timerConfig(four.config)).toEqual(four.config)
    const back = withRounds(four.config, 6, four.kept)
    expect(planOf(back.config).rounds).toEqual(planOf(config).rounds)
    expect(back.kept).toEqual([])
    // Only beyond what was ever there is the last round repeated.
    const more = withRounds(four.config, 8, four.kept)
    expect(themes(more.config)).toEqual(['jab', 'two', 'three', 'four', 'body', 'freestyle', 'freestyle', 'freestyle'])
    expect(themes(withRounds(config, 7).config).at(-1)).toBe('freestyle')
  })

  it('lays a template over the rounds there are, keeps the style, and the old plan comes back on undo', () => {
    const config = boxing([round('jab'), round('two'), round('three'), round('mix'), round('mix'), round('mix')], 'numbers')
    const before = copyConfig(config)
    const ladder = withTemplate(config, BOXING_TEMPLATES[0].rounds)
    // A ladder of four on six rounds climbs again.
    expect(themes(ladder)).toEqual(['jab', 'two', 'three', 'four', 'jab', 'two'])
    expect(planOf(ladder).style).toBe('numbers')
    expect(isTemplate(ladder, BOXING_TEMPLATES[0].rounds)).toBe(true)
    const power = withTemplate(config, BOXING_TEMPLATES[1].rounds)
    expect(planOf(power).rounds).toEqual(Array.from({ length: 6 }, () => round('mix', 6, 30)))
    expect(isTemplate(power, BOXING_TEMPLATES[0].rounds)).toBe(false)
    // Undo puts back the copy taken before.
    expect(sameConfig(before, config)).toBe(true)
    expect(sameConfig(power, config)).toBe(false)
  })

  it('copies a round to all, edits one, and switches the style', () => {
    const config = boxing([round('jab'), round('two'), round('three')])
    const copied = { ...config, boxing: copiedToAll(planOf(config), 1) }
    expect(themes(copied)).toEqual(['two', 'two', 'two'])
    const edited = { ...config, boxing: withRound(planOf(config), 2, round('defense', 9, 20)) }
    expect(planOf(edited).rounds[2]).toEqual(round('defense', 9, 20))
    expect(planOf(withStyle(config, 'numbers')).style).toBe('numbers')
    expect(planOf(withStyle(config, 'numbers')).rounds).toEqual(planOf(config).rounds)
  })

  it('picks exact combinations, where all and none both mean all, and a new theme clears them', () => {
    const config = boxing([round('three'), round('mix')])
    const three = planOf(config).rounds[0]
    expect(pickText(three)).toBe('Combinations: all 7')
    // Nothing picked: every one is checked, and called.
    expect(checkedOf(three)).toHaveLength(7)
    const two = ['jab-cross-hook', 'jab-jab-cross']
    // Picks come back in the order of the list, whatever order they were checked in.
    expect(pickOf('three', ['jab-jab-cross', 'jab-cross-hook'])).toEqual(two)
    const picked = withRoundChange(config, 0, { pick: pickOf('three', two) })
    expect(pickText(planOf(picked).rounds[0])).toBe('Combinations: 2 of 7')
    expect(checkedOf(planOf(picked).rounds[0])).toEqual(two)
    // None checked and all checked both mean all.
    expect(pickOf('three', [])).toEqual([])
    expect(pickOf('three', checkedOf(three))).toEqual([])
    // Only the picked ones are called.
    const cues = boxingCues(picked, DEFAULT_TIMER_CALLS, 5).filter((cue) => cue.at < 10_000 + 180_000)
    const called = new Set(cues.map((cue) => soundCombo(cue.sound)?.id).filter(Boolean))
    expect([...called].sort()).toEqual([...two].sort())
    expect(planOf(withRoundChange(picked, 0, { theme: 'body' })).rounds[0].pick).toEqual([])
    expect(planOf(withRoundChange(picked, 0, { every: 4 })).rounds[0].pick).toEqual(two)
    // The mix is listed by theme, every combination once.
    const groups = comboGroups('mix')
    expect(groups.map((group) => group.theme)).toEqual(['jab', 'two', 'three', 'four', 'body', 'hooks', 'defense'])
    expect(groups.flatMap((group) => group.combos)).toHaveLength(43)
    expect(comboGroups('two')[0].combos).toHaveLength(6)
  })

  it('moves, duplicates and removes a round, and the timer has as many rounds as the plan', () => {
    const config = boxing([round('jab'), round('two'), round('three')])
    const earlier = withRoundAction(config, 1, 'earlier')
    expect([themes(earlier.config), earlier.index]).toEqual([['two', 'jab', 'three'], 0])
    expect(withRoundAction(config, 0, 'earlier')).toEqual({ config, index: 0 })
    const later = withRoundAction(config, 1, 'later')
    expect([themes(later.config), later.index]).toEqual([['jab', 'three', 'two'], 2])
    const copy = withRoundAction(config, 0, 'duplicate')
    expect([themes(copy.config), copy.index, copy.config.rounds]).toEqual([['jab', 'jab', 'two', 'three'], 1, 4])
    const removed = withRoundAction(config, 2, 'remove')
    expect([themes(removed.config), removed.index, removed.config.rounds]).toEqual([['jab', 'two'], 1, 2])
    const single = boxing([round('jab')])
    expect(withRoundAction(single, 0, 'remove').config).toBe(single)
    const full = boxing(Array.from({ length: 15 }, () => round('jab')))
    expect(withRoundAction(full, 3, 'duplicate').config).toBe(full)
    expect(timerConfig(copy.config)).toEqual(copy.config)
  })

  it('copies a config so the setup never edits the saved one', () => {
    const saved = DEFAULT_TIMERS.boxing
    const copy = copyConfig(saved)
    copy.boxing!.rounds[0].theme = 'body'
    copy.boxing!.rounds[1].pick.push('jab-cross')
    expect(saved.boxing!.rounds[0].theme).toBe('jab')
    expect(saved.boxing!.rounds[1].pick).toEqual([])
    expect(copyConfig(DEFAULT_TIMERS.tabata)).toEqual(DEFAULT_TIMERS.tabata)
  })

  it('describes rounds, themes and the plan', () => {
    expect(roundDetail(round('jab', 4))).toBe('Fast')
    expect(roundDetail(round('mix', 6, 30))).toBe('Steady · speed and power 30s')
    expect(roundDetail({ ...round('three', 8), pick: ['jab-cross-hook', 'jab-jab-cross'] })).toBe('Easy · 2 picked')
    expect(roundDetail(round('freestyle'))).toBe('no calls')
    expect(themeExample('three', 'names')).toBe('Jab · Cross · Hook')
    expect(themeExample('jab', 'numbers')).toBe('1 – 1')
    expect(themeExample('mix', 'numbers')).toBe('Any combination')
    expect(themeExample('freestyle', 'names')).toBe('No calls')
    expect(planTotal(DEFAULT_TIMERS.boxing)).toBe('6 rounds · 23:00 total')
    expect(callsText(28, 180)).toBe('About 28 calls this round, one every 6s or so')
    expect(callsText(1, 30)).toBe('One call this round')
  })

  it('counts the calls of one round from the schedule', () => {
    const config = DEFAULT_TIMERS.boxing
    const counts = config.boxing!.rounds.map((_, index) => roundCalls(config, index, DEFAULT_TIMER_CALLS))
    expect(counts.reduce((sum, count) => sum + count, 0)).toBe(comboCount(boxingCues(config, DEFAULT_TIMER_CALLS, 1)))
    expect(counts[5]).toBe(0)
    expect(counts[0]).toBeGreaterThan(counts[3])
  })

  it('draws the workout to scale, with the speed and power stretches of a split round', () => {
    const config = { ...boxing([round('jab'), round('mix', 5, 60)]), work: 150, rest: 30 }
    const blocks = timeline(config)
    expect(blocks.map((block) => `${block.kind}:${block.seconds}`)).toEqual(['work:150', 'rest:30', 'work:150'])
    expect(blocks[0]).toMatchObject({ color: '#C8F031', stretches: [] })
    expect(blocks[2].stretches).toEqual([
      { focus: 'speed', share: 0.4 },
      { focus: 'power', share: 0.4 },
      { focus: 'speed', share: 0.2 },
    ])
    expect(timeline(boxing([round('jab')]))).toHaveLength(1)
  })
})

describe('boxing run', () => {
  // The Android app shares this directory and has lines this app does not know.
  it('has a recording for every line of the coach', () => {
    const files = Object.keys(import.meta.glob('../assets/voice/timer/box-*.mp3'))
      .map((path) => path.slice(path.lastIndexOf('/') + 1, -'.mp3'.length))
    expect(Object.keys(boxingRecordings()).filter((name) => !files.includes(name))).toEqual([])
  })

  it('gives a boxing run a seed, a new one every start, and none to other timers', () => {
    const numbers = [0.25, 0.75]
    expect(runSeed(DEFAULT_TIMERS.boxing, () => numbers.shift()!)).toBe(2 ** 29)
    expect(runSeed(DEFAULT_TIMERS.boxing, () => numbers.shift()!)).toBe(3 * 2 ** 29)
    const seeds = new Set(Array.from({ length: 20 }, () => runSeed(DEFAULT_TIMERS.boxing)))
    expect(seeds.size).toBeGreaterThan(1)
    for (const seed of seeds) expect(Number.isInteger(seed) && seed! >= 0 && seed! < 2 ** 31).toBe(true)
    expect(runSeed(DEFAULT_TIMERS.tabata)).toBeUndefined()
  })

  it('keeps the seed of a run restored after a reload, so the same combinations are called', () => {
    const run = {
      id: 'r',
      name: 'Boxing',
      config: DEFAULT_TIMERS.boxing,
      calls: DEFAULT_TIMER_CALLS,
      startedAt: 1000,
      pausedAt: null,
      paused: 0,
      taps: 0,
      seed: 123456,
    }
    const restored = restoredRun(JSON.parse(JSON.stringify(run)))
    expect(restored?.seed).toBe(123456)
    expect(boxingCues(restored!.config, restored!.calls, restored!.seed!)).toEqual(
      boxingCues(run.config, run.calls, run.seed),
    )
    // Without a seed it gets one; another timer gets none.
    const { seed: _, ...seedless } = run
    expect(Number.isInteger(restoredRun(seedless, () => 0.5)?.seed)).toBe(true)
    expect(restoredRun({ ...run, config: DEFAULT_TIMERS.tabata })?.seed).toBeUndefined()
    expect(restoredRun({ ...run, config: { ...run.config, mode: 'karate' } })).toBeNull()
  })

  it('counts the combinations called up to where the run ended', () => {
    const config = DEFAULT_TIMERS.boxing
    const cues = boxingCues(config, DEFAULT_TIMER_CALLS, 1)
    expect(combosCalled(cues, Infinity)).toBe(139)
    expect(combosCalled(cues, 10_000 + 5000)).toBe(0)
    const firstRound = combosCalled(cues, 10_000 + 180_000)
    expect(firstRound).toBe(roundCalls(config, 0, DEFAULT_TIMER_CALLS))
    expect(combosText(159)).toBe('159 combinations called')
    expect(combosText(1)).toBe('One combination called')
  })

  it('fills the bar to the next thing to throw: a combination or an "Again!", and the end of the round after the last', () => {
    const config = DEFAULT_TIMERS.boxing
    const cues = boxingCues(config, DEFAULT_TIMER_CALLS, 1)
    const combos = cues.filter((cue) => soundCombo(cue.sound))
    // Round one: jabs, too short to be drilled.
    const start = 10_000
    const end = start + 180_000
    expect(callSpan(cues, start + 3000, start, end)).toBeNull()
    const at = combos[1].at + 100
    expect(callSpan(cues, at, start, end)).toEqual({ from: combos[1].at, to: combos[2].at, call: combos[1].at })
    // What the bar times is the combination on screen.
    expect(boxingView(config, cues, at).combo?.id).toBe(soundCombo(combos[1].sound)?.id)
    const last = combos.filter((cue) => cue.at < end).at(-1)!
    expect(callSpan(cues, last.at + 10, start, end)).toEqual({ from: last.at, to: end, call: last.at })
    // Round three: 3-punch combinations, each thrown again before the next is called.
    const third = start + 2 * 240_000
    const call = combos.find((cue) => cue.at >= third)!
    const again = cues.find((cue) => cue.at > call.at && isAgain(cue.sound))!
    const after = cues.find((cue) => cue.at > again.at && (isAgain(cue.sound) || soundCombo(cue.sound)))!
    expect(callSpan(cues, call.at + 100, third, third + 180_000)).toEqual({ from: call.at, to: again.at, call: call.at })
    expect(callSpan(cues, again.at + 100, third, third + 180_000)).toEqual({ from: again.at, to: after.at, call: call.at })
  })

  it('says how many combinations a round calls, and that long ones are drilled', () => {
    expect(drillOf(round('three'))).toBe('all')
    expect(drillOf(round('mix'))).toBe('long')
    expect(drillOf(round('jab'))).toBe('none')
    expect(drillOf(round('freestyle'))).toBe('none')
    expect(drillOf({ ...round('mix'), pick: ['jab', 'jab-cross'] })).toBe('none')
    expect(callsText(18, 180, 'all')).toBe('About 18 combinations this round, each thrown 2 or 3 times')
    expect(callsText(22, 180, 'long')).toBe('About 22 combinations this round, long ones thrown 2 or 3 times')
    expect(callsText(28, 180)).toBe('About 28 calls this round, one every 6s or so')
  })
})
