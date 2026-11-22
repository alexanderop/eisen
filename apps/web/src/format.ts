import { platesPerSide } from './domain/plates'
import type { Equipment } from './domain/exercises'
import { emptyWeight, UNIT_DEFAULTS } from './domain/program'
import { formatWeight, type Unit } from './domain/units'
import { state } from './store'

export function fmt(value: number, unit: Unit = state.settings.unit): string {
  return formatWeight(value, unit)
}

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
