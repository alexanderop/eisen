import { computed, reactive, ref, watch } from 'vue'
import { HOLD_LEAD_IN_SECONDS, MAX_HELD_SECONDS } from './domain/program'
import { haptic } from './haptics'
import { beep, startRest, stopRest, unlockSound } from './rest'
import { state } from './store'

/**
 * The timer of one set of a timed exercise: a lead-in, then the hold.
 * Based on timestamps like the rest timer, so a phone that put the app to sleep still logs the
 * right time. It logs the set itself, so it keeps working when the workout screen is closed.
 * A reload ends it.
 */
export const hold = reactive({
  active: false,
  exerciseId: '',
  setIndex: 0,
  /** Seconds to hold. null = as long as possible. */
  target: null as number | null,
  /** When the lead-in ends and the hold starts. */
  startsAt: 0,
  /** True once the start cue was given. */
  started: false,
})

const now = ref(Date.now())
let interval: ReturnType<typeof setInterval> | undefined

export const holdReady = computed(() => now.value < hold.startsAt)
export const leadInLeft = computed(() => Math.max(0, Math.ceil((hold.startsAt - now.value) / 1000)))
/** Whole seconds held so far. */
export const heldSeconds = computed(() => Math.max(0, Math.floor((now.value - hold.startsAt) / 1000)))
export const holdLeft = computed(() =>
  hold.target === null ? 0 : Math.max(0, hold.target - Math.floor((now.value - hold.startsAt) / 1000)),
)
/** What is left of the lead-in or of a hold with a target, from 1 down to 0. */
export const holdRemaining = computed(() => {
  const end = holdReady.value ? hold.startsAt : hold.startsAt + (hold.target ?? 0) * 1000
  const total = holdReady.value ? HOLD_LEAD_IN_SECONDS * 1000 : (hold.target ?? 0) * 1000
  if (total <= 0) return 0
  return Math.min(1, Math.max(0, (end - now.value) / total))
})

function close(): void {
  hold.active = false
  clearInterval(interval)
  interval = undefined
}

/**
 * Logs the held seconds into the running workout. Rest follows as after any set:
 * longer when the hold was cut short, none after the last set of the exercise.
 */
function log(seconds: number, note: string, full: boolean): void {
  const { exerciseId, setIndex } = hold
  close()
  const exerciseIndex = state.active?.exercises.findIndex((item) => item.id === exerciseId) ?? -1
  const entry = state.active?.exercises[exerciseIndex]
  if (!entry || seconds < 1) return
  entry.sets[setIndex] = seconds
  if (entry.sets.every((value) => value !== null)) {
    stopRest()
    return
  }
  startRest(full ? state.settings.restDone : state.settings.restMissed, note, {
    exerciseId,
    exerciseIndex,
    setIndex,
  })
}

function signalEnd(): void {
  beep(3)
  haptic('holdEnd')
}

function tick(): void {
  now.value = Date.now()
  if (!hold.active) return
  const held = now.value - hold.startsAt
  if (hold.target !== null && held >= hold.target * 1000) {
    signalEnd()
    log(hold.target, 'Full hold done.', true)
    return
  }
  if (hold.target === null && held >= MAX_HELD_SECONDS * 1000) {
    signalEnd()
    log(MAX_HELD_SECONDS, 'Max hold logged.', true)
    return
  }
  if (!hold.started && held >= 0) {
    hold.started = true
    beep(1)
    haptic('holdStart')
  }
}

/** Opens the timer for a set. Call from a tap handler: browsers only allow sound after a user gesture. */
export function startHold(exerciseId: string, setIndex: number, target: number | null): void {
  void unlockSound()
  stopRest()
  now.value = Date.now()
  Object.assign(hold, {
    active: true,
    exerciseId,
    setIndex,
    target,
    startsAt: now.value + HOLD_LEAD_IN_SECONDS * 1000,
    started: false,
  })
  interval ??= setInterval(tick, 250)
}

export function skipLeadIn(): void {
  if (!hold.active || !holdReady.value) return
  hold.startsAt = Date.now()
  tick()
}

/**
 * "Stop" on a countdown logs the seconds held so far, a set with missed time.
 * "Done" on a max hold logs them as the result. Under a second logs nothing.
 */
export function stopHold(): void {
  if (!hold.active || holdReady.value) return
  now.value = Date.now()
  const seconds = Math.min(heldSeconds.value, hold.target ?? MAX_HELD_SECONDS)
  if (hold.target === null) {
    haptic('tap')
    log(seconds, 'Max hold logged.', true)
  } else {
    haptic('warning')
    log(seconds, 'Hold cut short. Rest longer.', false)
  }
}

export function cancelHold(): void {
  close()
}

// A phone that slept may have skipped ticks: catch up the moment the app is back.
document.addEventListener('visibilitychange', tick)

// Finishing or discarding the workout, or removing or moving an exercise, ends the hold unlogged.
watch(
  () => state.active?.exercises.map((entry) => entry.id).join('\n'),
  () => {
    if (hold.active) close()
  },
)
