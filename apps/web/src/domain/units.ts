export type Unit = 'kg' | 'lb'

const LB_PER_KG = 2.20462

export function roundTo(value: number, step: number): number {
  return Math.round(value / step) * step
}

export function convert(value: number, from: Unit, to: Unit): number {
  if (from === to) return value
  return from === 'kg' ? value * LB_PER_KG : value / LB_PER_KG
}

export function formatWeight(value: number, unit: Unit): string {
  return `${Number(value.toFixed(2))}${unit}`
}
