import { computed, reactive, ref, toRaw } from 'vue'
import {
  activeTime,
  MAX_OPEN_TIMER_SECONDS,
  TIMER_LEAD_IN_SECONDS,
  TIMER_MODES,
  timerConfig,
  timerCues,
  timerSeconds,
  timerSession,
  timerView,
  type TimerConfig,
  type TimerCue,
  type TimerRun,
  type TimerSession,
} from './domain/timer'
import { wod, wodDone, wodName, type Wod } from './domain/wod'
import { undoneRun } from './domain/wodScreens'
import { haptic } from './haptics'
import { cancelHold } from './hold'
import { audioContext, stopRest } from './rest'
import { addTimerSession, deleteTimerSession, newId, rememberTimerConfig, setTimerSessionRounds, state } from './store'

/**
 * The interval timer that runs, and the result once it is over.
 * Everything is worked out from timestamps like the rest timer, so a phone that put the app to
 * sleep shows the right time, and a reload picks the run up where it was.
 */
export const timer = reactive({
  run: null as TimerRun | null,
  /** The run that just ended, for the result screen. Null when it ended in the lead-in. */
  result: null as TimerSession | null,
  /** The WOD the result is a run of, so "Again" can start it once more. */
  resultWod: null as Wod | null,
  /** The run the last tap of a For Time WOD ended, while its result is open: "Undo" takes it back. */
  undo: null as TimerRun | null,
  /** The run came back after a reload: it has no sound until something is tapped. */
  restored: false,
  /** The audio clock runs, so the cues can be heard. */
  sound: false,
})

const TIMER_KEY = 'eisen:timer'
/** A cue the app slept through by more than this is left out, not played late. */
const LATE_MS = 1500
/** How far ahead the audio clock gets the cues. Hidden tabs tick about once a minute. */
const AHEAD_MS = 5 * 60 * 1000

const now = ref(Date.now())
let interval: ReturnType<typeof setInterval> | undefined

export const timerActive = computed(() => (timer.run ? activeTime(timer.run, now.value) : 0))
export const timerNow = computed(() => (timer.run ? timerView(timer.run.config, timerActive.value) : null))

// Sounds

/** The recordings there are, by cue name. A cue without one is skipped. */
const RECORDINGS: Record<string, string> = Object.fromEntries(
  Object.entries(
    import.meta.glob<string>('./assets/voice/timer/*.mp3', { eager: true, query: '?url', import: 'default' }),
  ).map(([path, url]) => [path.slice(path.lastIndexOf('/') + 1, -'.mp3'.length), url]),
)
/** Decoded recordings. Null for one that is missing or could not be read. */
const recordings = new Map<string, AudioBuffer | null>()
const decoding = new Map<string, Promise<void>>()

let context: AudioContext | undefined
let cues: TimerCue[] = []
/** Cues handed to the audio clock, played or still waiting, by their place in `cues`. */
const scheduled = new Map<number, { node: AudioScheduledSourceNode | null; when: number }>()

function isTone(sound: string): boolean {
  return sound === 'beep' || sound === 'beep-long'
}

function decode(names: string[]): Promise<unknown> {
  const audio = context
  if (!audio) return Promise.resolve()
  return Promise.all(
    names.map((name) => {
      if (recordings.has(name)) return undefined
      const url = RECORDINGS[name]
      if (!url) {
        recordings.set(name, null)
        return undefined
      }
      let pending = decoding.get(name)
      if (!pending) {
        pending = (async () => {
          try {
            const data = await (await fetch(url)).arrayBuffer()
            recordings.set(name, await audio.decodeAudioData(data))
          } catch {
            recordings.set(name, null)
          }
        })()
        decoding.set(name, pending)
      }
      return pending
    }),
  )
}

/** Gets the recordings of the running timer ready, then puts them on the audio clock. */
function prepare(): void {
  const names = [...new Set(cues.map((cue) => cue.sound).filter((sound) => !isTone(sound)))]
  void decode(names).then(syncSounds)
}

function tone(audio: AudioContext, when: number, frequency: number, length: number): AudioScheduledSourceNode {
  const oscillator = audio.createOscillator()
  const gain = audio.createGain()
  oscillator.frequency.value = frequency
  gain.gain.setValueAtTime(0.0001, when)
  gain.gain.exponentialRampToValueAtTime(0.4, when + 0.015)
  gain.gain.setValueAtTime(0.4, when + length - 0.05)
  gain.gain.exponentialRampToValueAtTime(0.0001, when + length)
  oscillator.connect(gain).connect(audio.destination)
  oscillator.start(when)
  oscillator.stop(when + length + 0.02)
  return oscillator
}

