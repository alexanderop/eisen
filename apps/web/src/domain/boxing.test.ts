import { describe, expect, it } from 'vitest'
import {
  BOXING_COMBOS,
  BOXING_TEMPLATES,
  BOXING_THEMES,
  boxingCues,
  boxingPlan,
  boxingRecordings,
  boxingView,
  DRILL_FROM,
  isAgain,
  throwTime,
  comboCount,
  comboSound,
  copiedToAll,
  focusAt,
  movedRound,
  duplicatedRound,
  paceOf,
  roundCombos,
  roundSummary,
  sayLength,
  seededRandom,
  soundCombo,
  speedCombos,
  templatePlan,
  themeCombos,
  withRound,
  withoutRound,
  type BoxingRound,
} from './boxing'
import {
  DEFAULT_TIMER_CALLS,
  DEFAULT_TIMERS,
  timerConfig,
  timerCues,
  timerSeconds,
  timerSummary,
  timerView,
  type TimerCalls,
  type TimerConfig,
} from './timer'

const round = (theme: BoxingRound['theme'], every = 4, split = 0): BoxingRound => ({ theme, every, split, pick: [] })
const short = (style: 'names' | 'numbers' = 'names'): TimerConfig =>
  timerConfig({
    mode: 'boxing',
    work: 30,
    rest: 10,
    rounds: 2,
    boxing: { style, rounds: [round('jab'), round('two', 5, 15)] },
  })
const QUIET: TimerCalls = { minutes: 0, halfway: false, final: false, phases: false, beeps: false }

/** "16:box-jab-n": seconds after Start and the sound, without the beeps. */
function spoken(config: TimerConfig, seed: number, calls: TimerCalls = DEFAULT_TIMER_CALLS): string {
  return boxingCues(config, calls, seed)
    .filter((cue) => !cue.sound.startsWith('beep'))
    .map((cue) => `${cue.at / 1000}:${cue.sound}`)
    .join(' ')
}

