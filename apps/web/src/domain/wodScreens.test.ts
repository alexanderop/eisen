import { describe, expect, it } from 'vitest'
import { catalogExercise } from './exercises'
import { DEFAULT_TIMER_CALLS, DEFAULT_TIMERS, timerSession, timerView, type TimerRun } from './timer'
import { cleanWods, freshState, isAppState, migrate } from './state'
import { wod, WOD_LIBRARY, type Wod } from './wod'
import { searchWods, suggestWods } from './wodSearch'
import {
  amountWheel,
  bannerMotions,
  cardLabel,
  endsRound,
  copyEdit,
  copyName,
  editOf,
  filterWods,
  isChanged,
  itemSummary,
  lastRun,
  noMatchText,
  loadText,
  loadWheel,
  newEdit,
  newItem,
  paceLine,
  parseScheme,
  readEdit,
  restCues,
  resultGap,
  roundCard,
  roundHeading,
  roundTag,
  runRest,
  runText,
  savedVersion,
  schemeChoice,
  searchRank,
  switchMode,
  typedParts,
  undoneRun,
  wodButton,
  WOD_EXERCISE_IDS,
  wodRecordIds,
} from './wodScreens'

const lib = (id: string): Wod => WOD_LIBRARY.find((entry) => entry.id === id)!
const find = catalogExercise
const exercise = (id: string) => catalogExercise(id)!

