import { describe, expect, it } from 'vitest'
import { catalogExercise } from './exercises'
import { wod, WOD_LIBRARY } from './wod'
import { matchRank, searchText, searchWods, suggestWods } from './wodSearch'

const find = catalogExercise
const names = (typed: string) => searchWods(WOD_LIBRARY, typed, find).map((entry) => entry.name)

describe('searching WODs', () => {
  it('compares without case, accents or punctuation', () => {
    expect(searchText('  Pull-ups ')).toBe('pull ups')
    expect(searchText('Café  Röll')).toBe('cafe roll')
    expect(searchText('21-15-9 · For Time')).toBe('21 15 9 for time')
  })

  it('ranks a start above a word start above somewhere inside', () => {
    expect(matchRank('clean and jerk', 'clean')).toBe(0)
    expect(matchRank('clean and jerk', 'jerk')).toBe(1)
    expect(matchRank('cable crunch', 'run')).toBe(2)
    expect(matchRank('push ups', 'pull')).toBeNull()
    // Every word has to match; the weakest one decides.
    expect(matchRank('hang power clean', 'pow cl')).toBe(1)
    expect(matchRank('hang power clean', 'pow x')).toBeNull()
    expect(matchRank('anything', '')).toBeNull()
  })

  it('answers an empty search with everything', () => {
    expect(searchWods(WOD_LIBRARY, '  ', find)).toBe(WOD_LIBRARY)
  })

  it('finds a WOD by its name, best match first', () => {
    expect(names('fran')).toEqual(['Fran'])
    expect(names('MUR')).toEqual(['Murph'])
    // "ar": Ares starts with it, Mary and Karen and Barbara have it inside.
    expect(names('ar').slice(0, 4)).toEqual(['Ares', 'Karen', 'Barbara', 'Mary'])
  })

  it('finds WODs by what kind they are and by what is in them', () => {
    expect(names('amrap')).toEqual(['Cindy', 'Mary'])
    expect(names('21-15-9')).toEqual(['Fran', 'Diane'])
    expect(names('emom')).toEqual(['Chelsea'])
    expect(names('thruster')).toEqual(['Fran', 'Jackie'])
    expect(names('burpee')).toEqual(['Aphrodite', 'Helios'])
    expect(names('kettle')).toEqual(['Helen'])
    expect(names('zzz')).toEqual([])
  })

  it('puts a name match above a movement match', () => {
    const own = wod({ ...WOD_LIBRARY[0], id: 'mine', name: 'Pull day' })
    expect(searchWods([WOD_LIBRARY[1], own], 'pull', find).map((entry) => entry.name)).toEqual(['Pull day', 'Cindy'])
  })

  it('suggests WODs by name, then movements with how many WODs use them', () => {
    expect(suggestWods(WOD_LIBRARY, '', find)).toEqual([])
    expect(suggestWods(WOD_LIBRARY, 'fr', find)).toEqual([
      { kind: 'wod', id: 'wod-fran', text: 'Fran', detail: '21-15-9 · For Time' },
    ])
    const pull = suggestWods(WOD_LIBRARY, 'pu', find)
    expect(pull.map((entry) => entry.text)).toEqual(['Pull-ups', 'Push-ups', 'Push Jerk', 'Handstand Push-up'])
    expect(pull[0]).toEqual({ kind: 'movement', id: 'pullups', text: 'Pull-ups', detail: '11 WODs' })
    const a = suggestWods(WOD_LIBRARY, 'a', find)
    // "Fran" has an a inside: that is a search hit, not a suggestion.
    expect(a.map((entry) => entry.text)).toEqual(['Annie', 'Angie', 'Aphrodite', 'Ares', 'Air Squat', 'Clean and Jerk'])
  })
})
