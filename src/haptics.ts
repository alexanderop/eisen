import { state } from './store'

/** Vibration in milliseconds: one number is a single pulse, a list alternates pulse and pause. */
const PATTERNS = {
  /** A set logged or cleared. */
  tap: 10,
  /** One step on a stepper. */
  step: 6,
  /** A set logged with reps missed. */
  warning: [30, 80, 30],
  /** Every set of an exercise done with all reps. */
  success: [12, 60, 24],
  /** Workout finished. */
  done: [20, 60, 20, 60, 40],
} as const

export type Haptic = keyof typeof PATTERNS

export const hapticsSupported = typeof navigator !== 'undefined' && 'vibrate' in navigator

/** Call from a tap handler: browsers only vibrate after a user gesture. */
export function haptic(kind: Haptic): void {
  if (!hapticsSupported || !state.settings.haptics) return
  const pattern = PATTERNS[kind]
  navigator.vibrate(typeof pattern === 'number' ? pattern : [...pattern])
}
