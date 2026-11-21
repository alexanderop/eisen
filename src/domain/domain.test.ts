import { describe, expect, it } from 'vitest'
import { CATALOG, catalogExercise, customExercise } from './exercises'
import { platesPerSide, warmupSets } from './plates'
import {
  applyProgression,
  convertProgress,
  createLog,
  createWorkout,
  defaultRoutines,
  defaultSlot,
  emptyWeight,
  nextReps,
  nextRoutineId,
  outcome,
  startProgress,
  warmupStep,
  type Progress,
  type Routine,
  type Workout,
} from './program'
import { workoutMarkdown } from './share'
import { freshState, isAppState, migrate } from './state'

function initialProgress(): Progress {
  return structuredClone(freshState('kg').progress)
}

function routine(id: 'A' | 'B'): Routine {
  return defaultRoutines().find((entry) => entry.id === id)!
}

function start(id: 'A' | 'B', progress: Progress = initialProgress()): Workout {
  return createWorkout(routine(id), catalogExercise, progress, 'kg', 0, 'w1')
}

function workoutWith(sets: Record<string, (number | null)[]>, type: 'A' | 'B' = 'A'): Workout {
  const workout = start(type)
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
    const { progress, changes } = applyProgression(initialProgress(), workout, 20)
    expect(progress.hackSquat).toEqual({ weight: 85, fails: 0, increment: 5 })
    expect(progress.bench.weight).toBe(82.5)
    expect(progress.row.weight).toBe(62.5)
    expect(changes.map((c) => c.kind)).toEqual(['up', 'up', 'up'])
  })

  it('adds 5kg to the deadlift', () => {
    const workout = workoutWith({ hackSquat: ALL, ohp: ALL, deadlift: ALL }, 'B')
    const { progress } = applyProgression(initialProgress(), workout, 20)
    expect(progress.deadlift.weight).toBe(105)
  })

  it('repeats the weight after missed reps and leaves skipped exercises alone', () => {
    const workout = workoutWith({ hackSquat: [5, 5, 5, 4, 3] })
    const { progress, changes } = applyProgression(initialProgress(), workout, 20)
    expect(outcome(workout.exercises[0])).toBe('fail')
    expect(progress.hackSquat).toEqual({ weight: 80, fails: 1, increment: 5 })
    expect(progress.bench).toEqual({ weight: 80, fails: 0, increment: 2.5 })
    expect(changes).toHaveLength(1)
  })

  it('counts an unfinished exercise as a miss', () => {
    const workout = workoutWith({ hackSquat: [5, 5, null, null, null] })
    expect(outcome(workout.exercises[0])).toBe('fail')
  })

  it('deloads 10% on the third miss in a row', () => {
    const before = initialProgress()
    before.hackSquat = { weight: 100, fails: 2, increment: 5 }
    const workout = start('A', before)
    workout.exercises[0].sets = [5, 4, 4, 3, 3]
    const { progress, changes } = applyProgression(before, workout, 20)
    expect(progress.hackSquat).toEqual({ weight: 90, fails: 0, increment: 5 })
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
    const a = start('A').exercises.at(-1)
    const b = start('B').exercises.at(-1)
    expect(a).toEqual({
      id: 'dips',
      name: 'Dips',
      equipment: 'bodyweight',
      weight: 0,
      targetReps: null,
      sets: [null, null, null],
    })
    expect(b?.id).toBe('pullups')
  })

  it('counts any logged reps as done and never changes a weight', () => {
    const workout = workoutWith({ dips: [12, 9, 7] })
    expect(outcome(workout.exercises[3])).toBe('success')
    const { progress, changes } = applyProgression(initialProgress(), workout, 20)
    expect(progress).toEqual(initialProgress())
    expect(changes).toEqual([])
  })
})

