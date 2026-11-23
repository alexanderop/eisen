import { describe, expect, it } from 'vitest'
import {
  activeTime,
  DEFAULT_TIMER_CALLS,
  DEFAULT_TIMERS,
  sessionSummary,
  timerConfig,
  timerOutcome,
  timerCues,
  timerSeconds,
  timerSession,
  timerSummary,
  timerView,
  type TimerCalls,
  type TimerConfig,
  type TimerRun,
} from './timer'

const tabata = (rounds: number): TimerConfig => ({ mode: 'tabata', work: 20, rest: 10, rounds })
const QUIET: TimerCalls = { minutes: 0, halfway: false, final: false, phases: false, beeps: false }

/** "10.7:round-1 30:beep-long": seconds after Start and the sound. */
function schedule(config: TimerConfig, calls: TimerCalls = DEFAULT_TIMER_CALLS): string {
  return timerCues(config, calls)
    .map((cue) => `${cue.at / 1000}:${cue.sound}`)
    .join(' ')
}

describe('timer', () => {
  it('knows how long each mode runs', () => {
    expect(timerSeconds(tabata(8))).toBe(230)
    expect(timerSeconds(DEFAULT_TIMERS.emom)).toBe(600)
    expect(timerSeconds(DEFAULT_TIMERS.amrap)).toBe(600)
    expect(timerSeconds(DEFAULT_TIMERS.forTime)).toBe(1200)
    expect(timerSeconds({ mode: 'forTime', work: 0, rest: 0, rounds: 1 })).toBeNull()
  })

  it('describes a timer in one line', () => {
    expect(timerSummary(tabata(8))).toBe('8 × 20s / 10s')
    expect(timerSummary(DEFAULT_TIMERS.emom)).toBe('10 × 1:00')
    expect(timerSummary(DEFAULT_TIMERS.amrap)).toBe('10:00')
    expect(timerSummary(DEFAULT_TIMERS.forTime)).toBe('Cap 20:00')
    expect(timerSummary({ mode: 'forTime', work: 0, rest: 0, rounds: 1 })).toBe('No cap')
  })

  it('brings a config into the limits of its mode', () => {
    expect(timerConfig({ mode: 'tabata', work: 1, rest: 0, rounds: 99 })).toEqual({
      mode: 'tabata',
      work: 5,
      rest: 5,
      rounds: 60,
    })
    expect(timerConfig({ mode: 'emom', work: 9999, rest: 30, rounds: 0 })).toEqual({
      mode: 'emom',
      work: 3600,
      rest: 0,
      rounds: 1,
    })
    expect(timerConfig({ mode: 'amrap', work: 90.4, rest: 5, rounds: 7 })).toEqual({
      mode: 'amrap',
      work: 90,
      rest: 0,
      rounds: 1,
    })
    expect(timerConfig({ mode: 'forTime', work: -3, rest: 5, rounds: 7 }).work).toBe(0)
    expect(timerConfig({ mode: 'forTime', work: 2, rest: 5, rounds: 7 }).work).toBe(5)
  })

  it('walks through the lead-in, work, rest and the end', () => {
    const config = tabata(3)
    expect(timerView(config, 4000)).toMatchObject({ phase: 'ready', phaseLeft: 6000, elapsed: 0, totalLeft: 80000 })
    expect(timerView(config, 10000)).toMatchObject({ phase: 'work', round: 1, roundsDone: 0, phaseLeft: 20000 })
    expect(timerView(config, 35000)).toEqual({
      phase: 'rest',
      round: 1,
      rounds: 3,
      roundsDone: 1,
      phaseLeft: 5000,
      phaseElapsed: 5000,
      phaseTotal: 10000,
      elapsed: 25000,
      totalLeft: 55000,
      progress: 0.5,
    })
    expect(timerView(config, 75000)).toMatchObject({ phase: 'work', round: 3, roundsDone: 2, totalLeft: 15000 })
    expect(timerView(config, 90000)).toMatchObject({ phase: 'done', roundsDone: 3, totalLeft: 0, elapsed: 80000 })
  })

  it('counts an open For Time up with nothing to count down to', () => {
    const open: TimerConfig = { mode: 'forTime', work: 0, rest: 0, rounds: 1 }
    expect(timerView(open, 70000)).toMatchObject({
      phase: 'work',
      rounds: null,
      phaseLeft: null,
      totalLeft: null,
      elapsed: 60000,
      progress: 0,
    })
    expect(timerView(open, 10000 + 3600000).phase).toBe('done')
  })

  it('beeps into every change of phase and names the rounds', () => {
    expect(schedule(tabata(3))).toBe(
      '0:get-ready 7:beep 8:beep 9:beep 10:beep-long 10.7:round-1 ' +
        '27:beep 28:beep 29:beep 30:beep-long 30.7:rest 33.2:minutes-left-1 ' +
        '37:beep 38:beep 39:beep 40:beep-long 40.7:round-2 50:halfway ' +
        '57:beep 58:beep 59:beep 60:beep-long 60.7:rest ' +
        '67:beep 68:beep 69:beep 70:beep-long 70.7:last-round 80:ten-seconds ' +
        '87:beep 88:beep 89:beep 90:beep-long 90.7:complete',
    )
  })

  it('lets a time call wait for the line about the phase, and says only one of two at once', () => {
    // 2 minutes left and halfway fall together: halfway is said.
    expect(schedule(tabata(8), { ...DEFAULT_TIMER_CALLS, beeps: false })).toBe(
      '0:get-ready 10.7:round-1 30.7:rest 40.7:round-2 60.7:rest 63.2:minutes-left-3 ' +
        '70.7:round-3 90.7:rest 100.7:round-4 120.7:rest 125:halfway 130.7:round-5 ' +
        '150.7:rest 160.7:round-6 180.7:rest 183.2:minutes-left-1 190.7:round-7 ' +
        '210.7:rest 213.2:thirty-seconds 220.7:last-round 230:ten-seconds 240.7:complete',
    )
  })

  it('leaves out a time call that lands on a round of an EMOM', () => {
    expect(schedule({ mode: 'emom', work: 60, rest: 0, rounds: 3 }, { ...DEFAULT_TIMER_CALLS, beeps: false })).toBe(
      '0:get-ready 10.7:round-1 70.7:round-2 100:halfway 130.7:last-round 160:thirty-seconds 180:ten-seconds 190.7:complete',
    )
  })

  it('calls the minutes left as often as asked', () => {
    const amrap: TimerConfig = { mode: 'amrap', work: 600, rest: 0, rounds: 1 }
    const only = (minutes: TimerCalls['minutes']) => schedule(amrap, { ...QUIET, minutes })
    expect(only(0)).toBe('')
    expect(only(5)).toBe('310:minutes-left-5')
    expect(only(2)).toBe('130:minutes-left-8 250:minutes-left-6 370:minutes-left-4 490:minutes-left-2')
    expect(only(1).split(' ')).toHaveLength(9)
    expect(schedule(amrap, { ...QUIET, halfway: true, final: true })).toBe(
      '310:halfway 580:thirty-seconds 600:ten-seconds',
    )
    // Halfway and 5 minutes left are the same moment: halfway is said.
    expect(schedule(amrap, { ...QUIET, halfway: true, minutes: 5 })).toBe('310:halfway')
  })

  it('counts the minutes up in a For Time without a cap', () => {
    const open: TimerConfig = { mode: 'forTime', work: 0, rest: 0, rounds: 1 }
    const cues = timerCues(open, { ...QUIET, minutes: 5, halfway: true, final: true })
    expect(cues[0]).toEqual({ at: 310000, sound: 'minutes-5' })
    expect(cues.at(-1)).toEqual({ at: 3310000, sound: 'minutes-55' })
    expect(schedule(open, { ...QUIET, phases: true, beeps: true })).toBe(
      '0:get-ready 7:beep 8:beep 9:beep 10:beep-long 10.7:go',
    )
  })

  it('says Go where there is one block, and keeps short timers quiet', () => {
    expect(schedule({ mode: 'amrap', work: 20, rest: 0, rounds: 1 }, { ...DEFAULT_TIMER_CALLS, beeps: false })).toBe(
      '0:get-ready 10.7:go 30.7:complete',
    )
  })

  const run = (config: TimerConfig, extra: Partial<TimerRun> = {}): TimerRun => ({
    id: 't1',
    name: 'Tabata',
    config,
    calls: DEFAULT_TIMER_CALLS,
    startedAt: 1000,
    pausedAt: null,
    paused: 0,
    taps: 0,
    ...extra,
  })

  it('does not count pauses', () => {
    expect(activeTime(run(tabata(3)), 31000)).toBe(30000)
    expect(activeTime(run(tabata(3), { paused: 5000 }), 31000)).toBe(25000)
    expect(activeTime(run(tabata(3), { pausedAt: 21000, paused: 5000 }), 99000)).toBe(15000)
  })

  it('turns a run into a history entry', () => {
    // Stopped in the second rest: two rounds done, 55 seconds in.
    const stopped = timerSession(run(tabata(3)), 1000 + 65000, false)
    expect(stopped).toMatchObject({ seconds: 55, rounds: 2, completed: false, finishedAt: 66000 })
    expect(sessionSummary(stopped!)).toBe('2 rounds · 55s')

    const done = timerSession(run(tabata(3)), 1000 + 200000, true)
    expect(done).toMatchObject({ seconds: 80, rounds: 3, completed: true })

    const forTime = timerSession(run(DEFAULT_TIMERS.forTime, { taps: 4 }), 1000 + 10000 + 372000, true)
    expect(sessionSummary(forTime!)).toBe('4 rounds · 6:12')
    const noTaps = timerSession(run(DEFAULT_TIMERS.amrap), 1000 + 10000 + 600000, true)
    expect(sessionSummary(noTaps!)).toBe('10:00')

    expect(timerSession(run(tabata(3)), 1000 + 9000, false)).toBeNull()
  })

  it('tells a For Time that hit its cap from one that was finished', () => {
    const capped = timerSession(run(DEFAULT_TIMERS.forTime), 1000 + 10000 + 1200000, true)
    expect(timerOutcome(capped!)).toBe('capped')
    const finished = timerSession(run(DEFAULT_TIMERS.forTime), 1000 + 10000 + 372000, true)
    expect(timerOutcome(finished!)).toBe('completed')
    expect(timerOutcome(timerSession(run(tabata(3)), 1000 + 65000, false)!)).toBe('stopped')
    expect(timerOutcome(timerSession(run(tabata(3)), 1000 + 200000, true)!)).toBe('completed')
    const open = run({ mode: 'forTime', work: 0, rest: 0, rounds: 1 })
    expect(timerOutcome(timerSession(open, 1000 + 10000 + 372000, true)!)).toBe('completed')
  })
})
