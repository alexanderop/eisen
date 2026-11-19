import { computed, reactive, ref } from 'vue'
import { state } from './store'

/** Rest timer between sets. Based on timestamps so it stays right when the tab sleeps. */
export const rest = reactive({
  active: false,
  startedAt: 0,
  endsAt: 0,
  note: '',
  /** True once the end signal has fired. */
  signalled: false,
})

const now = ref(Date.now())
let interval: ReturnType<typeof setInterval> | undefined
let audio: AudioContext | undefined

export const restRemaining = computed(() =>
  Math.max(0, Math.ceil((rest.endsAt - now.value) / 1000)),
)
export const restOver = computed(() => rest.active && now.value >= rest.endsAt)
export const restOvertime = computed(() =>
  Math.max(0, Math.floor((now.value - rest.endsAt) / 1000)),
)
export const restFraction = computed(() => {
  const total = rest.endsAt - rest.startedAt
  if (total <= 0) return 1
  return Math.min(1, Math.max(0, (now.value - rest.startedAt) / total))
})

function beep(): void {
  if (!state.settings.sound || !audio) return
  const start = audio.currentTime
  for (const offset of [0, 0.22, 0.44]) {
    const oscillator = audio.createOscillator()
    const gain = audio.createGain()
    oscillator.frequency.value = 880
    gain.gain.setValueAtTime(0.0001, start + offset)
    gain.gain.exponentialRampToValueAtTime(0.25, start + offset + 0.02)
    gain.gain.exponentialRampToValueAtTime(0.0001, start + offset + 0.16)
    oscillator.connect(gain).connect(audio.destination)
    oscillator.start(start + offset)
    oscillator.stop(start + offset + 0.18)
  }
}

function tick(): void {
  now.value = Date.now()
  if (rest.active && !rest.signalled && now.value >= rest.endsAt) {
    rest.signalled = true
    beep()
    navigator.vibrate?.([200, 100, 200])
  }
}

/** Call from a tap handler: browsers only allow sound after a user gesture. */
export function startRest(seconds: number, note: string): void {
  if (state.settings.sound) {
    audio ??= new AudioContext()
    void audio.resume()
  }
  now.value = Date.now()
  rest.active = true
  rest.startedAt = now.value
  rest.endsAt = now.value + seconds * 1000
  rest.note = note
  rest.signalled = false
  interval ??= setInterval(tick, 250)
}

export function extendRest(seconds: number): void {
  if (!rest.active) return
  rest.endsAt = Math.max(rest.endsAt, Date.now()) + seconds * 1000
  rest.signalled = false
  tick()
}

export function stopRest(): void {
  rest.active = false
  clearInterval(interval)
  interval = undefined
}

export function formatClock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}