describe('hack squat machine', () => {
  it('has no bar, so every plate counts toward the weight', () => {
    expect(emptyWeight('machine', 20)).toBe(0)
    expect(emptyWeight('barbell', 20)).toBe(20)
    expect(platesPerSide(60, emptyWeight('machine', 20), [25, 20, 15, 10, 5, 2.5, 1.25])).toEqual({
      plates: [25, 5],
      remainder: 0,
    })
  })

  it('warms up a machine without an empty set and in whole plates', () => {
    expect(warmupSets(80, 0, warmupStep('machine', 'kg'))).toEqual([
      { weight: 30, reps: 5, sets: 1 },
      { weight: 50, reps: 3, sets: 1 },
      { weight: 65, reps: 2, sets: 1 },
    ])
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

describe('exercise catalog', () => {
  it('has unique ids', () => {
    expect(new Set(CATALOG.map((exercise) => exercise.id)).size).toBe(CATALOG.length)
  })

  it('starts bars on plate steps and dumbbells on finer ones, in both units', () => {
    expect(startProgress(catalogExercise('bench')!, 'lb')).toEqual({
      weight: 175,
      fails: 0,
      increment: 5,
    })
    expect(startProgress(catalogExercise('lateralRaise')!, 'kg').weight).toBe(6)
    expect(startProgress(catalogExercise('lateralRaise')!, 'lb')).toEqual({
      weight: 12.5,
      fails: 0,
      increment: 2.5,
    })
  })

  it('converts working weights and increments between units', () => {
    expect(convertProgress({ weight: 225, fails: 1, increment: 10 }, 'barbell', 'lb', 'kg')).toEqual(
      { weight: 102.5, fails: 1, increment: 5 },
    )
  })
})

describe('custom workouts', () => {
  const curl = customExercise('custom-1', 'Cable Curl', 'weight')
  const find = (id: string) => (id === curl.id ? curl : catalogExercise(id))
  const own: Routine = {
    id: 'r1',
    name: 'Pull day',
    slots: [
      defaultSlot(curl),
      { exerciseId: 'pushups', sets: 2, reps: 15 },
      { exerciseId: 'gone', sets: 3, reps: 10 },
    ],
  }

  it('builds a workout from any exercises and skips deleted ones', () => {
    const workout = createWorkout(own, find, {}, 'kg', 0, 'w')
    expect(workout.name).toBe('Pull day')
    expect(workout.exercises).toEqual([
      { id: 'custom-1', name: 'Cable Curl', equipment: 'weight', weight: 10, targetReps: 10, sets: [null, null, null] },
      { id: 'pushups', name: 'Push-ups', equipment: 'bodyweight', weight: 0, targetReps: 15, sets: [null, null] },
    ])
  })

  it('progresses a custom exercise by its own increment and never a bodyweight one', () => {
    const progress: Progress = { 'custom-1': { weight: 10, fails: 0, increment: 1 } }
    const workout = createWorkout(own, find, progress, 'kg', 0, 'w')
    workout.exercises[0].sets = [10, 10, 10]
    workout.exercises[1].sets = [15, 12]
    const result = applyProgression(progress, workout, 20)
    expect(result.progress).toEqual({ 'custom-1': { weight: 11, fails: 0, increment: 1 } })
    expect(result.changes.map((change) => change.name)).toEqual(['Cable Curl'])
  })

  it('rotates through the workouts in order', () => {
    const routines = [routine('A'), routine('B'), own]
    expect(nextRoutineId(routines, 'A', 'A')).toBe('B')
    expect(nextRoutineId(routines, 'r1', 'A')).toBe('A')
    expect(nextRoutineId(routines, 'deleted', 'B')).toBe('B')
  })
})

describe('migrate', () => {
  const v3 = {
    version: 3,
    settings: { unit: 'kg', bar: 20, restDone: 90, restMissed: 300, sound: true },
    progress: {
      hackSquat: { weight: 90, fails: 1 },
      bench: { weight: 82.5, fails: 0 },
      row: { weight: 60, fails: 0 },
      ohp: { weight: 40, fails: 0 },
      deadlift: { weight: 100, fails: 0 },
    },
    nextType: 'B',
    active: null,
    history: [
      {
        id: 'h1',
        type: 'A',
        unit: 'kg',
        startedAt: 1,
        finishedAt: 2,
        exercises: [
          { id: 'hackSquat', weight: 85, targetReps: 5, sets: [5, 5, 5, 5, 5] },
          { id: 'dips', weight: 0, targetReps: null, sets: [10, 8, 6] },
        ],
      },
    ],
  }

  it('turns the fixed A/B program into two editable workouts and keeps everything logged', () => {
    const migrated = migrate(structuredClone(v3))
    if (!isAppState(migrated)) throw new Error('not a valid state')
    expect(migrated.routines).toEqual(defaultRoutines())
    expect(migrated.nextRoutineId).toBe('B')
    expect(migrated.progress.hackSquat).toEqual({ weight: 90, fails: 1, increment: 5 })
    expect(migrated.progress.bench).toEqual({ weight: 82.5, fails: 0, increment: 2.5 })
    expect(migrated.history[0]).toMatchObject({ routineId: 'A', name: 'Workout A' })
    expect(migrated.history[0].exercises.map((log) => [log.name, log.equipment])).toEqual([
      ['Hack Squat', 'machine'],
      ['Dips', 'bodyweight'],
    ])
  })

  it('gives a deadlift still on one set all five, and leaves an edited one alone', () => {
    const slots = (deadlift: object) => [{ exerciseId: 'ohp', sets: 5, reps: 5 }, deadlift]
    const v4 = (deadlift: object) => ({
      ...freshState('kg'),
      version: 4,
      routines: [{ id: 'B', name: 'Workout B', slots: slots(deadlift) }],
    })
    const upgraded = migrate(v4({ exerciseId: 'deadlift', sets: 1, reps: 5 }))
    if (!isAppState(upgraded)) throw new Error('not a valid state')
    expect(upgraded.routines[0].slots).toEqual(slots({ exerciseId: 'deadlift', sets: 5, reps: 5 }))

    const edited = migrate(v4({ exerciseId: 'deadlift', sets: 3, reps: 3 }))
    if (!isAppState(edited)) throw new Error('not a valid state')
    expect(edited.routines[0].slots).toEqual(slots({ exerciseId: 'deadlift', sets: 3, reps: 3 }))
  })

  it('leaves a current state alone and rejects junk', () => {
    const fresh = freshState('kg')
    expect(migrate(fresh)).toBe(fresh)
    expect(isAppState(migrate({ version: 99 }))).toBe(false)
    expect(isAppState(migrate('nope'))).toBe(false)
  })
})

describe('workoutMarkdown', () => {
  it('lists the logged exercises and leaves out skipped ones', () => {
    const workout = workoutWith({ hackSquat: ALL, bench: [5, 5, 4, null, null], dips: [12, 9, 7] })
    workout.finishedAt = 50 * 60000
    expect(workoutMarkdown(workout, 'Thu, Nov 19')).toBe(
      [
        '## Workout A',
        '',
        'Thu, Nov 19 · 50 min',
        '',
        '- **Hack Squat** 80kg: 5×5',
        '- **Bench Press** 80kg: 5, 5, 4, –, –',
        '- **Dips** BW: 12, 9, 7',
      ].join('\n'),
    )
  })
})

describe('createLog', () => {
  it('starts an added exercise at its working weight with its default sets', () => {
    const curl = catalogExercise('barbellCurl')!
    const log = createLog(curl, defaultSlot(curl), { barbellCurl: { weight: 30, fails: 0, increment: 2.5 } }, 'kg')
    expect(log).toMatchObject({ id: 'barbellCurl', weight: 30, targetReps: 5, sets: [null, null, null, null, null] })
  })
})
