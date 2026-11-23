import type { Exercise } from './exercises'
import { wodFormat, wodName, type Wod } from './wod'

type Find = (id: string) => Exercise | undefined

/** Lower case, accents and punctuation gone, single spaces: what a search compares. */
export function searchText(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
}

/**
 * How well `text` answers `query`, both already through {@link searchText}:
 * 0 it starts with it, 1 one of its words starts with it, 2 it is somewhere inside. Null: not at all.
 * A query of several words matches when every word does; the weakest word decides.
 */
export function matchRank(text: string, query: string): number | null {
  if (query === '') return null
  if (text.startsWith(query)) return 0
  let worst = 0
  for (const word of query.split(' ')) {
    const rank = text.split(' ').some((part) => part.startsWith(word)) ? 1 : text.includes(word) ? 2 : null
    if (rank === null) return null
    worst = Math.max(worst, rank)
  }
  return worst
}

/** Names of the exercises a WOD uses, once each, in its order. Deleted exercises are left out. */
function movements(workout: Wod, find: Find): Exercise[] {
  const list: Exercise[] = []
  for (const item of workout.items) {
    const exercise = find(item.exerciseId)
    if (exercise && !list.includes(exercise)) list.push(exercise)
  }
  return list
}

/**
 * Where a WOD answers the query: its name first (0 to 2), then its format ("amrap", "21 15 9": 3),
 * then one of its movements (4 word start, 5 inside). Null when nothing matches.
 */
function wodRank(workout: Wod, query: string, find: Find): number | null {
  const name = matchRank(searchText(wodName(workout)), query)
  if (name !== null) return name
  if (matchRank(searchText(wodFormat(workout)), query) !== null) return 3
  let best: number | null = null
  for (const exercise of movements(workout, find)) {
    const rank = matchRank(searchText(exercise.name), query)
    if (rank !== null) best = Math.min(best ?? 9, rank <= 1 ? 4 : 5)
  }
  return best
}

/**
 * The WODs that answer a typed query, best first; ties keep the order given.
 * An empty query answers with all of them.
 */
export function searchWods(wods: Wod[], typed: string, find: Find): Wod[] {
  const query = searchText(typed)
  if (query === '') return wods
  return wods
    .map((workout, index) => ({ workout, index, rank: wodRank(workout, query, find) }))
    .filter((entry): entry is { workout: Wod; index: number; rank: number } => entry.rank !== null)
    .sort((a, b) => a.rank - b.rank || a.index - b.index)
    .map((entry) => entry.workout)
}

/** One line under the search field: a WOD to open, or a movement to search for. */
export type WodSuggestion =
  | { kind: 'wod'; id: string; text: string; detail: string }
  | { kind: 'movement'; id: string; text: string; detail: string }

export const MAX_SUGGESTIONS = 6

/**
 * What to offer while the lifter types: WODs whose name matches, then movements that match with
 * how many WODs use each. Only matches at the start of a word are offered: one typed letter that
 * is somewhere inside a name is no suggestion. Best first, at most {@link MAX_SUGGESTIONS}.
 */
export function suggestWods(wods: Wod[], typed: string, find: Find): WodSuggestion[] {
  const query = searchText(typed)
  if (query === '') return []
  const named = wods
    .map((workout, index) => ({ workout, index, rank: matchRank(searchText(wodName(workout)), query) }))
    .filter((entry) => entry.rank !== null && entry.rank <= 1)
    .sort((a, b) => (a.rank ?? 0) - (b.rank ?? 0) || a.index - b.index)
    .map(
      ({ workout }): WodSuggestion => ({
        kind: 'wod',
        id: workout.id,
        text: wodName(workout),
        detail: wodFormat(workout),
      }),
    )

  const used = new Map<Exercise, number>()
  for (const workout of wods) {
    for (const exercise of movements(workout, find)) used.set(exercise, (used.get(exercise) ?? 0) + 1)
  }
  const moves = [...used.entries()]
    .map(([exercise, count], index) => ({ exercise, count, index, rank: matchRank(searchText(exercise.name), query) }))
    .filter((entry) => entry.rank !== null && entry.rank <= 1)
    .sort((a, b) => (a.rank ?? 0) - (b.rank ?? 0) || b.count - a.count || a.index - b.index)
    .map(
      ({ exercise, count }): WodSuggestion => ({
        kind: 'movement',
        id: exercise.id,
        text: exercise.name,
        detail: `${count} ${count === 1 ? 'WOD' : 'WODs'}`,
      }),
    )
  return [...named, ...moves].slice(0, MAX_SUGGESTIONS)
}
