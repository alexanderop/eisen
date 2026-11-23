import { describe, expect, it } from 'vitest'
import { calendarWeeks, trainings, weekStreak } from './chart'
import { defaultRoutines, createWorkout } from './program'
import { catalogExercise } from './exercises'
import { freshState, isAppState, migrate } from './state'
import {
  DEFAULT_TIMER_CALLS,
  DEFAULT_TIMERS,
  timerView,
  type TimerConfig,
  type TimerSession,
} from './timer'
import {
  callsSummary,
  historyItems,
  nextText,
  presetName,
  roundsWheel,
  timeWheel,
  timerWheels,
  totalText,
} from './timerScreens'

const tabata: TimerConfig = { mode: 'tabata', work: 20, rest: 10, rounds: 3 }

function session(id: string, startedAt: number): TimerSession {
  return {
    id,
    name: 'Tabata',
    config: tabata,
    startedAt,
    finishedAt: startedAt + 80000,
    seconds: 80,
    rounds: 3,
    completed: true,
  }
}

describe('timer setup', () => {
  it('steps the time wheels by 5 s, then 15 s, then whole minutes', () => {
    const wheel = timeWheel(5, 600, 20)
    expect(wheel.slice(0, 4)).toEqual([5, 10, 15, 20])
    expect(wheel.filter((value) => value > 50 && value <= 360)).toEqual([
      55, 60, 75, 90, 105, 120, 135, 150, 165, 180, 195, 210, 225, 240, 255, 270, 285, 300, 360,
    ])
    expect(wheel.at(-1)).toBe(600)
  })

  it('keeps a stored value that is off the steps', () => {
    expect(timeWheel(5, 300, 42)).toContain(42)
    expect(timeWheel(5, 300, 600).at(-1)).toBe(600)
    expect(roundsWheel(8)).toHaveLength(60)
    expect(roundsWheel(80).at(-1)).toBe(80)
  })

  it('gives each mode the wheels it needs', () => {
    const fields = (config: TimerConfig) => timerWheels(config).map((wheel) => wheel.header)
    expect(fields(DEFAULT_TIMERS.tabata)).toEqual(['Work', 'Rest', 'Rounds'])
    expect(fields(DEFAULT_TIMERS.emom)).toEqual(['Every', 'Rounds'])
    expect(fields(DEFAULT_TIMERS.amrap)).toEqual(['Time'])
    expect(fields(DEFAULT_TIMERS.forTime)).toEqual(['Time cap'])
    const cap = timerWheels({ mode: 'forTime', work: 0, rest: 0, rounds: 1 })[0]
    expect(cap.values.slice(0, 3)).toEqual([0, 60, 75])
    expect(cap.format(0)).toBe('Off')
    expect(timerWheels(DEFAULT_TIMERS.emom)[0].values[0]).toBe(10)
    expect(timerWheels(DEFAULT_TIMERS.amrap)[0].values[0]).toBe(30)
  })

  it('names the total and a new preset', () => {
    expect(totalText({ ...tabata, rounds: 8 })).toBe('Total 3:50')
    expect(totalText({ mode: 'forTime', work: 0, rest: 0, rounds: 1 })).toBe('No limit')
    expect(totalText({ ...tabata, work: 5, rest: 5, rounds: 2 })).toBe('Total 15s')
    expect(presetName('tabata', [])).toBe('Tabata')
    expect(presetName('tabata', ['tabata', 'Tabata 2', 'EMOM'])).toBe('Tabata 3')
    expect(presetName('emom', ['Tabata'])).toBe('EMOM')
  })

  it('sums up what the voice says', () => {
    expect(callsSummary({ ...DEFAULT_TIMER_CALLS, phases: false, beeps: false })).toBe(
      'Every minute, halfway, last 30 s',
    )
    expect(callsSummary({ minutes: 5, halfway: false, final: false, phases: false, beeps: true })).toBe(
      'Every 5 min, 3-2-1 beeps',
    )
    expect(callsSummary({ minutes: 0, halfway: false, final: false, phases: false, beeps: false })).toBe('Voice off')
  })
})

