import { describe, expect, it } from 'vitest'
import { catalogExercise } from './exercises'
import { DEFAULT_TIMER_CALLS, DEFAULT_TIMERS, timerSession, timerView, type TimerRun } from './timer'
import { cleanWods, freshState, isAppState, migrate } from './state'
import { wod, WOD_LIBRARY, type Wod } from './wod'
import {
  amountWheel,
  cardLabel,
  copyEdit,
  copyName,
  editOf,
  filterWods,
  isChanged,
  itemSummary,
  lastRun,
  loadText,
  loadWheel,
  newEdit,
  newItem,
  parseScheme,
  readEdit,
  roundCard,
  roundHeading,
  runText,
  savedVersion,
  schemeChoice,
  searchRank,
  switchMode,
  undoneRun,
  wodButton,
  WOD_EXERCISE_IDS,
  wodRecordIds,
} from './wodScreens'

const lib = (id: string): Wod => WOD_LIBRARY.find((entry) => entry.id === id)!
const find = catalogExercise
const exercise = (id: string) => catalogExercise(id)!

const runOf = (workout: Wod, taps = 0): TimerRun => ({
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

describe('the WODs page', () => {
  it('filters the benchmarks', () => {
    const names = (filter: Parameters<typeof filterWods>[1]) =>
      filterWods(WOD_LIBRARY, filter, find).map((entry) => entry.name)
    expect(names('all')).toHaveLength(21)
    expect(names('bodyweight')).toEqual([
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
    expect(names('amrap')).toEqual(['Cindy', 'Mary'])
    expect(names('forTime')).not.toContain('Chelsea')
  })

  it('reads a card with pauses between its parts', () => {
    expect(cardLabel(lib('wod-fran'), find, 'kg', null)).toBe('Fran. 21-15-9, For Time. Thruster 43kg, Pull-ups.')
    const best = timerSession(runOf(lib('wod-fran'), 3), 10000 + 292000, true)
    expect(cardLabel(lib('wod-fran'), find, 'kg', best)).toBe(
      'Fran. 21-15-9, For Time. Thruster 43kg, Pull-ups. Best 4:52.',
    )
    expect(cardLabel({ ...lib('wod-cindy'), name: '' }, find, 'kg', null)).toBe(
      'AMRAP 20:00. 5 × Pull-ups, 10 × Push-ups, 15 × Air Squat.',
    )
  })

  it('tells the last run, and which runs were records at the time', () => {
    const fran = lib('wod-fran')
    const at = (id: string, startedAt: number, seconds: number) => ({
      ...timerSession(runOf(fran, 3), 10000 + seconds * 1000, true)!,
      id,
      startedAt,
    })
    const sessions = [at('third', 300, 290), at('second', 200, 400), at('first', 100, 300)]
    expect(wodRecordIds(sessions)).toEqual(new Set(['third']))
    expect(wodRecordIds([at('b', 200, 250), at('a', 100, 300)])).toEqual(new Set(['b']))
    expect(lastRun(sessions, 'wod-fran')?.id).toBe('third')
    expect(lastRun(sessions, 'wod-cindy')).toBeNull()
    expect(runText(sessions[0])).toBe('4:50')
  })
})

describe('the exercise picker of the builder', () => {
  it('starts on the movements WODs are made of', () => {
    for (const id of ['thruster', 'pullups', 'run', 'burpee', 'rowErg']) expect(WOD_EXERCISE_IDS.has(id), id).toBe(true)
    expect(WOD_EXERCISE_IDS.has('legExtension')).toBe(false)
  })

  it('ranks a hit at the start of a word above one inside a word', () => {
    expect(searchRank('Run', 'run')).toBe(0)
    expect(searchRank('Cable Crunch', 'run')).toBe(1)
    expect(searchRank('Box Jump', 'jump')).toBe(0)
    expect(searchRank('Double-Under', 'under')).toBe(0)
    expect(searchRank('Plank', 'run')).toBeNull()
  })
})

describe('the builder', () => {
  it('adds a movement with a sensible start, as Max in a Tabata', () => {
    expect(newItem(exercise('pullups'), 'forTime')).toEqual({ exerciseId: 'pullups', amount: 10, unit: 'reps', load: 0 })
    expect(newItem(exercise('plank'), 'amrap')).toMatchObject({ amount: 30, unit: 'seconds' })
    expect(newItem(exercise('run'), 'emom')).toMatchObject({ amount: 400, unit: 'meters' })
    expect(newItem(exercise('rowErg'), 'forTime')).toMatchObject({ amount: 400, unit: 'meters' })
    expect(newItem(exercise('thruster'), 'forTime')).toMatchObject({ unit: 'reps', load: 30 })
    expect(newItem(exercise('burpee'), 'tabata')).toMatchObject({ amount: 0, unit: 'reps' })
  })

  it('keeps what was set for each type when the type changes and changes back', () => {
    const fran = lib('wod-fran')
    const amrap = switchMode(fran, {}, 'amrap')
    expect(amrap.draft.items).toEqual(fran.items)
    expect(amrap.draft).toMatchObject({ timer: DEFAULT_TIMERS.amrap, rounds: 1, scheme: [] })
    const set = { ...amrap.draft, timer: { ...amrap.draft.timer, work: 300 } }
    const back = switchMode(set, amrap.memory, 'forTime')
    expect(back.draft).toMatchObject({ timer: fran.timer, rounds: 3, scheme: [21, 15, 9] })
    expect(switchMode(back.draft, back.memory, 'amrap').draft.timer.work).toBe(300)
    expect(switchMode(fran, {}, 'forTime').draft).toBe(fran)
  })

  it('turns Max into an amount when a draft leaves Tabata', () => {
    const tabata = {
      ...lib('wod-cindy'),
      timer: { ...DEFAULT_TIMERS.tabata },
      items: [
        { exerciseId: 'burpee', amount: 0, unit: 'reps' as const, load: 0 },
        { exerciseId: 'plank', amount: 0, unit: 'seconds' as const, load: 0 },
        { exerciseId: 'run', amount: 0, unit: 'meters' as const, load: 0 },
      ],
    }
    expect(switchMode(tabata, {}, 'forTime').draft.items.map((item) => item.amount)).toEqual([10, 30, 400])
  })

  it('opens "Edit a copy" as an unsaved new draft', () => {
    const copy = copyEdit(lib('wod-fran'), 'new')
    expect(copy.draft).toMatchObject({ id: 'new', name: 'Fran (mine)', items: lib('wod-fran').items })
    expect(copy.back).toEqual({ kind: 'detail', id: 'wod-fran' })
    expect(isChanged(copy)).toBe(false)
    expect(copyEdit({ ...lib('wod-cindy'), name: '' }, 'x').draft.name).toBe('AMRAP 20:00 (mine)')
    expect(newEdit('n').draft).toMatchObject({ id: 'n', name: '', items: [] })
  })

  it('keeps a draft in storage and reads it back after a reload', () => {
    const open = editOf(lib('wod-fran'))
    open.draft.name = 'Changed'
    const read = readEdit(JSON.stringify(open))
    expect(read).toEqual(open)
    expect(read && isChanged(read)).toBe(true)
    expect(readEdit(null)).toBeNull()
    expect(readEdit('{oops')).toBeNull()
    expect(readEdit(JSON.stringify({ draft: { id: 'x' }, start: '' }))).toBeNull()
  })

  it('saves an edit that changes the work under a new id, and a new name or cap under the old one', () => {
    const fran = lib('wod-fran')
    expect(savedVersion({ ...fran, name: 'Mine' }, fran, 'new').id).toBe('wod-fran')
    expect(savedVersion({ ...fran, timer: { ...fran.timer, work: 900 } }, fran, 'new').id).toBe('wod-fran')
    expect(savedVersion({ ...fran, scheme: [15, 12, 9] }, fran, 'new').id).toBe('new')
    expect(savedVersion({ ...fran, id: 'own' }, undefined, 'new').id).toBe('own')
    // An empty name stays empty: the WOD is shown by its format.
    expect(savedVersion({ ...fran, name: '  ' }, undefined, 'n').name).toBe('')
  })

  it('copies with "(mine)" within the name limit', () => {
    expect(copyName('Fran')).toBe('Fran (mine)')
    expect(copyName('A'.repeat(40))).toHaveLength(40)
  })

  it('reads a custom scheme and recognises the offered ones', () => {
    expect(parseScheme('30-20-10')).toEqual([30, 20, 10])
    expect(parseScheme(' 5 4, 3 – x 0 ')).toEqual([5, 4, 3])
    expect(parseScheme('')).toEqual([])
    expect(schemeChoice([])).toBe('same')
    expect(schemeChoice([21, 15, 9])).toBe('0')
    expect(schemeChoice([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])).toBe('3')
    expect(schemeChoice([30, 20, 10])).toBe('custom')
  })

  it('offers amounts on a wheel that always holds the current value', () => {
    const reps = amountWheel('reps', 10)
    expect(reps.slice(0, 3)).toEqual([1, 2, 3])
    expect(reps).toContain(30)
    expect(reps).toContain(35)
    expect(reps).not.toContain(31)
    expect(reps).toContain(125)
    expect(reps).toContain(600)
    expect(reps.at(-1)).toBe(1000)
    expect(amountWheel('reps', 33)).toContain(33)
    expect(amountWheel('meters', 400)).toEqual([100, 200, 400, 500, 800, 1000, 1600, 2000, 5000])
    expect(amountWheel('meters', 300)).toContain(300)
    expect(amountWheel('seconds', 30)).toContain(30)
    expect(amountWheel('seconds', 0)).not.toContain(0)
  })

  it('offers kettlebell loads a kilo apart and bar loads in plate steps', () => {
    const bell = loadWheel('weight', 'kg', 24)
    expect(bell.slice(0, 4)).toEqual([0, 1, 2, 3])
    expect(bell).toContain(24)
    expect(bell).toContain(40)
    expect(bell).toContain(42.5)
    expect(bell).not.toContain(41)
    expect(bell.at(-1)).toBe(200)
    expect(loadWheel('barbell', 'kg', 0).slice(0, 3)).toEqual([0, 2.5, 5])
    expect(loadWheel('barbell', 'kg', 43)).toContain(43)
    expect(loadWheel('weight', 'lb', 55).slice(0, 3)).toEqual([0, 5, 10])
    expect(loadWheel('machine', 'lb', 0).at(-1)).toBe(440)
    expect(loadText(16, 'kg')).toBe('16kg')
    expect(loadText(0, 'lb')).toBe('None')
  })

  it('sums up a movement', () => {
    const fran = lib('wod-fran')
    const helen = lib('wod-helen')
    expect(itemSummary(fran, fran.items[0], 'kg')).toBe('by scheme · 43kg')
    expect(itemSummary(helen, helen.items[0], 'kg')).toBe('400 m')
    expect(itemSummary(helen, helen.items[1], 'lb')).toBe('21 reps · 55lb')
    expect(itemSummary(helen, { ...helen.items[2], amount: 0 }, 'kg')).toBe('Max')
    expect(itemSummary(helen, { exerciseId: 'plank', amount: 30, unit: 'seconds', load: 0 }, 'kg')).toBe('30s')
  })
})

describe('the run screen', () => {
  const view = (workout: Wod, seconds: number) => timerView(workout.timer, (10 + seconds) * 1000)

  it('counts the rounds of a For Time in the heading only, and AMRAP rounds on the button only', () => {
    const fran = lib('wod-fran')
    expect(roundHeading(fran, 1, view(fran, 60))).toBe('Round 2 of 3')
    expect(roundCard(fran, 1, view(fran, 60), find, 'kg').map((line) => line.text)).toEqual([
      '15 × Thruster 43kg',
      '15 × Pull-ups',
    ])
    expect(roundHeading(lib('wod-grace'), 0, view(lib('wod-grace'), 60))).toBe('')
    expect(roundHeading(lib('wod-cindy'), 7, view(lib('wod-cindy'), 60))).toBe('')
    expect(roundHeading(lib('wod-chelsea'), 0, view(lib('wod-chelsea'), 125))).toBe('Round 3 of 30')
  })

  it('has one button that moves on: a round, the next item, or done', () => {
    expect(wodButton(lib('wod-fran'), 1, find, 'kg')).toEqual({ big: 'Round done', small: 'tap' })
    expect(wodButton(lib('wod-murph'), 2, find, 'kg')).toEqual({ big: 'Next', small: '200 × Push-ups' })
    expect(wodButton(lib('wod-grace'), 0, find, 'kg')).toEqual({ big: 'Done', small: 'tap when finished' })
  })

  it('ticks off the items of a single round in order', () => {
    const murph = lib('wod-murph')
    expect(roundCard(murph, 2, view(murph, 60), find, 'kg').map((line) => line.state)).toEqual([
      'done',
      'done',
      'current',
      'plain',
      'plain',
    ])
  })

  it('ends a For Time WOD complete only on the last tap, and Undo takes that tap back', () => {
    const fran = lib('wod-fran')
    const done = timerSession(runOf(fran, 3), 10000 + 300000, true)!
    expect(done).toMatchObject({ completed: true, rounds: 3 })
    const undone = undoneRun(runOf(fran, 3))
    expect(undone.taps).toBe(2)
    expect(timerSession(undone, 10000 + 300000, true)!.completed).toBe(false)
    expect(undoneRun(runOf(fran, 0)).taps).toBe(0)
  })

  it('shows the coming round with its heading in the rest of a Tabata', () => {
    const tabata = wod({
      id: 't',
      name: 'Turns',
      timer: { mode: 'tabata', work: 20, rest: 10, rounds: 4 },
      rounds: 1,
      scheme: [],
      items: [
        { exerciseId: 'burpee', amount: 0, unit: 'reps', load: 0 },
        { exerciseId: 'plank', amount: 20, unit: 'seconds', load: 0 },
      ],
    })
    const text = (seconds: number) => roundCard(tabata, 0, view(tabata, seconds), find, 'kg').map((line) => line.text)
    expect(text(5)).toEqual(['Max Burpee'])
    expect(roundHeading(tabata, 0, view(tabata, 25))).toBe('Up next · Round 2 of 4')
    expect(text(25)).toEqual(['20s Plank'])
    expect(text(35)).toEqual(['20s Plank'])
  })
})

describe('migrate to version 8', () => {
  it('adds no WODs to a version 7 state and keeps everything else', () => {
    const { wods: _, ...v7 } = { ...freshState('kg'), version: 7, nextRoutineId: 'B' }
    const migrated = migrate(structuredClone(v7))
    if (!isAppState(migrated)) throw new Error('not a valid state')
    expect(migrated.version).toBe(8)
    expect(migrated.wods).toEqual([])
    expect(migrated.nextRoutineId).toBe('B')
  })

  it('rejects a version 8 state without its WODs', () => {
    const { wods: _, ...broken } = freshState('kg')
    expect(isAppState(broken)).toBe(false)
  })

  it('brings imported WODs into their limits and leaves out what cannot be read', () => {
    const mine = { ...lib('wod-fran'), id: 'mine', name: 'x'.repeat(60) }
    const cleaned = cleanWods([
      mine,
      { ...mine, name: 'Same id' },
      lib('wod-cindy'),
      { id: 'broken', name: 'No timer', items: [] },
      'nope',
      { ...lib('wod-grace'), id: 'heavy', items: [{ exerciseId: 'snatch', amount: 5, unit: 'kilos', load: 900 }, 7] },
    ])
    expect(cleaned.map((entry) => entry.id)).toEqual(['mine', 'heavy'])
    expect(cleaned[0]).toEqual(wod(mine))
    expect(cleaned[0].name).toHaveLength(40)
    expect(cleaned[1].items).toEqual([{ exerciseId: 'snatch', amount: 5, unit: 'reps', load: 500 }])
  })
})
