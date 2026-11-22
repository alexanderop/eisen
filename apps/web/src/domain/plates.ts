import { roundTo } from './units'

export interface PlateLoad {
  /** Plates for one side of the bar, heaviest first. */
  plates: number[]
  /** Weight per side that the available plates cannot make up. */
  remainder: number
}

export function platesPerSide(total: number, bar: number, available: number[]): PlateLoad {
  let rest = (total - bar) / 2
  const plates: number[] = []
  if (rest <= 0) return { plates, remainder: 0 }

  for (const plate of [...available].sort((a, b) => b - a)) {
    while (rest >= plate - 1e-9) {
      plates.push(plate)
      rest -= plate
    }
  }
  return { plates, remainder: Math.max(0, roundTo(rest, 0.01)) }
}

export interface WarmupSet {
  weight: number
  reps: number
  sets: number
}

const RAMP: [fraction: number, reps: number][] = [
  [0.4, 5],
  [0.6, 3],
  [0.8, 2],
]

/**
 * Two sets with the empty bar, then lighter sets that ramp up to the work weight.
 * A machine has no bar, so it starts with the first ramp set.
 */
export function warmupSets(work: number, bar: number, step: number): WarmupSet[] {
  if (work <= bar) return []
  const result: WarmupSet[] = bar > 0 ? [{ weight: bar, reps: 5, sets: 2 }] : []
  let last = bar
  for (const [fraction, reps] of RAMP) {
    const weight = roundTo(work * fraction, step)
    if (weight > last && weight < work) {
      result.push({ weight, reps, sets: 1 })
      last = weight
    }
  }
  return result
}
