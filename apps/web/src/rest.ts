import { computed, reactive, ref, watch } from 'vue'
import { canRate } from './domain/program'
import { adjustedRestEnd, restSetLog, type RestSet } from './domain/rest'
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
  /** The set the rest follows. */
  set: null as RestSet | null,
  /** The rest screen covers the workout. False when it is minimised to the bar. */
  expanded: false,
})

/**
 * The last set of an exercise, open on the rest screen without a rest so it can be rated.
 * Not saved: after a reload the workout shows without it.
 */
export const doneSet = ref<RestSet | null>(null)

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
      const { startedAt, endsAt, note, set, expanded } = rest
      localStorage.setItem(REST_KEY, JSON.stringify({ startedAt, endsAt, note, set, expanded }))
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

/** Three short beeps for an end, one for a start. */
export function beep(times: 1 | 3 = 3): void {
  if (!state.settings.sound || !audio) return
  void audio.resume()
  const start = audio.currentTime
  for (const offset of [0, 0.22, 0.44].slice(0, times)) {
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
export async function unlockSound(): Promise<void> {
  if (!state.settings.sound) return
  await loadVoices(audioContext())
}

/**
 * The app's one audio context, shared by the rest and the interval timers, so one tap unlocks both.
 * Call it from a tap handler the first time.
 */
export function audioContext(): AudioContext {
  audio ??= new AudioContext()
  void audio.resume()
  return audio
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

/**
 * Starts the rest after a set and opens the rest screen for it.
 * Call from a tap handler: browsers only allow sound after a user gesture.
 */
export function startRest(seconds: number, note: string, set: RestSet): void {
  void unlockSound()
  now.value = Date.now()
  rest.active = true
  rest.startedAt = now.value
  rest.endsAt = now.value + seconds * 1000
  rest.note = note
  rest.signalled = false
  rest.warned = seconds < WARN_MIN_REST_SECONDS
  rest.set = { ...set }
  rest.expanded = state.settings.restFullScreen !== false
  doneSet.value = null
  interval ??= setInterval(tick, 250)
  persist()
}

/** Makes the running rest longer, or shorter with negative seconds. It never ends before now. */
export function adjustRest(seconds: number): void {
  if (!rest.active) return
  now.value = Date.now()
  rest.endsAt = adjustedRestEnd(rest.endsAt, now.value, seconds)
  if (seconds > 0) {
    rest.signalled = false
    rest.warned = false
  } else if (rest.endsAt - now.value <= WARN_SECONDS * 1000) {
    // Cut into the last ten seconds: "ten seconds" would be said too late to be true.
    rest.warned = true
  }
  tick()
  persist()
}

/** The note was about the count as tapped. Once the count is corrected it would contradict it. */
export function clearRestNote(): void {
  rest.note = ''
  persist()
}

/** Ends the rest, and closes the rest screen when it shows a last set instead. */
export function stopRest(): void {
  doneSet.value = null
  rest.active = false
  rest.set = null
  rest.expanded = false
  clearInterval(interval)
  interval = undefined
  persist()
}

/** Picks the timer up again after a reload or after the phone closed the app mid-workout. */
function restore(): void {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(REST_KEY) ?? 'null')
    const data = saved as {
      startedAt?: unknown
      endsAt?: unknown
      note?: unknown
      set?: unknown
      expanded?: unknown
    } | null
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
    rest.set = isRestSet(data.set) ? data.set : null
    rest.expanded = data.expanded === true
    // A rest that ended while the app was closed must not beep on reopen.
    rest.signalled = Date.now() >= data.endsAt
    rest.warned = data.endsAt - Date.now() <= WARN_SECONDS * 1000
    interval ??= setInterval(tick, 250)
  } catch {
    // Unreadable timer data: start without a timer.
  }
}

function isRestSet(value: unknown): value is RestSet {
  const set = value as Partial<RestSet> | null
  return (
    typeof set?.exerciseId === 'string' &&
    Number.isInteger(set.exerciseIndex) &&
    Number.isInteger(set.setIndex)
  )
}

/** The exercise of the set the rest follows, while that set is still logged where it was. */
export const restLog = computed(() => (rest.active ? restSetLog(state.active, rest.set) : null))
export const restScreenOpen = computed(() => rest.expanded && restLog.value !== null)

export function openRestScreen(): void {
  if (!restLog.value) return
  rest.expanded = true
  persist()
}

export function minimiseRestScreen(): void {
  if (!rest.expanded) return
  rest.expanded = false
  persist()
}

/** The exercise of the last set on the rest screen, while that set is still logged where it was. */
export const doneLog = computed(() => restSetLog(state.active, doneSet.value))

/**
 * Opens the rest screen for the last set of an exercise, so it can be rated. Only when ratings and
 * the full-screen rest are on: with the full screen off, nothing covers the workout by itself.
 */
export function openSetDone(set: RestSet): void {
  const log = restSetLog(state.active, set)
  if (state.settings.trackRir === false || state.settings.restFullScreen === false) return
  if (log && canRate(log, set.setIndex)) doneSet.value = { ...set }
}

export function closeSetDone(): void {
  doneSet.value = null
}

restore()

// The set was cleared, its exercise removed or moved, or the workout ended: the rest screen
// has nothing left to show. The rest itself runs on.
watch(
  restLog,
  (log) => {
    if (log || !rest.set) return
    rest.set = null
    rest.expanded = false
    persist()
  },
  { immediate: true },
)

watch(doneLog, (log) => {
  if (!log) doneSet.value = null
})

// `click` and not `pointerdown`: a button pressed with the keyboard counts as well.
window.addEventListener('click', unlockOnTap, { capture: true })

export function formatClock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}
