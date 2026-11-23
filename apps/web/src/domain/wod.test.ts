import { describe, expect, it } from 'vitest'
import { CATALOG, catalogExercise } from './exercises'
import {
  DEFAULT_TIMER_CALLS,
  splitTime,
  timerOutcome,
  timerSession,
  wodSteps,
  type TimerRun,
  type TimerSession,
} from './timer'
import {
  bestText,
  findWod,
  gapText,
  isBodyweightWod,
  isLibraryWod,
  isRest,
  isWodRecord,
  liveGap,
  itemAmount,
  loadFrom,
  loadIn,
  roundLines,
  roundsGap,
  roundsGapText,
  sameWork,
  wod,
  wodBest,
  wodDone,
  wodFormat,
  wodImages,
  wodLines,
  wodName,
  wodRest,
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
    // Two items a round: the second tap finishes round one.
    expect(wodRound(fran, 1, 1)).toBe(1)
    expect(wodRound(fran, 2, 1)).toBe(2)
    expect(wodRound(fran, 5, 1)).toBe(3)
    expect(wodRound(fran, 9, 1)).toBe(3)
    expect(wodDone(fran, 5)).toBe(false)
    expect(wodDone(fran, 6)).toBe(true)
    expect(wodRound(lib('wod-cindy'), 7, 1)).toBe(8)
    expect(wodDone(lib('wod-cindy'), 99)).toBe(false)
    expect(wodRound(lib('wod-chelsea'), 7, 12)).toBe(12)
  })

  it('ticks the items of a single round off one by one', () => {
    const murph = lib('wod-murph')
    expect(wodSteps(murph)).toBe(5)
    expect(wodSteps(lib('wod-grace'))).toBe(1)
    expect(wodSteps(lib('wod-fran'))).toBe(6)
    expect(wodSteps(lib('wod-venus'))).toBe(12)
    // Venus: three items a round. The fifth tap is the second item of round two.
    expect(wodStep(lib('wod-venus'), 4)).toBe(1)
    expect(wodRound(lib('wod-venus'), 4, 1)).toBe(2)
    expect(wodStep(lib('wod-venus'), 12)).toBe(2)
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

  it('lets the clock count a rest instead of asking for a tap', () => {
    const zeus = lib('wod-zeus')
    // Five movements and a rest a round: five taps, four rounds.
    expect(wodSteps(zeus)).toBe(20)
    expect(isRest(zeus.items[5])).toBe(true)
    expect(wodStep(zeus, 4)).toBe(4)
    // The fifth tap ends round one: on to the first movement of round two, past the rest.
    expect(wodStep(zeus, 5)).toBe(0)
    expect(wodRound(zeus, 5, 1)).toBe(2)
    expect(wodDone(zeus, 19)).toBe(false)
    expect(wodDone(zeus, 20)).toBe(true)

    const five = [60, 120, 180, 240, 300]
    // No rest in the middle of a round.
    expect(wodRest(zeus, five.slice(0, 3), 185)).toBeNull()
    // The fifth tap starts the two minutes by itself.
    expect(wodRest(zeus, five, 300)).toEqual({ left: 120, total: 120 })
    expect(wodRest(zeus, five, 361.5)).toEqual({ left: 59, total: 120 })
    expect(wodRest(zeus, five, 420)).toBeNull()
    // After the very last tap there is nothing to rest for.
    const all = Array.from({ length: 20 }, (_, index) => (index + 1) * 60)
    expect(wodRest(zeus, all, 1200)).toBeNull()
    expect(wodRest(lib('wod-fran'), [40, 75], 76)).toBeNull()
    expect(wodRest(lib('wod-cindy'), [60], 61)).toBeNull()

    // Ares: two sprints, then a minute.
    const ares = lib('wod-ares')
    expect(wodSteps(ares)).toBe(20)
    expect(wodRest(ares, [20, 40, 50, 60], 70)).toEqual({ left: 50, total: 60 })
    expect(timerSession(run(zeus, 20), 10000 + 1300000, true)).toMatchObject({ completed: true, rounds: 4 })
    expect(timerSession(run(zeus, 7), 10000 + 500000, false)).toMatchObject({ completed: false, rounds: 1 })
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
    expect(session(lib('wod-fran'), 292, 6, true, 'a')).toMatchObject({ wodId: 'wod-fran', seconds: 292, rounds: 3 })
    const plain = timerSession({ ...run(lib('wod-fran')), wod: undefined }, 20000, true)!
    expect('wodId' in plain).toBe(false)
  })

  it('counts a For Time only as finished with every step tapped off', () => {
    // The clock stopped after one of three rounds: saved, but not a time to beat.
    const early = session(lib('wod-fran'), 3, 2, true, 'early')
    expect(early).toMatchObject({ completed: false, rounds: 1 })
    expect(session(lib('wod-fran'), 3, 1, true, 'item')).toMatchObject({ completed: false, rounds: 0 })
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
      session(fran, 310, 6, true, 'slow'),
      session(fran, 292, 6, true, 'fast'),
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

    expect(isWodRecord(sessions, session(fran, 280, 6, true, 'new'))).toBe(true)
    expect(isWodRecord(sessions, session(fran, 292, 6, true, 'same'))).toBe(false)
    expect(isWodRecord(sessions, session(fran, 100, 1, false, 'quit'))).toBe(false)
    expect(isWodRecord(sessions, session(cindy, 1200, 19, true, 'more'))).toBe(true)
    // A first run has nothing to beat.
    expect(isWodRecord([], session(fran, 280, 6, true, 'first'))).toBe(false)
  })

  it('keeps the time of every tap and files it with the run', () => {
    const fran = lib('wod-fran')
    expect(splitTime(run(fran), 10000 + 41260)).toBe(41.3)
    // In the lead-in nothing has started.
    expect(splitTime(run(fran), 4000)).toBe(0)
    const done = timerSession({ ...run(fran, 6), splits: [40, 75, 120, 150, 190, 212.5] }, 10000 + 212500, true)!
    expect(done.splits).toEqual([40, 75, 120, 150, 190, 212.5])
    expect('splits' in timerSession(run(fran, 0), 30000, false)!).toBe(false)
    const plain = timerSession({ ...run(fran, 2), wod: undefined, splits: [1, 2] }, 30000, true)!
    expect('splits' in plain).toBe(false)
  })

  it('measures a run against the best while it is still going', () => {
    const best = { ...session(lib('wod-fran'), 213, 6, true, 'best'), splits: [40, 75, 120, 150, 190, 212.5] }
    // Level until the first item takes longer than it did in the best run.
    expect(liveGap(best, [], 20)).toBe(0)
    expect(liveGap(best, [], 47)).toBe(7)
    // Tapped 5 s early: ahead by that, for as long as the next item stays within its time.
    expect(liveGap(best, [35], 50)).toBe(-5)
    expect(liveGap(best, [35], 75)).toBe(0)
    expect(liveGap(best, [35], 83)).toBe(8)
    // Behind at the last tap stays behind.
    expect(liveGap(best, [40, 90], 95)).toBe(15)
    // The finish: only the last tap counts.
    // Whole seconds as the screen shows them: the best reads 3:33, this run 3:20.
    expect(liveGap(best, [38, 70, 110, 140, 180, 200], 200)).toBe(-13)
    // Nothing to measure against.
    expect(liveGap(null, [10], 20)).toBeNull()
    expect(liveGap(session(lib('wod-fran'), 213, 6, true, 'old'), [10], 20)).toBeNull()

    expect(gapText(-12)).toBe('12s ahead')
    expect(gapText(65)).toBe('1:05 behind')
    expect(gapText(0)).toBe('On pace')
  })

  it('counts an AMRAP against the rounds the best run had by now', () => {
    const best = { ...session(lib('wod-cindy'), 1200, 4, true, 'best'), splits: [60, 125, 200, 290] }
    expect(roundsGap(best, 0, 30)).toBe(0)
    expect(roundsGap(best, 0, 61)).toBe(-1)
    expect(roundsGap(best, 3, 130)).toBe(1)
    expect(roundsGap(best, 4, 300)).toBe(0)
    expect(roundsGap(null, 2, 100)).toBeNull()
    expect(roundsGapText(2)).toBe('2 rounds ahead')
    expect(roundsGapText(-1)).toBe('1 round behind')
    expect(roundsGapText(0)).toBe('On pace')
  })
})
