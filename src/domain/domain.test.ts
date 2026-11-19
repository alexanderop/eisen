import { describe, expect, it } from 'vitest'
import { platesPerSide, warmupSets } from './plates'
import {
  applyProgression,
  createWorkout,
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
    const workout = workoutWith({ squat: ALL, bench: ALL, row: ALL })
    const { progress, changes } = applyProgression(initialProgress('kg'), workout, 20)
    expect(progress.squat).toEqual({ weight: 22.5, fails: 0 })
    expect(progress.row.weight).toBe(32.5)
    expect(changes.map((c) => c.kind)).toEqual(['up', 'up', 'up'])
  })

  it('adds 5kg to the deadlift', () => {
    const workout = workoutWith({ squat: ALL, ohp: ALL, deadlift: [5] }, 'B')
    const { progress } = applyProgression(initialProgress('kg'), workout, 20)
    expect(progress.deadlift.weight).toBe(45)
  })

  it('repeats the weight after missed reps and leaves skipped exercises alone', () => {
    const workout = workoutWith({ squat: [5, 5, 5, 4, 3] })
    const { progress, changes } = applyProgression(initialProgress('kg'), workout, 20)
    expect(outcome(workout.exercises[0])).toBe('fail')
    expect(progress.squat).toEqual({ weight: 20, fails: 1 })
    expect(progress.bench).toEqual({ weight: 20, fails: 0 })
    expect(changes).toHaveLength(1)
  })

  it('counts an unfinished exercise as a miss', () => {
    const workout = workoutWith({ squat: [5, 5, null, null, null] })
    expect(outcome(workout.exercises[0])).toBe('fail')
  })

  it('deloads 10% on the third miss in a row', () => {
    const start = initialProgress('kg')
    start.squat = { weight: 100, fails: 2 }
    const workout = createWorkout('A', start, 'kg', 0, 'w1')
    workout.exercises[0].sets = [5, 4, 4, 3, 3]
    const { progress, changes } = applyProgression(start, workout, 20)
    expect(progress.squat).toEqual({ weight: 90, fails: 0 })
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
