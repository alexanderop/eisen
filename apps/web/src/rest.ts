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
  /** True once "ten seconds left" was said, or when the rest is too short to say it. */
  warned: false,
})

/** Spoken cues. Recorded once with `scripts/voice.py`. */
const VOICE_URLS = {
  ten: new URL('./assets/voice/ten-seconds.mp3', import.meta.url).href,
  over: new URL('./assets/voice/rest-over.mp3', import.meta.url).href,
}
type Cue = keyof typeof VOICE_URLS
const WARN_SECONDS = 10
/** A shorter rest would get the warning right after it started. */
const WARN_MIN_REST_SECONDS = 20
const voices: Partial<Record<Cue, AudioBuffer>> = {}

/** Decoded into the same audio context as the beep, so one tap unlocks both. */
async function loadVoices(context: AudioContext): Promise<void> {
  for (const cue of Object.keys(VOICE_URLS) as Cue[]) {
    if (voices[cue]) continue
    try {
      const data = await (await fetch(VOICE_URLS[cue])).arrayBuffer()
      voices[cue] = await context.decodeAudioData(data)
    } catch {
      // No voice file: the end of the rest falls back to the beep.
    }
  }
}

/** Says a cue. False when it cannot, so the caller can fall back. */
function say(cue: Cue): boolean {
  const buffer = voices[cue]
  if (!state.settings.sound || !audio || !buffer) return false
  const source = audio.createBufferSource()
  source.buffer = buffer
  source.connect(audio.destination)
  source.start()
  return true
}

const REST_KEY = 'eisen:rest'

function persist(): void {
  try {
    if (rest.active) {
      const { startedAt, endsAt, note } = rest
      localStorage.setItem(REST_KEY, JSON.stringify({ startedAt, endsAt, note }))
    } else {
      localStorage.removeItem(REST_KEY)
    }
  } catch {
    // Storage blocked: the timer still runs, it just does not survive a reload.
  }
}

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

/**
 * Gets the sound ready. Call from a tap handler: browsers only allow sound after a user gesture.
 * Safe to call again: a phone suspends the sound when the app was in the background.
 */
async function unlockSound(): Promise<void> {
  if (!state.settings.sound) return
  audio ??= new AudioContext()
  void audio.resume()
  await loadVoices(audio)
}

function signalEnd(): void {
  void audio?.resume()
  if (!say('over')) beep()
}

/** Plays the end-of-rest sound once, so the lifter hears what switching it on does. */
export async function previewSound(): Promise<void> {
  await unlockSound()
  signalEnd()
}

/**
 * A timer that came back after a reload has no sound yet: nothing was tapped in this session.
 * The first tap anywhere gets it ready, so the end of that rest is still heard.
 */
function unlockOnTap(): void {
  if (rest.active) void unlockSound()
}

function tick(): void {
  now.value = Date.now()
  if (!rest.active) return
  const left = rest.endsAt - now.value
  if (!rest.warned && left > 0 && left <= WARN_SECONDS * 1000) {
    rest.warned = true
    say('ten')
  }
  if (!rest.signalled && left <= 0) {
    rest.signalled = true
    signalEnd()
    navigator.vibrate?.([300, 120, 300, 120, 300])
  }
}

/** Call from a tap handler: browsers only allow sound after a user gesture. */
export function startRest(seconds: number, note: string): void {
  void unlockSound()
  now.value = Date.now()
  rest.active = true
  rest.startedAt = now.value
  rest.endsAt = now.value + seconds * 1000
  rest.note = note
  rest.signalled = false
  rest.warned = seconds < WARN_MIN_REST_SECONDS
  interval ??= setInterval(tick, 250)
  persist()
}

export function extendRest(seconds: number): void {
  if (!rest.active) return
  rest.endsAt = Math.max(rest.endsAt, Date.now()) + seconds * 1000
  rest.signalled = false
  rest.warned = false
  tick()
  persist()
}

export function stopRest(): void {
  rest.active = false
  clearInterval(interval)
  interval = undefined
  persist()
}

/** Picks the timer up again after a reload or after the phone closed the app mid-workout. */
function restore(): void {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(REST_KEY) ?? 'null')
    const data = saved as { startedAt?: unknown; endsAt?: unknown; note?: unknown } | null
    if (
      !state.active ||
      typeof data?.startedAt !== 'number' ||
      typeof data.endsAt !== 'number' ||
      typeof data.note !== 'string'
    ) {
      localStorage.removeItem(REST_KEY)
      return
    }
    rest.active = true
    rest.startedAt = data.startedAt
    rest.endsAt = data.endsAt
    rest.note = data.note
    // A rest that ended while the app was closed must not beep on reopen.
    rest.signalled = Date.now() >= data.endsAt
    rest.warned = data.endsAt - Date.now() <= WARN_SECONDS * 1000
    interval ??= setInterval(tick, 250)
  } catch {
    // Unreadable timer data: start without a timer.
  }
}

restore()
// `click` and not `pointerdown`: a button pressed with the keyboard counts as well.
window.addEventListener('click', unlockOnTap, { capture: true })

export function formatClock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}