describe('timer run screen', () => {
  it('says what comes next', () => {
    const at = (config: TimerConfig, seconds: number) => nextText(config, timerView(config, seconds * 1000))
    expect(at(tabata, 2)).toBe('Next: Work 20s')
    expect(at(tabata, 12)).toBe('Next: Rest 10s')
    expect(at(tabata, 32)).toBe('Next: Round 2')
    // Round 2 of 3: the round after it is the last.
    expect(at(tabata, 42)).toBe('Next: Last round')
    expect(at(tabata, 62)).toBe('Next: Last round')
    expect(at(tabata, 72)).toBe('Last round')
    expect(at(tabata, 95)).toBe('')
    const emom: TimerConfig = { mode: 'emom', work: 60, rest: 0, rounds: 4 }
    expect(at(emom, 2)).toBe('Next: Work 1:00')
    expect(at(emom, 20)).toBe('Next: Round 2')
    expect(at(emom, 140)).toBe('Next: Last round')
    expect(at(emom, 200)).toBe('Last round')
    expect(at({ mode: 'emom', work: 60, rest: 0, rounds: 1 }, 20)).toBe('Last round')
    expect(at(DEFAULT_TIMERS.amrap, 2)).toBe('Next: Work 10:00')
    expect(at(DEFAULT_TIMERS.amrap, 20)).toBe('')
    expect(at(DEFAULT_TIMERS.forTime, 2)).toBe('Next: Work 20:00')
    expect(at({ mode: 'forTime', work: 0, rest: 0, rounds: 1 }, 2)).toBe('Next: Go')
  })
})

describe('timers in the history', () => {
  const workout = (startedAt: number) => ({
    ...createWorkout(defaultRoutines()[0], catalogExercise, freshState('kg').progress, 'kg', startedAt, `w${startedAt}`),
    finishedAt: startedAt + 1,
  })

  it('mixes timers and workouts newest first, and keeps each workout its index', () => {
    const items = historyItems([workout(300), workout(100)], [session('t1', 200), session('t2', 400)])
    expect(items.map((item) => (item.kind === 'timer' ? item.session.id : `w${item.index}`))).toEqual([
      't2',
      'w0',
      't1',
      'w1',
    ])
  })

  it('counts a day with a timer as a training day, once', () => {
    const day = (date: number) => new Date(2026, 10, date, 18).getTime()
    const now = day(21)
    const weeks = calendarWeeks([workout(day(17))], now, 12, [
      session('t1', day(17)),
      session('t2', day(19)),
      session('t3', day(19)),
    ])
    expect(weeks[11].map(trainings)).toEqual([0, 1, 0, 1, 0, 0, 0])
    expect(weekStreak([session('t1', day(10)).startedAt, now], now)).toBe(2)
  })
})

describe('migrate to version 7', () => {
  it('adds the timers to a version 6 state and keeps everything else', () => {
    const { timerCalls: _, ...settings } = freshState('lb').settings
    const { timerPresets: _p, timerSessions: _s, timerConfigs: _c, ...rest } = freshState('lb')
    const v6 = { ...rest, version: 6, settings, nextRoutineId: 'B' }
    const migrated = migrate(structuredClone(v6))
    if (!isAppState(migrated)) throw new Error('not a valid state')
    expect(migrated.version).toBe(7)
    expect(migrated.settings).toEqual(freshState('lb').settings)
    expect(migrated.settings.timerCalls).toEqual(DEFAULT_TIMER_CALLS)
    expect(migrated.timerPresets).toEqual([])
    expect(migrated.timerSessions).toEqual([])
    expect(migrated.timerConfigs).toEqual(DEFAULT_TIMERS)
    expect(migrated.nextRoutineId).toBe('B')
  })

  it('still restores the oldest backups', () => {
    const v1 = {
      version: 1,
      settings: { unit: 'kg', bar: 20, restDone: 90, restMissed: 300, sound: true },
      progress: { squat: { weight: 60, fails: 0 } },
      nextType: 'A',
      active: null,
      history: [],
    }
    const migrated = migrate(v1)
    expect(isAppState(migrated)).toBe(true)
    expect(migrated).toMatchObject({ version: 7, timerSessions: [], timerConfigs: DEFAULT_TIMERS })
  })

  it('rejects a version 7 state without its timers', () => {
    const { timerSessions: _, ...broken } = freshState('kg')
    expect(isAppState(broken)).toBe(false)
  })
})
