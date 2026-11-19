import { describe, expect, it } from 'vitest'
import { platesPerSide, warmupSets } from './plates'
import {
  applyProgression,
  createWorkout,
  emptyWeight,
  initialProgress,
  nextReps,
  outcome,
  type Workout,
} from './program'

function workoutWith(sets: Record<string, (number | null)[]>, type: 'A' | 'B' = 'A'): Workout {
  const workout = createWorkout(type, initialProgress('kg'), 'kg', 0, 'w1')
  for (const log of workout.exercises) log.sets = sets[log.id] ?? log.sets
  return workout
}

const ALL = [5, 5, 5, 5, 5]

describe('nextReps', () => {
  it('cycles empty -> target -> down to 0 -> empty', () => {
    const seen: (number | null)[] = []
    let value: number | null = null
    for (let i = 0; i < 7; i++) {
      value = nextReps(value, 5)
      seen.push(value)
    }
    expect(seen).toEqual([5, 4, 3, 2, 1, 0, null])
  })
})

describe('applyProgression', () => {
  it('adds weight after a fully completed exercise', () => {
    const workout = workoutWith({ hackSquat: ALL, bench: ALL, row: ALL })
    const { progress, changes } = applyProgression(initialProgress('kg'), workout, 20)
    expect(progress.hackSquat).toEqual({ weight: 22.5, fails: 0 })
    expect(progress.row.weight).toBe(32.5)
    expect(changes.map((c) => c.kind)).toEqual(['up', 'up', 'up'])
  })

  it('adds 5kg to the deadlift', () => {
    const workout = workoutWith({ hackSquat: ALL, ohp: ALL, deadlift: [5] }, 'B')
    const { progress } = applyProgression(initialProgress('kg'), workout, 20)
    expect(progress.deadlift.weight).toBe(45)
  })

  it('repeats the weight after missed reps and leaves skipped exercises alone', () => {
    const workout = workoutWith({ hackSquat: [5, 5, 5, 4, 3] })
    const { progress, changes } = applyProgression(initialProgress('kg'), workout, 20)
    expect(outcome(workout.exercises[0])).toBe('fail')
    expect(progress.hackSquat).toEqual({ weight: 20, fails: 1 })
    expect(progress.bench).toEqual({ weight: 20, fails: 0 })
    expect(changes).toHaveLength(1)
  })

  it('counts an unfinished exercise as a miss', () => {
    const workout = workoutWith({ hackSquat: [5, 5, null, null, null] })
    expect(outcome(workout.exercises[0])).toBe('fail')
  })

  it('deloads 10% on the third miss in a row', () => {
    const start = initialProgress('kg')
    start.hackSquat = { weight: 100, fails: 2 }
    const workout = createWorkout('A', start, 'kg', 0, 'w1')
    workout.exercises[0].sets = [5, 4, 4, 3, 3]
    const { progress, changes } = applyProgression(start, workout, 20)
    expect(progress.hackSquat).toEqual({ weight: 90, fails: 0 })
    expect(changes[0].kind).toBe('deload')
  })
})

describe('platesPerSide', () => {
  const kg = [25, 20, 15, 10, 5, 2.5, 1.25]

  it('loads the heaviest plates first', () => {
    expect(platesPerSide(100, 20, kg)).toEqual({ plates: [25, 15], remainder: 0 })
    expect(platesPerSide(62.5, 20, kg)).toEqual({ plates: [20, 1.25], remainder: 0 })
  })

  it('returns no plates for the empty bar', () => {
    expect(platesPerSide(20, 20, kg).plates).toEqual([])
  })

  it('reports what the plates cannot make up', () => {
    expect(platesPerSide(21, 20, kg)).toEqual({ plates: [], remainder: 0.5 })
  })
})

describe('bodyweight exercises', () => {
  it('adds 3 max-reps sets of dips to A and pull-ups to B', () => {
    const a = createWorkout('A', initialProgress('kg'), 'kg', 0, 'a').exercises.at(-1)
    const b = createWorkout('B', initialProgress('kg'), 'kg', 0, 'b').exercises.at(-1)
    expect(a).toEqual({ id: 'dips', weight: 0, targetReps: null, sets: [null, null, null] })
    expect(b?.id).toBe('pullups')
  })

  it('counts any logged reps as done and never changes a weight', () => {
    const workout = workoutWith({ dips: [12, 9, 7] })
    expect(outcome(workout.exercises[3])).toBe('success')
    const { progress, changes } = applyProgression(initialProgress('kg'), workout, 20)
    expect(progress).toEqual(initialProgress('kg'))
    expect(changes).toEqual([])
  })
})

describe('hack squat machine', () => {
  it('has no bar, so every plate counts toward the weight', () => {
    expect(emptyWeight('hackSquat', 20)).toBe(0)
    expect(emptyWeight('bench', 20)).toBe(20)
    expect(platesPerSide(60, emptyWeight('hackSquat', 20), [25, 20, 15, 10, 5, 2.5, 1.25])).toEqual({
      plates: [25, 5],
      remainder: 0,
    })
  })

  it('warms up from the empty sled', () => {
    expect(warmupSets(100, 0, 2.5)[0]).toEqual({ weight: 0, reps: 5, sets: 2 })
  })
})

describe('warmupSets', () => {
  it('has no warmup for the empty bar', () => {
    expect(warmupSets(20, 20, 2.5)).toEqual([])
  })

  it('ramps up from the bar to below the work weight', () => {
    expect(warmupSets(100, 20, 2.5)).toEqual([
      { weight: 20, reps: 5, sets: 2 },
      { weight: 40, reps: 5, sets: 1 },
      { weight: 60, reps: 3, sets: 1 },
      { weight: 80, reps: 2, sets: 1 },
    ])
  })

  it('drops ramp sets that are not heavier than the bar', () => {
    expect(warmupSets(30, 20, 2.5)).toEqual([
      { weight: 20, reps: 5, sets: 2 },
      { weight: 25, reps: 2, sets: 1 },
    ])
  })
})