/** Plays a sound at `when` on the audio clock. Null when there is nothing to play, undefined while it loads. */
function play(audio: AudioContext, sound: string, when: number): AudioScheduledSourceNode | null | undefined {
  if (sound === 'beep') return tone(audio, when, 880, 0.12)
  if (sound === 'beep-long') return tone(audio, when, 1320, 0.5)
  const buffer = recordings.get(sound)
  if (buffer === undefined) return RECORDINGS[sound] ? undefined : null
  if (buffer === null) return null
  const source = audio.createBufferSource()
  source.buffer = buffer
  source.connect(audio.destination)
  source.start(when)
  return source
}

/** Puts the cues of the next minutes on the audio clock, timed from the run's timestamps. */
function syncSounds(): void {
  const run = timer.run
  const audio = context
  if (!run || run.pausedAt !== null || !audio || audio.state !== 'running') return
  const active = activeTime(run, Date.now())
  const base = audio.currentTime
  for (const [index, cue] of cues.entries()) {
    const wait = cue.at - active
    if (wait > AHEAD_MS) break
    if (scheduled.has(index) || wait < -LATE_MS) continue
    const when = base + Math.max(0, wait) / 1000
    const node = play(audio, cue.sound, when)
    if (node !== undefined) scheduled.set(index, { node, when })
  }
}

/** Takes back what the audio clock would still play. `all` also cuts off what plays right now. */
function silence(all: boolean): void {
  const time = context?.currentTime ?? 0
  for (const [index, entry] of scheduled) {
    const waiting = entry.when > time
    if (entry.node && (waiting || all)) {
      try {
        entry.node.stop()
      } catch {
        // Already over.
      }
    }
    if (waiting) scheduled.delete(index)
  }
}

/** Gets the sound going. Call from a tap: browsers only allow sound after a user gesture. */
function unlock(): void {
  if (context) {
    void context.resume()
    return
  }
  context = audioContext()
  timer.sound = context.state === 'running'
  // Back from a phone call or the lock screen: the clock stood still, so the cues are timed again.
  context.addEventListener('statechange', () => {
    timer.sound = context?.state === 'running'
    silence(false)
    syncSounds()
  })
}

/** Says "Halfway" once, so the lifter can set the volume. A beep when there is no recording. */
export async function playSample(): Promise<void> {
  unlock()
  const audio = context!
  await decode(['halfway'])
  const when = audio.currentTime + 0.05
  if (!play(audio, 'halfway', when)) tone(audio, when, 1320, 0.5)
}

// The run

function persist(): void {
  try {
    if (timer.run) localStorage.setItem(TIMER_KEY, JSON.stringify(timer.run))
    else localStorage.removeItem(TIMER_KEY)
  } catch {
    // Storage blocked: the timer still runs, it just does not survive a reload.
  }
}

/** The moment the run runs out by itself, pauses included. */
function endsAt(run: TimerRun): number {
  const seconds = timerSeconds(run.config) ?? MAX_OPEN_TIMER_SECONDS
  return run.startedAt + run.paused + (TIMER_LEAD_IN_SECONDS + seconds) * 1000
}

/** Files the run into the history, or not, and closes it. */
function close(session: TimerSession | null): void {
  if (session) addTimerSession(session)
  timer.result = session
  timer.resultWod = session ? (timer.run?.wod ?? null) : null
  timer.undo = null
  timer.run = null
  clearInterval(interval)
  interval = undefined
  persist()
}

/** Phase and round the vibration last went with, so each change vibrates once. */
let lastStep = ''

function tick(): void {
  now.value = Date.now()
  const run = timer.run
  if (!run) return
  const view = timerView(run.config, activeTime(run, now.value))
  if (view.phase === 'done') {
    const end = endsAt(run)
    if (now.value - end < LATE_MS) haptic('timerEnd')
    // The voice still says "Workout complete" over the result: what is on the audio clock stays.
    close(timerSession(run, end, true))
    return
  }
  const step = `${view.phase}:${view.round}`
  if (lastStep && step !== lastStep && run.pausedAt === null) {
    if (view.phase === 'work') haptic('timerWork')
    else if (view.phase === 'rest') haptic('timerRest')
  }
  lastStep = step
  syncSounds()
}

function begin(): void {
  cues = timerCues(timer.run!.config, timer.run!.calls)
  scheduled.clear()
  lastStep = ''
  interval ??= setInterval(tick, 200)
  tick()
  prepare()
}

function start(config: TimerConfig, name: string, workout?: Wod): void {
  unlock()
  stopRest()
  cancelHold()
  silence(true)
  timer.result = null
  timer.resultWod = null
  timer.undo = null
  timer.restored = false
  lastTap = 0
  timer.run = {
    id: newId(),
    name,
    config: timerConfig(config),
    calls: { ...state.settings.timerCalls },
    startedAt: Date.now(),
    pausedAt: null,
    paused: 0,
    taps: 0,
    ...(workout ? { wod: workout } : {}),
  }
  begin()
  persist()
}

/**
 * Starts a timer: the lead-in first, then the timer itself. Ends a running rest and closes a hold.
 * Call from a tap handler: browsers only allow sound after a user gesture.
 */
