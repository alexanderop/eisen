import { state } from './store'

/** Vibration in milliseconds: one number is a single pulse, a list alternates pulse and pause. */
const PATTERNS = {
  /** A set logged or cleared. */
  tap: 10,
  /** One step on a stepper. */
  step: 6,
  /** A set rated with its reps in reserve. */
  rate: 8,
  /** A set logged with reps missed. */
  warning: [30, 80, 30],
  /** Every set of an exercise done with all reps. */
  success: [12, 60, 24],
  /** Workout finished. */
  done: [20, 60, 20, 60, 40],
  /** A hold starts after the lead-in. */
  holdStart: 60,
  /** A hold ran its full time. Long enough to feel with the phone on the floor. */
  holdEnd: [300, 120, 300, 120, 300],
  /** A round of a WOD ticked off: stronger than a tap, felt with the phone on the floor. */
  round: [60, 80, 120],
  /** An interval timer's work starts. */
  timerWork: 80,
  /** An interval timer's rest starts. */
  timerRest: [80, 120, 80],
  /** The boxing coach calls a combination: short, felt through the floor or a pocket. */
  call: 30,
  /** An interval timer is over. */
  timerEnd: 600,
} as const

export type Haptic = keyof typeof PATTERNS

export const hapticsSupported = typeof navigator !== 'undefined' && 'vibrate' in navigator

/** Browsers only vibrate once the page was tapped: call from a tap handler or after one. */
export function haptic(kind: Haptic): void {
  if (!hapticsSupported || !state.settings.haptics) return
  const pattern = PATTERNS[kind]
  navigator.vibrate(typeof pattern === 'number' ? pattern : [...pattern])
}