const runOf = (workout: Wod, taps = 0, splits?: number[]): TimerRun => ({
  id: 'r',
  name: workout.name,
  config: workout.timer,
  calls: DEFAULT_TIMER_CALLS,
  startedAt: 0,
  pausedAt: null,
  paused: 0,
  taps,
  wod: workout,
  ...(splits ? { splits } : {}),
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
    const best = timerSession(runOf(lib('wod-fran'), 6), 10000 + 292000, true)
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
      ...timerSession(runOf(fran, 6), 10000 + seconds * 1000, true)!,
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

describe('the banner of a WOD', () => {
  it('gives each movement a place once, in order, at most four, a figure where it has one', () => {
    const strips = (workout: Wod) => bannerMotions(workout).map((slot) => slot.strip)
    expect(strips(lib('wod-fran'))).toEqual(['thruster', 'pull-up'])
    expect(strips(lib('wod-venus'))).toEqual(['push-up', 'sit-up', 'air-squat'])
    // Murph runs twice: once in the banner.
    expect(strips(lib('wod-murph'))).toEqual(['run', 'pull-up', 'push-up', 'air-squat'])
    // Zeus has five movements and a rest: the first four, and a rest is no movement.
    expect(strips(lib('wod-zeus'))).toEqual(['handstand-push-up', 'pull-up', 'push-up', 'sit-up'])
    expect(bannerMotions(lib('wod-ares')).map((slot) => slot.exerciseId)).toEqual(['pullups', 'situps', 'sprint'])
    // A movement without a figure keeps its place, for its still picture.
    const mixed = {
      ...lib('wod-grace'),
      items: [
        { exerciseId: 'legExtension', amount: 10, unit: 'reps' as const, load: 0 },
        { exerciseId: 'pushups', amount: 10, unit: 'reps' as const, load: 0 },
      ],
    }
    expect(bannerMotions(mixed)).toEqual([
      { exerciseId: 'legExtension', strip: null },
      { exerciseId: 'pushups', strip: 'push-up' },
    ])
    // Not one figure: no banner, the card keeps its tile.
    expect(bannerMotions({ ...mixed, items: [mixed.items[0]] })).toEqual([])
    expect(bannerMotions({ ...mixed, items: [] })).toEqual([])
  })
})

describe('the search of the WODs page', () => {
  it('says which chip narrowed a search that found nothing', () => {
    expect(noMatchText(' fra ', 'all')).toBe('No WODs match “fra”')
    expect(noMatchText('fra', 'bodyweight')).toBe('No WODs match “fra” in No weights')
    expect(noMatchText('xyz', 'amrap')).toBe('No WODs match “xyz” in AMRAP')
  })

  it('suggests only what the chip leaves, so the list and the page agree', () => {
    const noWeights = filterWods(WOD_LIBRARY, 'bodyweight', find)
    // Fran needs a barbell: with "No weights" on, "fra" offers nothing and finds nothing.
    expect(suggestWods(noWeights, 'fra', find)).toEqual([])
    expect(searchWods(noWeights, 'fra', find)).toEqual([])
    expect(suggestWods(WOD_LIBRARY, 'fra', find).map((entry) => entry.text)).toEqual(['Fran'])
  })

  it('cuts a suggestion around what was typed, preferring the start of a word', () => {
    expect(typedParts('Push-ups', 'pu')).toEqual(['', 'Pu', 'sh-ups'])
    expect(typedParts('Handstand Push-up', 'pu')).toEqual(['Handstand ', 'Pu', 'sh-up'])
    expect(typedParts('Pistol Squat', 'qua')).toEqual(['Pistol S', 'qua', 't'])
    expect(typedParts('Fran', ' FR ')).toEqual(['', 'Fr', 'an'])
    expect(typedParts('Fran', 'xyz')).toEqual(['Fran', '', ''])
    expect(typedParts('Fran', '')).toEqual(['Fran', '', ''])
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

  it('says the round of a For Time after the phase word, not in the card', () => {
    const fran = lib('wod-fran')
    // Two items a round: the third tap is the first item of round two.
    expect(roundTag(fran, 1)).toBe('1/3')
    expect(roundTag(fran, 2)).toBe('2/3')
    expect(roundTag(fran, 6)).toBe('3/3')
    expect(roundHeading(fran, 2, view(fran, 60))).toBe('')
    expect(roundCard(fran, 2, view(fran, 60), find, 'kg').map((line) => line.text)).toEqual([
      '15 × Thruster 43kg',
      '15 × Pull-ups',
    ])
    expect(roundTag(lib('wod-murph'), 3)).toBe('')
    expect(roundTag(lib('wod-cindy'), 3)).toBe('')
    expect(roundHeading(lib('wod-grace'), 0, view(lib('wod-grace'), 60))).toBe('')
    expect(roundHeading(lib('wod-cindy'), 7, view(lib('wod-cindy'), 60))).toBe('')
    expect(roundHeading(lib('wod-chelsea'), 0, view(lib('wod-chelsea'), 125))).toBe('Round 3 of 30')
  })

  it('knows the tap that ends a round, for a stronger buzz', () => {
    const venus = lib('wod-venus')
    expect([1, 2, 3, 4, 6, 9, 12].map((taps) => endsRound(venus, taps))).toEqual([
      false,
      false,
      true,
      false,
      true,
      true,
      // The last tap ends the WOD, not a round.
      false,
    ])
    expect(endsRound(lib('wod-murph'), 5)).toBe(false)
    expect(endsRound(lib('wod-cindy'), 3)).toBe(false)
  })

  it('has one button that names the line a tap ticks off and what comes after it', () => {
    const venus = lib('wod-venus')
    expect(wodButton(venus, 0, find, 'kg')).toEqual({
      line: '50 × Push-ups',
      hint: 'tap when done · then 20 × Sit-ups',
      label: '50 × Push-ups done. Next: 20 × Sit-ups.',
    })
    expect(wodButton(venus, 2, find, 'kg')).toMatchObject({ line: '50 × Air Squat', hint: 'tap when done · then round 2' })
    expect(wodButton(venus, 11, find, 'kg')).toEqual({
      line: '50 × Air Squat',
      hint: 'tap when done · finishes the WOD',
      label: '50 × Air Squat done. Finishes the WOD.',
    })
    // The scheme gives each round its reps.
    expect(wodButton(lib('wod-fran'), 2, find, 'kg')).toMatchObject({
      line: '15 × Thruster 43kg',
      hint: 'tap when done · then 15 × Pull-ups',
    })
    // Zeus rests two minutes after its fifth movement; the rest itself takes no tap.
    const zeus = lib('wod-zeus')
    expect(wodButton(zeus, 4, find, 'kg')).toMatchObject({
      line: '50 × Air Squat',
      hint: 'tap when done · then rest 2:00',
      label: '50 × Air Squat done. Next: rest 2:00.',
    })
    expect(wodButton(zeus, 5, find, 'kg')).toMatchObject({ line: '10 × Handstand Push-up' })
    expect(wodButton(zeus, 19, find, 'kg')).toMatchObject({ hint: 'tap when done · finishes the WOD' })
    expect(wodButton(lib('wod-murph'), 4, find, 'kg')).toMatchObject({ line: '1.6 km Run' })
    expect(wodButton(lib('wod-grace'), 0, find, 'kg')).toMatchObject({ hint: 'tap when done · finishes the WOD' })
    expect(wodButton({ ...lib('wod-grace'), items: [] }, 0, find, 'kg')).toEqual({
      line: 'Done',
      hint: 'tap when finished',
      label: 'Done',
    })
  })

  it('runs a rest by itself after the tap before it, until it is over or skipped', () => {
    const zeus = { ...lib('wod-zeus'), items: lib('wod-zeus').items.map((item) => (item.exerciseId === 'rest' ? { ...item, amount: 10 } : item)) }
    const five = [10, 20, 30, 40, 50]
    expect(runRest(runOf(zeus, 4, five.slice(0, 4)), 45)).toBeNull()
    expect(runRest(runOf(zeus, 5, five), 53)).toEqual({ left: 7, total: 10 })
    expect(runRest({ ...runOf(zeus, 5, five), restSkipped: 5 }, 53)).toBeNull()
    // A skip belongs to the rest it was made in.
    expect(runRest({ ...runOf(zeus, 5, five), restSkipped: 4 }, 53)).toEqual({ left: 7, total: 10 })
    // Back brings a skipped rest back.
    expect('restSkipped' in undoneRun({ ...runOf(zeus, 5, five), restSkipped: 5 })).toBe(false)

    // In the rest the card shows the coming round, nothing ticked, under "Up next".
    const states = roundCard(zeus, 5, view(zeus, 53), find, 'kg', true).map((line) => line.state)
    expect(states).toEqual(['plain', 'plain', 'plain', 'plain', 'plain', 'plain'])
    expect(roundHeading(zeus, 5, view(zeus, 53), true)).toBe('Up next · Round 2 of 4')
    // At work the rest line is ticked off with the item before it, never the one to do.
    expect(roundCard(zeus, 4, view(zeus, 45), find, 'kg').map((line) => line.state)).toEqual([
      'done',
      'done',
      'done',
      'done',
      'current',
      'plain',
    ])
    const midway = { ...zeus, items: [zeus.items[0], zeus.items[5], zeus.items[1]] }
    expect(roundCard(midway, 1, view(midway, 15), find, 'kg').map((line) => line.state)).toEqual(['done', 'done', 'current'])
    expect(roundCard(midway, 1, view(midway, 12), find, 'kg', true).map((line) => line.state)).toEqual([
      'done',
      'done',
      'plain',
    ])
    expect(roundHeading(midway, 1, view(midway, 12), true)).toBe('Up next')
  })

  it('beeps and says go at the end of a rest, as the calls allow', () => {
    const zeus = { ...lib('wod-zeus'), items: lib('wod-zeus').items.map((item) => (item.exerciseId === 'rest' ? { ...item, amount: 10 } : item)) }
    const run = runOf(zeus, 5, [10, 20, 30, 40, 50])
    // The rest starts 50 s into the WOD, after the 10 s lead-in, and ends 10 s later.
    expect(restCues(run)).toEqual([
      { at: 67000, sound: 'beep' },
      { at: 68000, sound: 'beep' },
      { at: 69000, sound: 'beep' },
      { at: 70000, sound: 'beep-long' },
      { at: 70700, sound: 'go' },
    ])
    expect(restCues({ ...run, calls: { ...run.calls, beeps: false } })).toEqual([{ at: 70700, sound: 'go' }])
    expect(restCues({ ...run, calls: { ...run.calls, phases: false } })).toHaveLength(4)
    expect(restCues({ ...run, restSkipped: 5 })).toEqual([])
    expect(restCues(runOf(zeus, 4, [10, 20, 30, 40]))).toEqual([])
    // A rest shorter than the beeps keeps only those inside it.
    const short = { ...zeus, items: zeus.items.map((item) => (item.exerciseId === 'rest' ? { ...item, amount: 2 } : item)) }
    expect(restCues(runOf(short, 5, [10, 20, 30, 40, 50])).map((cue) => cue.at)).toEqual([61000, 62000, 62700])
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

  it('ticks off the items of every round, and starts the next round fresh after its last', () => {
    const venus = lib('wod-venus')
    const states = (taps: number) => roundCard(venus, taps, view(venus, 60), find, 'kg').map((line) => line.state)
    expect(states(0)).toEqual(['current', 'plain', 'plain'])
    expect(states(2)).toEqual(['done', 'done', 'current'])
    expect(roundTag(venus, 2)).toBe('1/4')
    // The tap on the last item of round one moves the card on.
    expect(states(3)).toEqual(['current', 'plain', 'plain'])
    expect(roundTag(venus, 3)).toBe('2/4')
    expect(states(4)).toEqual(['done', 'current', 'plain'])
    // Back from the first item of round two is the last item of round one.
    const back = undoneRun(runOf(venus, 3))
    expect(back.taps).toBe(2)
    expect(roundTag(venus, back.taps)).toBe('1/4')
    expect(states(back.taps)).toEqual(['done', 'done', 'current'])
    expect(states(11)).toEqual(['done', 'done', 'current'])
    expect(roundTag(venus, 11)).toBe('4/4')
  })

  it('names the exercise of every line, for its picture', () => {
    const murph = lib('wod-murph')
    expect(roundCard(murph, 0, view(murph, 60), find, 'kg').map((line) => line.exerciseId)).toEqual(
      murph.items.map((item) => item.exerciseId),
    )
    const fran = lib('wod-fran')
    expect(roundCard(fran, 2, view(fran, 60), find, 'kg').map((line) => line.exerciseId)).toEqual([
      'thruster',
      'pullups',
    ])
    // A For Time keeps the line of a deleted exercise: it still takes a tap.
    const gone = { ...fran, items: [{ ...fran.items[0], exerciseId: 'deleted' }, fran.items[1]] }
    expect(roundCard(gone, 0, view(gone, 60), find, 'kg')).toEqual([
      { text: '21 × Deleted exercise 43kg', state: 'current', exerciseId: 'deleted' },
      { text: '21 × Pull-ups', state: 'plain', exerciseId: 'pullups' },
    ])
    const cindy = lib('wod-cindy')
    const goneAmrap = { ...cindy, items: [{ ...cindy.items[0], exerciseId: 'deleted' }, cindy.items[1]] }
    expect(roundCard(goneAmrap, 0, view(goneAmrap, 60), find, 'kg')).toEqual([
      { text: '10 × Push-ups', state: 'plain', exerciseId: 'pushups' },
    ])
  })

  it('ends a For Time WOD complete only on the last tap, and Undo takes that tap back', () => {
    const fran = lib('wod-fran')
    const splits = [40, 75, 120, 150, 190, 212.5]
    const done = timerSession(runOf(fran, 6, splits), 10000 + 300000, true)!
    expect(done).toMatchObject({ completed: true, rounds: 3, splits })
    const undone = undoneRun(runOf(fran, 6, splits))
    expect(undone).toMatchObject({ taps: 5, splits: [40, 75, 120, 150, 190] })
    expect(timerSession(undone, 10000 + 300000, true)!.completed).toBe(false)
    expect(undoneRun(runOf(fran, 0, [])).splits).toEqual([])
    expect(undoneRun(runOf(fran, 0)).taps).toBe(0)
    expect('splits' in undoneRun(runOf(fran, 2))).toBe(false)
  })

  it('measures a For Time against the best run while it is going', () => {
    const fran = lib('wod-fran')
    const best = { ...timerSession(runOf(fran, 6), 10000 + 213000, true)!, splits: [40, 75, 120, 150, 190, 212.5] }
    const at = (seconds: number, splits: number[]) => paceLine(runOf(fran, splits.length, splits), best, view(fran, seconds))
    // In the lead-in only the best: nothing has started.
    expect(paceLine(runOf(fran, 0, []), best, timerView(fran.timer, 4000))).toEqual({
      best: 'Best 3:33',
      gap: null,
      tone: 'level',
      arrow: '',
    })
    expect(at(20, [])).toEqual({ best: 'Best 3:33', gap: 'On pace', tone: 'level', arrow: '' })
    expect(at(50, [35])).toEqual({ best: 'Best 3:33', gap: '5s ahead', tone: 'ahead', arrow: '▲' })
    // The current item overruns its time in the best run: the gap grows with the clock.
    expect(at(83, [35])).toMatchObject({ gap: '8s behind', tone: 'behind', arrow: '▼' })
    expect(at(95, [40, 90])).toMatchObject({ gap: '15s behind', tone: 'behind' })
    // A best from before splits were kept: only the best.
    const old = timerSession(runOf(fran, 6), 10000 + 213000, true)!
    expect(paceLine(runOf(fran, 1, [35]), old, view(fran, 50))).toEqual({
      best: 'Best 3:33',
      gap: null,
      tone: 'level',
      arrow: '',
    })
    expect(paceLine(runOf(fran, 0, []), null, view(fran, 50))).toEqual({
      best: 'First run: this sets your best',
      gap: null,
      tone: 'level',
      arrow: '',
    })
  })

  it('measures an AMRAP in rounds, and leaves an EMOM or a plain timer without a line', () => {
    const cindy = lib('wod-cindy')
    const best = { ...timerSession(runOf(cindy, 4), 10000 + 1200000, true)!, splits: [60, 125, 200, 290] }
    expect(paceLine(runOf(cindy, 0, []), best, view(cindy, 30))).toEqual({
      best: 'Best 4 rounds',
      gap: 'On pace',
      tone: 'level',
      arrow: '',
    })
    expect(paceLine(runOf(cindy, 0, []), best, view(cindy, 61))).toMatchObject({
      gap: '1 round behind',
      tone: 'behind',
      arrow: '▼',
    })
    expect(paceLine(runOf(cindy, 3, [50, 100, 128]), best, view(cindy, 130))).toMatchObject({
      gap: '1 round ahead',
      tone: 'ahead',
      arrow: '▲',
    })
    expect(paceLine(runOf(lib('wod-chelsea')), best, view(lib('wod-chelsea'), 30))).toBeNull()
    expect(paceLine({ ...runOf(cindy), wod: undefined }, best, view(cindy, 30))).toBeNull()
  })

  it('measures a finished run against the best before it', () => {
    const fran = lib('wod-fran')
    const timed = (seconds: number, taps = 6) => timerSession(runOf(fran, taps), 10000 + seconds * 1000, true)!
    expect(resultGap(timed(280), timed(292))).toEqual({ text: '12s faster than your best', better: true })
    expect(resultGap(timed(300), timed(292))).toEqual({ text: '8s slower than your best', better: false })
    expect(resultGap(timed(357), timed(292))).toEqual({ text: '1:05 slower than your best', better: false })
    expect(resultGap(timed(292), timed(292))).toEqual({ text: 'Same time as your best', better: false })
    // Stopped early, or nothing to measure against.
    expect(resultGap(timed(200, 4), timed(292))).toBeNull()
    expect(resultGap(timed(280), null)).toBeNull()

    const cindy = lib('wod-cindy')
    const rounds = (count: number) => timerSession(runOf(cindy, count), 10000 + 1200000, true)!
    expect(resultGap(rounds(20), rounds(18))).toEqual({ text: '2 rounds more than your best', better: true })
    expect(resultGap(rounds(17), rounds(18))).toEqual({ text: '1 round fewer than your best', better: false })
    expect(resultGap(rounds(18), rounds(18))).toEqual({ text: 'As many rounds as your best', better: false })
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
    expect(migrated.version).toBe(9)
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
