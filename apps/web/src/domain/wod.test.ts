import { describe, expect, it } from 'vitest'
import { CATALOG, catalogExercise } from './exercises'
import { DEFAULT_TIMER_CALLS, timerOutcome, timerSession, wodSteps, type TimerRun, type TimerSession } from './timer'
import {
  bestText,
  findWod,
  isBodyweightWod,
  isLibraryWod,
  isWodRecord,
  itemAmount,
  loadFrom,
  loadIn,
  roundLines,
  sameWork,
  wod,
  wodBest,
  wodDone,
  wodFormat,
  wodImages,
  wodLines,
  wodName,
  wodRound,
  wodRounds,
  wodStep,
  WOD_LIBRARY,
  type Wod,
} from './wod'

const lib = (id: string): Wod => WOD_LIBRARY.find((entry) => entry.id === id)!
const find = catalogExercise

describe('wod', () => {
  it('ships twenty-one benchmark workouts whose exercises all exist', () => {
    expect(WOD_LIBRARY.map((entry) => entry.name)).toEqual([
      'Fran',
      'Cindy',
      'Helen',
      'Grace',
      'Annie',
      'Karen',
      'Angie',
      'Barbara',
      'Chelsea',
      'Mary',
      'Jackie',
      'Diane',
      'Isabel',
      'DT',
      'Murph',
      'Venus',
      'Aphrodite',
      'Helios',
      'Zeus',
      'Ares',
      'Gaia',
    ])
    expect(new Set(WOD_LIBRARY.map((entry) => entry.id)).size).toBe(21)
    for (const entry of WOD_LIBRARY) {
      expect(entry, entry.name).toEqual(wod(entry))
      for (const item of entry.items) expect(find(item.exerciseId), item.exerciseId).toBeDefined()
    }
  })

  it('adds the movements the workouts need to the catalog', () => {
    const ids = CATALOG.map((exercise) => exercise.id)
    for (const id of [
      'airSquat',
      'pistolSquat',
      'cleanAndJerk',
      'snatch',
      'hangPowerClean',
      'kettlebellSwing',
      'thruster',
      'pushJerk',
      'handstandPushup',
      'toesToBar',
      'burpee',
      'boxJump',
      'doubleUnder',
      'wallBall',
      'run',
      'rowErg',
      'bodyweightLunge',
      'climber',
      'jumpingJack',
      'jump',
      'standup',
      'sprint',
      'rest',
    ]) {
      expect(ids, id).toContain(id)
    }
    expect(CATALOG.filter((exercise) => exercise.group === 'Conditioning').map((exercise) => exercise.name)).toEqual([
      'Burpee',
      'Box Jump',
      'Double-Under',
      'Wall Ball',
      'Run',
      'Row',
      'Climbers',
      'Jumping Jacks',
      'Jumps',
      'Standups',
      'Sprint',
      'Rest',
    ])
  })

  it('says what kind of workout it is', () => {
    expect(wodFormat(lib('wod-fran'))).toBe('21-15-9 · For Time')
    expect(wodFormat(lib('wod-helen'))).toBe('3 rounds · For Time')
    expect(wodFormat(lib('wod-grace'))).toBe('For Time')
    expect(wodFormat(lib('wod-venus'))).toBe('4 rounds · For Time')
    expect(wodFormat(lib('wod-aphrodite'))).toBe('50-40-30-20-10 · For Time')
    expect(roundLines(lib('wod-zeus'), 1, find, 'kg').at(-1)).toBe('2:00 Rest')
    expect(roundLines(lib('wod-ares'), 2, find, 'kg')).toEqual([
      '7 × Pull-ups',
      '7 × Sit-ups',
      '40 m Sprint',
      '40 m Sprint',
      '1:00 Rest',
    ])
    expect(wodLines(lib('wod-helios'), find, 'kg')).toHaveLength(7)
    expect(wodLines(lib('wod-venus'), find, 'kg')).toEqual(['50 × Push-ups', '20 × Sit-ups', '50 × Air Squat'])
    expect(wodFormat(lib('wod-cindy'))).toBe('AMRAP 20:00')
    expect(wodFormat(lib('wod-chelsea'))).toBe('EMOM 30 × 1:00')
    expect(wodFormat(wod({ ...lib('wod-cindy'), timer: { mode: 'tabata', work: 20, rest: 10, rounds: 8 } }))).toBe(
      'Tabata 8 × 20s / 10s',
    )
  })

  it('lists what to do, in the unit the app shows', () => {
    expect(wodLines(lib('wod-fran'), find, 'kg')).toEqual(['Thruster 43kg', 'Pull-ups'])
    expect(wodLines(lib('wod-fran'), find, 'lb')).toEqual(['Thruster 95lb', 'Pull-ups'])
    expect(wodLines(lib('wod-helen'), find, 'kg')).toEqual(['400 m Run', '21 × Kettlebell Swing 24kg', '12 × Pull-ups'])
    expect(wodLines(lib('wod-murph'), find, 'kg')).toEqual([
      '1.6 km Run',
      '100 × Pull-ups',
      '200 × Push-ups',
      '300 × Air Squat',
      '1.6 km Run',
    ])
    expect(wodLines(lib('wod-grace'), find, 'lb')).toEqual(['30 × Clean and Jerk 135lb'])
  })

  it('gives every round of a scheme its own reps', () => {
    const fran = lib('wod-fran')
    expect(roundLines(fran, 1, find, 'kg')).toEqual(['21 × Thruster 43kg', '21 × Pull-ups'])
    expect(roundLines(fran, 2, find, 'kg')).toEqual(['15 × Thruster 43kg', '15 × Pull-ups'])
    expect(roundLines(fran, 3, find, 'kg')).toEqual(['9 × Thruster 43kg', '9 × Pull-ups'])
    // Past the last round it stays on the last one.
    expect(itemAmount(fran, fran.items[0], 9)).toBe(9)
    // Metres are never replaced by the scheme.
    const mixed = wod({ ...fran, items: [...fran.items, { exerciseId: 'run', amount: 200, unit: 'meters', load: 0 }] })
    expect(roundLines(mixed, 2, find, 'kg')[2]).toBe('200 m Run')
  })

  it('takes the items of a Tabata in turns and shows a missing amount as max', () => {
    const turns = wod({
      id: 'w',
      name: 'Turns',
      timer: { mode: 'tabata', work: 20, rest: 10, rounds: 8 },
      rounds: 3,
      scheme: [21, 15, 9],
      items: [
        { exerciseId: 'pushups', amount: 0, unit: 'reps', load: 0 },
        { exerciseId: 'plank', amount: 20, unit: 'seconds', load: 0 },
      ],
    })
    // Rounds and scheme belong to For Time only.
    expect(turns.rounds).toBe(1)
    expect(turns.scheme).toEqual([])
    expect(roundLines(turns, 1, find, 'kg')).toEqual(['Max Push-ups'])
    expect(roundLines(turns, 2, find, 'kg')).toEqual(['20s Plank'])
    expect(roundLines(turns, 3, find, 'kg')).toEqual(['Max Push-ups'])
  })

  it('brings a draft into its limits', () => {
    const draft = wod({
      id: 'w',
      name: '   ',
      timer: { mode: 'forTime', work: 0, rest: 9, rounds: 9 },
      rounds: 99,
      scheme: [],
      items: [{ exerciseId: 'thruster', amount: -4, unit: 'reps', load: 43.26 }],
    })
    expect(draft.name).toBe('')
    expect(wodName(draft)).toBe('60 rounds · For Time')
    expect(wodName(lib('wod-fran'))).toBe('Fran')
    expect(draft.rounds).toBe(60)
    expect(draft.timer).toEqual({ mode: 'forTime', work: 0, rest: 0, rounds: 1 })
    expect(draft.items[0]).toEqual({ exerciseId: 'thruster', amount: 0, unit: 'reps', load: 43.5 })
    // A scheme sets the number of rounds.
    expect(wod({ ...draft, scheme: [5, 4, 0] })).toMatchObject({ rounds: 3, scheme: [5, 4, 1] })
  })

  it('counts rounds by taps where the lifter counts and by the clock elsewhere', () => {
    const fran = lib('wod-fran')
    expect(wodRounds(fran)).toBe(3)
    expect(wodRounds(lib('wod-cindy'))).toBeNull()
    expect(wodRounds(lib('wod-chelsea'))).toBe(30)
    expect(wodRound(fran, 0, 1)).toBe(1)
    expect(wodRound(fran, 2, 1)).toBe(3)
    expect(wodRound(fran, 5, 1)).toBe(3)
    expect(wodDone(fran, 2)).toBe(false)
    expect(wodDone(fran, 3)).toBe(true)
    expect(wodRound(lib('wod-cindy'), 7, 1)).toBe(8)
    expect(wodDone(lib('wod-cindy'), 99)).toBe(false)
    expect(wodRound(lib('wod-chelsea'), 7, 12)).toBe(12)
  })

  it('ticks the items of a single round off one by one', () => {
    const murph = lib('wod-murph')
    expect(wodSteps(murph)).toBe(5)
    expect(wodSteps(lib('wod-grace'))).toBe(1)
    expect(wodSteps(lib('wod-fran'))).toBe(3)
    expect(wodSteps(lib('wod-cindy'))).toBeNull()
    expect(wodStep(murph, 0)).toBe(0)
    expect(wodStep(murph, 3)).toBe(3)
    expect(wodStep(murph, 9)).toBe(4)
    expect(wodDone(murph, 4)).toBe(false)
    expect(wodDone(murph, 5)).toBe(true)
    expect(wodRound(murph, 3, 1)).toBe(1)
  })

  it('knows when an edit changed the work and not only the name or the cap', () => {
    const fran = lib('wod-fran')
    expect(sameWork(fran, { ...fran, name: 'Other' })).toBe(true)
    expect(sameWork(fran, { ...fran, timer: { ...fran.timer, work: 300 } })).toBe(true)
    expect(sameWork(fran, wod({ ...fran, scheme: [15, 12, 9] }))).toBe(false)
    expect(sameWork(fran, { ...fran, items: [{ ...fran.items[0], load: 30 }, fran.items[1]] })).toBe(false)
    const cindy = lib('wod-cindy')
    expect(sameWork(cindy, { ...cindy, timer: { ...cindy.timer, work: 600 } })).toBe(false)
  })

  it('converts weights both ways', () => {
    expect(loadIn(43, 'kg')).toBe(43)
    expect(loadIn(61, 'lb')).toBe(135)
    expect(loadIn(102, 'lb')).toBe(225)
    expect(loadFrom(95, 'lb')).toBe(43)
    expect(loadFrom(42.5, 'kg')).toBe(42.5)
  })

  it('picks up to three different pictures and knows what needs no weights', () => {
    expect(wodImages(lib('wod-murph'), find)).toEqual(['run', 'pull-up', 'push-up'])
    expect(wodImages(lib('wod-grace'), find)).toEqual(['clean-and-jerk'])
    const free = WOD_LIBRARY.filter((entry) => isBodyweightWod(entry, find)).map((entry) => entry.name)
    expect(free).toEqual([
      'Cindy',
      'Annie',
      'Angie',
      'Barbara',
      'Chelsea',
      'Mary',
      'Murph',
      'Venus',
      'Aphrodite',
      'Helios',
      'Zeus',
      'Ares',
      'Gaia',
    ])
  })

  it('finds library and own workouts by id', () => {
    const own = wod({ ...lib('wod-fran'), id: 'mine', name: 'My Fran' })
    expect(findWod([own], 'mine')?.name).toBe('My Fran')
    expect(findWod([own], 'wod-fran')?.name).toBe('Fran')
    expect(findWod([], 'nope')).toBeUndefined()
    expect(isLibraryWod('wod-fran')).toBe(true)
    expect(isLibraryWod('mine')).toBe(false)
  })

  const run = (workout: Wod, taps = 0): TimerRun => ({
    id: 'r',
    name: workout.name,
    config: workout.timer,
    calls: DEFAULT_TIMER_CALLS,
    startedAt: 0,
    pausedAt: null,
    paused: 0,
    taps,
    wod: workout,
  })
  const session = (workout: Wod, seconds: number, taps: number, finished: boolean, id: string): TimerSession => ({
    ...timerSession(run(workout, taps), 10000 + seconds * 1000, finished)!,
    id,
  })

  it('files a run of a workout under its id', () => {
    expect(session(lib('wod-fran'), 292, 3, true, 'a')).toMatchObject({ wodId: 'wod-fran', seconds: 292, rounds: 3 })
    const plain = timerSession({ ...run(lib('wod-fran')), wod: undefined }, 20000, true)!
    expect('wodId' in plain).toBe(false)
  })

  it('counts a For Time only as finished with every step tapped off', () => {
    // The clock stopped after one of three rounds: saved, but not a time to beat.
    const early = session(lib('wod-fran'), 3, 1, true, 'early')
    expect(early).toMatchObject({ completed: false, rounds: 1 })
    expect(timerOutcome(early)).toBe('stopped')
    expect(wodBest([early], 'wod-fran')).toBeNull()
    // A single round counts its items: all five ticked off is one round.
    expect(session(lib('wod-murph'), 2400, 5, true, 'm')).toMatchObject({ completed: true, rounds: 1 })
    expect(session(lib('wod-murph'), 900, 2, true, 'm2')).toMatchObject({ completed: false, rounds: 0 })
    expect(session(lib('wod-grace'), 200, 1, true, 'g')).toMatchObject({ completed: true, rounds: 1 })
    // The clock decides in the other modes.
    expect(session(lib('wod-cindy'), 1200, 0, true, 'c')).toMatchObject({ completed: true, rounds: 0 })
  })

  it('keeps the fastest finished For Time and the AMRAP with the most rounds', () => {
    const fran = lib('wod-fran')
    const cindy = lib('wod-cindy')
    const sessions = [
      session(fran, 310, 3, true, 'slow'),
      session(fran, 292, 3, true, 'fast'),
      session(fran, 120, 1, false, 'stopped'),
      session(fran, 600, 2, true, 'capped'),
      session(cindy, 1200, 14, true, 'c14'),
      session(cindy, 1200, 18, true, 'c18'),
      session(cindy, 400, 30, false, 'cstopped'),
      session(lib('wod-chelsea'), 1800, 0, true, 'emom'),
    ]
    expect(wodBest(sessions, 'wod-fran')?.id).toBe('fast')
    expect(bestText(wodBest(sessions, 'wod-fran')!)).toBe('4:52')
    expect(wodBest(sessions, 'wod-cindy')?.id).toBe('c18')
    expect(bestText(wodBest(sessions, 'wod-cindy')!)).toBe('18 rounds')
    expect(wodBest(sessions, 'wod-chelsea')).toBeNull()
    expect(wodBest(sessions, 'wod-grace')).toBeNull()

    expect(isWodRecord(sessions, session(fran, 280, 3, true, 'new'))).toBe(true)
    expect(isWodRecord(sessions, session(fran, 292, 3, true, 'same'))).toBe(false)
    expect(isWodRecord(sessions, session(fran, 100, 1, false, 'quit'))).toBe(false)
    expect(isWodRecord(sessions, session(cindy, 1200, 19, true, 'more'))).toBe(true)
    // A first run has nothing to beat.
    expect(isWodRecord([], session(fran, 280, 3, true, 'first'))).toBe(false)
  })
})