describe('boxing', () => {
  it('has combinations for every theme and a recording for each in both styles', () => {
    expect(BOXING_THEMES.map((theme) => `${theme}:${themeCombos(theme).length}`).join(' ')).toBe(
      'jab:6 two:6 three:7 four:7 body:9 hooks:7 defense:6 mix:43 freestyle:0',
    )
    expect(new Set(BOXING_COMBOS.map((combo) => combo.id)).size).toBe(43)
    const recordings = boxingRecordings()
    // 43 combinations twice, nine themes, speed and power, again and one more.
    expect(Object.keys(recordings)).toHaveLength(99)
    expect(recordings['box-jab-cross-hook-n']).toBe('Jab, cross, hook.')
    expect(recordings['box-jab-cross-hook-d']).toBe('One, two, three.')
    expect(recordings['box-theme-four']).toBe('Long combos.')
    for (const theme of BOXING_THEMES) expect(recordings[`box-theme-${theme}`]).toBeDefined()
  })

  it('names the recording of a combination and finds it again', () => {
    const combo = BOXING_COMBOS.find((entry) => entry.id === 'jab-cross')!
    expect(comboSound(combo, 'names')).toBe('box-jab-cross-n')
    expect(comboSound(combo, 'numbers')).toBe('box-jab-cross-d')
    expect(soundCombo('box-jab-cross-d')).toBe(combo)
    expect(soundCombo('box-theme-jab')).toBeUndefined()
    expect(soundCombo('box-speed')).toBeUndefined()
    expect(soundCombo('round-1')).toBeUndefined()
  })

  it('gives a plan one round per round of the timer, repeating the last one', () => {
    const plan = boxingPlan({ style: 'numbers', rounds: [round('jab'), round('body', 99, 7)] }, 4)
    expect(plan.style).toBe('numbers')
    expect(plan.rounds.map((entry) => entry.theme)).toEqual(['jab', 'body', 'body', 'body'])
    // Out of range comes back in: every 3 to 15, a split only of the offered lengths.
    expect(plan.rounds[1]).toEqual({ theme: 'body', every: 15, split: 0, pick: [] })
    expect(boxingPlan(plan, 1).rounds).toHaveLength(1)
    // A freestyle round cannot be split: there is no coach to call speed and power.
    expect(boxingPlan({ rounds: [round('freestyle', 5, 30)] }, 1).rounds[0].split).toBe(0)
    expect(boxingPlan(undefined, 2)).toEqual({
      style: 'names',
      rounds: [round('mix', 6), round('mix', 6)],
    })
  })

  it('is a timer of rounds with rest between them', () => {
    const config = timerConfig({ mode: 'boxing', work: 9999, rest: 0, rounds: 99 })
    expect(config).toMatchObject({ work: 600, rest: 10, rounds: 15 })
    expect(config.boxing?.rounds).toHaveLength(15)
    expect(timerSeconds(DEFAULT_TIMERS.boxing)).toBe(6 * 180 + 5 * 60)
    expect(timerSummary(DEFAULT_TIMERS.boxing)).toBe('6 × 3:00 / 1:00')
    expect(timerView(short(), 10000 + 35000)).toMatchObject({ phase: 'rest', round: 1, rounds: 2 })
    // The other modes carry no plan.
    expect('boxing' in timerConfig({ mode: 'tabata', work: 20, rest: 10, rounds: 8 })).toBe(false)
  })

  it('draws the same numbers from the same seed', () => {
    const a = seededRandom(42)
    const b = seededRandom(42)
    const first = [a(), a(), a()]
    expect([b(), b(), b()]).toEqual(first)
    expect(first.map((value) => Math.floor(value * 1000))).toEqual([601, 448, 852])
    expect(first.every((value) => value >= 0 && value < 1)).toBe(true)
  })

  it('calls the round, its theme, the combinations and the end of the round', () => {
    expect(spoken(short(), 1)).toBe(
      '0:get-ready 10.7:round-1 12.4:box-theme-jab 16:box-double-jab-n 20:box-jab-jab-body-n ' +
        '24:box-double-jab-n 28:box-triple-jab-n 30:ten-seconds 32.5:box-jab-jab-body-n 40.7:rest ' +
        '50.7:last-round 52.4:box-theme-two 54.5:box-speed 57:box-cross-hook-n 61:box-hook-cross-n ' +
        '65:box-power 70:ten-seconds 72.5:box-cross-uppercut-n 80.7:complete',
    )
  })

  it('says the same plan with numbers, and other combinations with another seed', () => {
    expect(spoken(short('numbers'), 2)).toBe(
      '0:get-ready 10.7:round-1 12.4:box-theme-jab 16:box-jab-d 20:box-triple-jab-d ' +
        '24.5:box-jab-body-d 30:ten-seconds 32.5:box-jab-jab-body-d 40.7:rest ' +
        '50.7:last-round 52.4:box-theme-two 54.5:box-speed 57:box-cross-uppercut-d 61:box-jab-cross-d ' +
        '65:box-power 70:ten-seconds 72.5:box-jab-uppercut-d 80.7:complete',
    )
    expect(timerCues(short(), DEFAULT_TIMER_CALLS, 1)).toEqual(boxingCues(short(), DEFAULT_TIMER_CALLS, 1))
  })

  it('keeps calling combinations when everything else is switched off, and none in a freestyle round', () => {
    const quiet = spoken(short(), 1, QUIET)
    expect(quiet.startsWith('16:box-double-jab-n 20:box-jab-jab-body-n')).toBe(true)
    expect(quiet).not.toContain('round-1')
    expect(quiet).toContain('box-speed')
    const free = timerConfig({ ...short(), boxing: { style: 'names', rounds: [round('freestyle'), round('freestyle')] } })
    expect(comboCount(boxingCues(free, DEFAULT_TIMER_CALLS, 1))).toBe(0)
    expect(spoken(free, 1)).toContain('12.4:box-theme-freestyle')
  })

  it('never calls the same combination twice in a row and never talks over another line', () => {
    for (const seed of [1, 2, 3, 99, 12345]) {
      const cues = boxingCues(DEFAULT_TIMERS.boxing, DEFAULT_TIMER_CALLS, seed)
      const voice = cues.filter((cue) => !cue.sound.startsWith('beep'))
      for (let index = 1; index < voice.length; index += 1) {
        const gap = voice[index].at - voice[index - 1].at
        expect(gap, `${voice[index - 1].sound} then ${voice[index].sound}`).toBeGreaterThanOrEqual(1500)
        if (soundCombo(voice[index].sound) && soundCombo(voice[index - 1].sound)) {
          expect(voice[index].sound).not.toBe(voice[index - 1].sound)
        }
      }
    }
    expect(comboCount(boxingCues(DEFAULT_TIMERS.boxing, DEFAULT_TIMER_CALLS, 1))).toBe(139)
  })

  it('says the time left after "Speed" or "Power" when both fall on the same moment', () => {
    const split = (work: number, every: number) =>
      timerConfig({ mode: 'boxing', work, rest: 10, rounds: 1, boxing: { style: 'names', rounds: [round('mix', 5, every)] } })
    // 3:00 in 30s stretches: "Power" at 2:30 into the round, which is also thirty seconds before its end.
    expect(spoken(split(180, 30), 1)).toContain('160:box-power 162.5:thirty-seconds')
    for (const every of [15, 20, 30, 60]) {
      for (let work = 30; work <= 600; work += 5) {
        const voice = boxingCues(split(work, every), DEFAULT_TIMER_CALLS, 1).filter((cue) => !cue.sound.startsWith('beep'))
        for (let index = 1; index < voice.length; index += 1) {
          const gap = voice[index].at - voice[index - 1].at
          expect(gap, `${every}s stretches, ${work}s: ${voice[index - 1].sound} then ${voice[index].sound}`).toBeGreaterThanOrEqual(1500)
        }
      }
    }
  })

  it('lets a long combination be said before the next one is called', () => {
    expect(sayLength('Jab.')).toBe(1500)
    expect(sayLength('Jab, cross.')).toBe(2600)
    expect(sayLength('Jab, cross, hook, cross, jab.')).toBe(5900)
    // Few parts but many words: the words decide.
    expect(sayLength('Jab to the body, cross to the head.')).toBe(4080)
    // Asked for a call every 3 seconds, long combinations still get their time.
    const fast = timerConfig({
      mode: 'boxing',
      work: 40,
      rest: 10,
      rounds: 1,
      boxing: { style: 'names', rounds: [round('four', 3)] },
    })
    expect(spoken(fast, 5)).toBe(
      '0:get-ready 10.7:round-1 12.4:box-theme-four 16:box-jab-jab-cross-hook-n 23.3:box-again ' +
        '26.8:box-one-more 30.5:box-jab-cross-uppercut-cross-n 37.8:box-again 40:ten-seconds ' +
        '42.5:box-cross-hook-cross-hook-n 50.7:complete',
    )
    for (const seed of [1, 2, 3]) {
      const calls = boxingCues({ ...fast, work: 180 }, QUIET, seed).filter((cue) => soundCombo(cue.sound))
      for (let index = 1; index < calls.length; index += 1) {
        const said = soundCombo(calls[index - 1].sound)!.say
        expect(calls[index].at - calls[index - 1].at).toBeGreaterThanOrEqual(sayLength(said) + 600)
      }
    }
  })

  it('calls faster in a speed stretch than in a power stretch', () => {
    const split = timerConfig({
      mode: 'boxing',
      work: 180,
      rest: 60,
      rounds: 1,
      boxing: { style: 'names', rounds: [round('mix', 6, 30)] },
    })
    const combos = boxingCues(split, QUIET, 7).filter((cue) => soundCombo(cue.sound))
    const inStretch = (from: number, to: number) =>
      combos.filter((cue) => cue.at >= 10000 + from * 1000 && cue.at < 10000 + to * 1000).length
    expect(inStretch(30, 60)).toBeLessThan(inStretch(60, 90))
    expect(focusAt(round('mix', 6, 30), 0)).toBe('speed')
    expect(focusAt(round('mix', 6, 30), 30000)).toBe('power')
    expect(focusAt(round('mix', 6, 30), 60000)).toBe('speed')
    expect(focusAt(round('mix', 6, 0), 30000)).toBeNull()
  })

  it('shows the round, the stretch and the combination called last', () => {
    const config = short()
    const cues = boxingCues(config, DEFAULT_TIMER_CALLS, 1)
    // The lead-in shows what round one will be.
    expect(boxingView(config, cues, 3000)).toEqual({ round: round('jab'), focus: null, combo: null, go: 0, goes: 0 })
    // Before the first call of a round there is nothing to throw yet.
    expect(boxingView(config, cues, 14000).combo).toBeNull()
    expect(boxingView(config, cues, 17000).combo?.id).toBe('double-jab')
    expect(boxingView(config, cues, 23000).combo?.id).toBe('jab-jab-body')
    // The rest shows the round that comes next.
    expect(boxingView(config, cues, 45000)).toEqual({ round: round('two', 5, 15), focus: null, combo: null, go: 0, goes: 0 })
    expect(boxingView(config, cues, 58000)).toMatchObject({ focus: 'speed' })
    expect(boxingView(config, cues, 58000).combo?.id).toBe('cross-hook')
    expect(boxingView(config, cues, 66000).focus).toBe('power')
  })

  it('describes a round and edits a plan', () => {
    expect(roundSummary(round('jab'))).toBe('Jab only · Fast')
    expect(roundSummary(round('mix', 6, 30))).toBe('Mix · Steady · speed and power 30s')
    expect(roundSummary({ ...round('three'), pick: ['jab-cross-hook', 'jab-jab-cross'] })).toBe(
      '3-punch combos · Fast · 2 picked',
    )
    expect(paceOf(8)).toBe('Easy')
    expect(paceOf(3)).toBe('All out')
    expect(paceOf(15)).toBe('Easy')
    expect(roundSummary(round('freestyle'))).toBe('Freestyle')
    const plan = boxingPlan({ style: 'names', rounds: [round('jab'), round('two'), round('three')] }, 3)
    expect(withRound(plan, 1, round('body', 7)).rounds.map((entry) => entry.theme)).toEqual(['jab', 'body', 'three'])
    expect(copiedToAll(plan, 2).rounds).toEqual([round('three'), round('three'), round('three')])
    expect(copiedToAll(plan, 9)).toBe(plan)
  })

  it('lays a template over any number of rounds by repeating it, in the style the lifter chose', () => {
    expect(BOXING_TEMPLATES.map((template) => template.name)).toEqual(['Ladder', 'Speed and power', 'Head and body', 'Classic'])
    const ladder = templatePlan(BOXING_TEMPLATES[0].rounds, 6, 'numbers')
    expect(ladder.style).toBe('numbers')
    expect(ladder.rounds.map((entry) => entry.theme)).toEqual(['jab', 'two', 'three', 'four', 'jab', 'two'])
    expect(templatePlan(BOXING_TEMPLATES[1].rounds, 3, 'names').rounds.every((entry) => entry.split === 30)).toBe(true)
  })

  it('calls only the picked combinations of a theme', () => {
    const picked = { ...round('three'), pick: ['jab-cross-hook', 'jab-jab-cross'] }
    expect(roundCombos(picked).map((combo) => combo.id)).toEqual(['jab-cross-hook', 'jab-jab-cross'])
    expect(roundCombos(round('three'))).toHaveLength(7)
    // A pick from another theme does not survive: the round would have nothing to call.
    const cleaned = boxingPlan({ rounds: [{ ...round('jab'), pick: ['jab', 'jab-cross-hook', 'nope'] }] }, 1)
    expect(cleaned.rounds[0].pick).toEqual(['jab'])
    const config = timerConfig({ mode: 'boxing', work: 30, rest: 10, rounds: 1, boxing: { style: 'names', rounds: [picked] } })
    const called = boxingCues(config, QUIET, 3)
      .map((cue) => cue.sound)
      .filter((sound) => soundCombo(sound))
    expect(new Set(called)).toEqual(new Set(['box-jab-jab-cross-n', 'box-jab-cross-hook-n']))
    // One picked combination is called again and again.
    const one = timerConfig({
      ...config,
      boxing: { style: 'names', rounds: [{ ...round('three'), pick: ['jab-cross-hook'] }] },
    })
    expect(new Set(boxingCues(one, QUIET, 1).map((cue) => cue.sound))).toEqual(
      new Set(['box-jab-cross-hook-n', 'box-again', 'box-one-more']),
    )
  })

  it('keeps calling through speed and power, with the short combinations in speed', () => {
    const plain = (split: number) =>
      comboCount(
        boxingCues(
          timerConfig({ mode: 'boxing', work: 180, rest: 60, rounds: 1, boxing: { style: 'names', rounds: [round('jab', 5, split)] } }),
          DEFAULT_TIMER_CALLS,
          1,
        ),
      )
    expect([plain(0), plain(15), plain(30)]).toEqual([33, 31, 35])
    expect(speedCombos(themeCombos('four')).every((combo) => combo.say.split(',').length <= 5)).toBe(true)
    expect(speedCombos(themeCombos('mix')).map((combo) => combo.say.split(',').length).every((count) => count <= 2)).toBe(true)
  })

  it('drills a long combination: says it once, then calls for it again once or twice', () => {
    expect(DRILL_FROM).toBe(3)
    expect(throwTime(3)).toBe(2000)
    expect(throwTime(4)).toBe(2500)
    const config = timerConfig({
      mode: 'boxing',
      work: 60,
      rest: 10,
      rounds: 1,
      boxing: { style: 'names', rounds: [{ ...round('three'), pick: ['jab-cross-hook', 'jab-jab-cross'] }] },
    })
    const cues = boxingCues(config, QUIET, 3)
    expect(cues.map((cue) => `${cue.at / 1000}:${cue.sound}`).join(' ')).toBe(
      '16:box-jab-jab-cross-n 21.7:box-again 25:box-jab-cross-hook-n 30.7:box-again ' +
        '34:box-jab-jab-cross-n 39.7:box-again 42.7:box-one-more 46:box-jab-cross-hook-n 51.7:box-again ' +
        '55:box-jab-jab-cross-n 60.7:box-again 64:box-jab-cross-hook-n',
    )
    expect(isAgain('box-again')).toBe(true)
    expect(isAgain('box-one-more')).toBe(true)
    expect(isAgain('box-jab-n')).toBe(false)
    // "Again" is not another combination.
    expect(comboCount(cues)).toBe(6)
    // The screen counts the goes: one of two, then two of two.
    expect(boxingView(config, cues, 15000)).toMatchObject({ combo: null, go: 0, goes: 0 })
    expect(boxingView(config, cues, 17000)).toMatchObject({ go: 1, goes: 2 })
    expect(boxingView(config, cues, 23000)).toMatchObject({ go: 2, goes: 2 })
    expect(boxingView(config, cues, 35000)).toMatchObject({ go: 1, goes: 3 })
    expect(boxingView(config, cues, 43000)).toMatchObject({ go: 3, goes: 3 })
    // Short combinations are thrown once and move on.
    const jabs = boxingCues(short(), QUIET, 1)
    expect(jabs.some((cue) => isAgain(cue.sound))).toBe(false)
    expect(boxingView(short(), boxingCues(short(), DEFAULT_TIMER_CALLS, 1), 17000)).toMatchObject({ go: 1, goes: 1 })
    // A three-minute round of three-punch combinations: 18 of them instead of a call every few seconds.
    const long = timerConfig({ mode: 'boxing', work: 180, rest: 10, rounds: 1, boxing: { style: 'names', rounds: [round('three')] } })
    expect(comboCount(boxingCues(long, QUIET, 1))).toBe(18)
  })

  it('moves, copies and removes rounds', () => {
    const plan = boxingPlan({ style: 'names', rounds: [round('jab'), round('two'), round('three')] }, 3)
    const themes = (edited: typeof plan) => edited.rounds.map((entry) => entry.theme)
    expect(themes(movedRound(plan, 0, 1))).toEqual(['two', 'jab', 'three'])
    expect(themes(movedRound(plan, 2, -1))).toEqual(['jab', 'three', 'two'])
    expect(movedRound(plan, 0, -1)).toBe(plan)
    expect(themes(duplicatedRound(plan, 1))).toEqual(['jab', 'two', 'two', 'three'])
    expect(themes(withoutRound(plan, 0))).toEqual(['two', 'three'])
    const single = boxingPlan({ rounds: [round('jab')] }, 1)
    expect(withoutRound(single, 0)).toBe(single)
    expect(duplicatedRound(boxingPlan({ rounds: [round('jab')] }, 15), 0).rounds).toHaveLength(15)
  })
})
