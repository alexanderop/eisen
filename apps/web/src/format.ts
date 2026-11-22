import { platesPerSide } from './domain/plates'
import type { Equipment } from './domain/exercises'
import { emptyWeight, loadText, UNIT_DEFAULTS } from './domain/program'
import { formatWeight, type Unit } from './domain/units'
import { state } from './store'

export function fmt(value: number, unit: Unit = state.settings.unit): string {
  return formatWeight(value, unit)
}

/** How heavy an exercise is: "80kg", "BW" or "BW + 10kg". In the settings' unit unless given. */
export function fmtLoad(equipment: Equipment, weight: number, unit: Unit = state.settings.unit): string {
  return loadText(equipment === 'bodyweight', weight, unit)
}

/** The added weight of a bodyweight exercise on its own: "None" or "+10kg". */
export function fmtAdded(weight: number, unit: Unit = state.settings.unit): string {
  return weight > 0 ? `+${formatWeight(weight, unit)}` : 'None'
}

/** Under every "Added weight" stepper, so the lifter knows what it is for. */
export const ADDED_WEIGHT_HINT = 'For a dip belt or a weight vest. Counts on top of your bodyweight.'

const dateFormat = new Intl.DateTimeFormat(undefined, {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
})

export function formatDate(timestamp: number): string {
  return dateFormat.format(timestamp)
}

const shortDateFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' })

export function formatShortDate(timestamp: number): string {
  return shortDateFormat.format(timestamp)
}

export function emptyWeightFor(equipment: Equipment): number {
  return emptyWeight(equipment, state.settings.bar)
}

export function loadFor(weight: number, equipment: Equipment) {
  return platesPerSide(weight, emptyWeightFor(equipment), UNIT_DEFAULTS[state.settings.unit].plates)
}

/** "20 + 2.5 per side", "Empty bar", or a note when the plates cannot make the weight. */
export function platesText(weight: number, equipment: Equipment): string {
  if (weight < emptyWeightFor(equipment)) return 'Lighter than the bar'
  const load = loadFor(weight, equipment)
  if (load.plates.length === 0 && load.remainder === 0) {
    return equipment === 'machine' ? 'Empty sled' : 'Empty bar'
  }
  const parts = load.plates.map((plate) => String(plate))
  const text = parts.length > 0 ? `${parts.join(' + ')} per side` : 'No plates fit'
  return load.remainder > 0 ? `${text} (${fmt(load.remainder)} short)` : text
}