export function startTimer(config: TimerConfig, name: string): void {
  rememberTimerConfig(timerConfig(config))
  start(config, name)
}

/**
 * Starts a WOD like a timer, under its name. Its mode keeps the config the lifter last set:
 * a benchmark does not change what the timer cards open with.
 */
export function startWod(workout: Wod): void {
  const copy = wod(structuredClone(toRaw(workout)))
  start(copy.timer, wodName(copy), copy)
}

export function pauseTimer(): void {
  const run = timer.run
  if (!run || run.pausedAt !== null) return
  run.pausedAt = Date.now()
  silence(true)
  tick()
  persist()
}

export function resumeTimer(): void {
  const run = timer.run
  if (!run || run.pausedAt === null) return
  unlock()
  run.paused += Date.now() - run.pausedAt
  run.pausedAt = null
  tick()
  persist()
}

/** A second tap this soon after one is a double tap by accident, not a second round. */
const TAP_GUARD_MS = 800
let lastTap = 0

/**
 * One more round in AMRAP and For Time. In a For Time WOD a tap is a round or an item done, and
 * the one on the last stops the clock: the run is complete at that moment.
 */
export function tapRound(): void {
  const run = timer.run
  if (!run || run.pausedAt !== null || timerNow.value?.phase !== 'work') return
  const time = Date.now()
  if (time - lastTap < TAP_GUARD_MS) return
  lastTap = time
  run.taps += 1
  if (run.wod && wodDone(run.wod, run.taps)) {
    const ended = { ...toRaw(run) }
    finishTimer()
    timer.undo = ended
    return
  }
  haptic('tap')
  persist()
}

/** Takes back a round tapped by mistake. */
export function untapRound(): void {
  const run = timer.run
  if (!run || run.taps === 0) return
  run.taps -= 1
  haptic('tap')
  persist()
}

/** Corrects the rounds of the result just shown. It changes the entry in the history too. */
export function adjustResultRounds(delta: number): void {
  const result = timer.result
  if (!result) return
  const rounds = Math.max(0, result.rounds + delta)
  if (rounds === result.rounds) return
  result.rounds = rounds
  setTimerSessionRounds(result.id, rounds)
  haptic('tap')
}

/** The lifter is done with a For Time: it counts as completed. */
export function finishTimer(): void {
  const run = timer.run
  if (!run) return
  silence(true)
  haptic('timerEnd')
  close(timerSession(run, Date.now(), true))
}

/** Ends the run early. Saved it goes into the history as stopped early. Nothing is saved from the lead-in. */
export function endTimer(save: boolean): void {
  const run = timer.run
  if (!run) return
  silence(true)
  close(save ? timerSession(run, Date.now(), false) : null)
}

export function dismissResult(): void {
  timer.result = null
  timer.undo = null
}

/**
 * "Not finished? Undo": the last tap was too early. The saved run goes, the tap is taken back and
 * the run carries on. It runs on timestamps, so the clock shows the time that really passed.
 */
export function undoFinish(): void {
  const ended = timer.undo
  if (!ended || !timer.result) return
  deleteTimerSession(timer.result.id)
  unlock()
  silence(true)
  timer.result = null
  timer.resultWod = null
  timer.undo = null
  timer.restored = false
  timer.run = undoneRun(ended)
  begin()
  persist()
}

function isTimerRun(value: unknown): value is TimerRun {
  const run = value as Partial<TimerRun> | null
  return (
    typeof run?.id === 'string' &&
    typeof run.name === 'string' &&
    typeof run.startedAt === 'number' &&
    (run.pausedAt === null || typeof run.pausedAt === 'number') &&
    typeof run.paused === 'number' &&
    typeof run.taps === 'number' &&
    typeof run.calls === 'object' &&
    run.calls !== null &&
    TIMER_MODES.includes(run.config?.mode as TimerRun['config']['mode'])
  )
}

/** Picks the run up after a reload, or files it when it ran out while the app was closed. */
function restore(): void {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(TIMER_KEY) ?? 'null')
    if (!isTimerRun(saved)) {
      localStorage.removeItem(TIMER_KEY)
      return
    }
    const { wod: workout, ...rest } = saved
    timer.run = {
      ...rest,
      config: timerConfig(saved.config),
      ...(workout && Array.isArray(workout.items) ? { wod: wod(workout) } : {}),
    }
    timer.restored = true
    begin()
  } catch {
    // Unreadable timer data: start without a timer.
  }
}

restore()

/** Gets the sound of a run back that has none, after a reload. Call from a tap. */
export function wakeSound(): void {
  if (!timer.run || context?.state === 'running') return
  unlock()
  prepare()
}

// A reload has no sound until something is tapped: the first tap anywhere brings it back.
window.addEventListener('click', wakeSound, { capture: true })

// A phone that slept may have skipped ticks: catch up, and time the cues again from the clock.
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState !== 'visible' || !timer.run) return
  silence(false)
  tick()
})
