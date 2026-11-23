<script setup lang="ts">
/**
 * A running interval timer over the whole screen, and its result once it is over.
 * The phone lies on the floor two metres away: the colour says the phase, the digits the time,
 * and there is one obvious button.
 */
import { Check, Minus, Pause, Play, Plus, Undo2, Volume2 } from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { holdText } from '../domain/program'
import {
  countsRoundsByTap,
  OUTCOME_LABELS,
  TIMER_NAMES,
  timerOutcome,
  timerSummary,
  type TimerConfig,
} from '../domain/timer'
import { nextText } from '../domain/timerScreens'
import { bestText, findWod, isWodRecord, wodBest, wodFormat } from '../domain/wod'
import { roundCard, roundHeading, wodButton } from '../domain/wodScreens'
import { formatClock } from '../rest'
import { findExercise, state } from '../store'
import {
  adjustResultRounds,
  dismissResult,
  endTimer,
  finishTimer,
  pauseTimer,
  resumeTimer,
  startTimer,
  startWod,
  tapRound,
  timer,
  timerNow,
  undoFinish,
  untapRound,
  wakeSound,
} from '../timer'
import { useWakeLock } from '../wakeLock'
import ExerciseMotion from './ExerciseMotion.vue'
import { Button } from './ui'

useWakeLock()

const emit = defineEmits<{ done: [] }>()

/** "End" asks first, except in the lead-in where nothing was done yet. */
const confirming = ref(false)

const run = computed(() => timer.run)
const view = computed(() => timerNow.value)
const config = computed(() => run.value?.config)
const paused = computed(() => run.value?.pausedAt != null)
const ready = computed(() => view.value?.phase === 'ready')
/** The WOD being done, if this run is one. */
const workout = computed(() => run.value?.wod ?? null)
const tapped = computed(() => (config.value ? countsRoundsByTap(config.value.mode) : false))
/**
 * A For Time WOD has one big button that always moves on: a round, an item, or the whole WOD.
 * The tap on the last stops the clock, so it has no "Hold to finish".
 */
const stepped = computed(() => workout.value?.timer.mode === 'forTime')
const button = computed(() =>
  workout.value && run.value ? wodButton(workout.value, run.value.taps, findExercise, state.settings.unit) : null,
)
const forTime = computed(() => config.value?.mode === 'forTime')
/** After a reload the browser keeps the timer silent until the screen is tapped once. */
const needsTap = computed(() => timer.restored && !timer.sound)

const stage = computed(() => {
  if (paused.value) return 'paused'
  return view.value?.phase ?? 'work'
})

const PHASE_WORDS = { ready: 'Get ready', work: 'Work', rest: 'Rest', done: 'Done' } as const

const roundLine = computed(() => {
  const current = view.value
  if (!current || current.rounds === null || workout.value) return ''
  return `Round ${current.round} of ${current.rounds}`
})

const seconds = (ms: number) => Math.ceil(ms / 1000)

const clock = computed(() => {
  const current = view.value
  if (!current) return ''
  if (current.phase === 'ready') return String(seconds(current.phaseLeft ?? 0))
  if (forTime.value) return formatClock(Math.floor(current.elapsed / 1000))
  return formatClock(seconds(current.phaseLeft ?? 0))
})

const capLine = computed(() => {
  if (!forTime.value || !config.value || ready.value) return ''
  return config.value.work > 0 ? `Cap ${holdText(config.value.work)}` : 'No cap'
})

const RADIUS = 46
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

/**
 * The ring empties over the lead-in and every phase. A For Time counts up, so its ring grows
 * towards the cap, and without a cap it sweeps once a minute.
 */
const ring = computed(() => {
  const current = view.value
  if (!current) return 0
  if (current.phase === 'ready') return 1 - current.progress
  if (forTime.value) return current.phaseTotal === null ? (current.elapsed % 60000) / 60000 : current.progress
  return 1 - current.progress
})

/** A new phase starts with a full ring at once, not with the ring running back round. */
const ringKey = computed(() => (view.value ? `${view.value.phase}:${view.value.round}` : ''))

/** Left out where it would only repeat the digits or the next line: an AMRAP is one block. */
const totalLine = computed(() => {
  const left = view.value?.totalLeft
  if (left === null || left === undefined || config.value?.mode === 'amrap') return ''
  return `Total left ${formatClock(seconds(left))}`
})

const next = computed(() => (config.value && view.value ? nextText(config.value, view.value) : ''))

/** Lines past the four in sight. Ticked off to near the end, the list has scrolled to its last. */
const moreBelow = computed(() => {
  const current = lines.value.findIndex((line) => line.state === 'current')
  return lines.value.length > 4 && (current === -1 || current < lines.value.length - 3)
})

const linesList = ref<HTMLElement>()
/** The item to do now stays in view as the list is ticked off. */
watch(
  () => run.value?.taps,
  async () => {
    await nextTick()
    const list = linesList.value
    const current = list?.querySelector<HTMLElement>('.current')
    if (!list || !current) return
    // The last one done stays in sight above it, so the list reads as a list.
    const before = current.previousElementSibling as HTMLElement | null
    list.scrollTop = before ? before.offsetTop : 0
  },
  { immediate: true },
)

/** The round card of a WOD: what to do now, in place of the "Next" line. */
const heading = computed(() =>
  workout.value && run.value && view.value ? roundHeading(workout.value, run.value.taps, view.value) : '',
)
const lines = computed(() =>
  workout.value && run.value && view.value
    ? roundCard(workout.value, run.value.taps, view.value, findExercise, state.settings.unit).map((line) => ({
        ...line,
        exercise: findExercise(line.exerciseId),
      }))
    : [],
)
/** A single round ticked off in order: only the item to do now moves. */
const ticking = computed(() => lines.value.some((line) => line.state === 'current'))
/** In a WOD the time left sits under the phase. A For Time has its cap in the ring already. */
const wodTotal = computed(() => workout.value !== null && !forTime.value && totalLine.value !== '')
/** What the ring has to leave room for: the card shows up to four lines and scrolls the rest. */
const cardSpace = computed(() => ({
  '--shown': Math.min(4, Math.max(1, lines.value.length)),
  '--current': ticking.value ? 1 : 0,
  '--heading': heading.value ? 1 : 0,
  '--total': wodTotal.value ? 1 : 0,
}))

function onEnd() {
  if (!run.value) return
  if (ready.value) {
    endTimer(false)
    return
  }
  confirming.value = true
}

function onToggle() {
  if (paused.value) resumeTimer()
  else pauseTimer()
}

function onEndChoice(choice: 'save' | 'discard' | 'keep') {
  confirming.value = false
  if (choice !== 'keep') endTimer(choice === 'save')
}

// The run ran out while the question was open: there is nothing left to end.
watch(run, (current) => {
  if (!current) confirming.value = false
})

/** Finish ends a For Time for good, so a brush of the thumb must not do it: it is held down. */
const HOLD_MS = 700
const holding = ref(false)
/** Shown for a moment after a tap that was too short. */
const holdHint = ref(false)
let holdTimer: ReturnType<typeof setTimeout> | undefined
let hintTimer: ReturnType<typeof setTimeout> | undefined

function onHoldDown(event: PointerEvent) {
  if (ready.value || event.button !== 0) return
  ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
  holding.value = true
  holdHint.value = false
  clearTimeout(holdTimer)
  holdTimer = setTimeout(() => {
    holding.value = false
    finishTimer()
  }, HOLD_MS)
}

function onHoldUp() {
  if (!holding.value) return
  clearTimeout(holdTimer)
  holding.value = false
  holdHint.value = true
  clearTimeout(hintTimer)
  hintTimer = setTimeout(() => (holdHint.value = false), 1800)
}

/** A keyboard or a screen reader clicks without a pointer: that finishes at once. */
function onHoldClick(event: MouseEvent) {
  if (event.detail === 0 && !ready.value) finishTimer()
}

onBeforeUnmount(() => {
  clearTimeout(holdTimer)
  clearTimeout(hintTimer)
})

const result = computed(() => timer.result)
const outcome = computed(() => (result.value ? timerOutcome(result.value) : 'completed'))
const resultWod = computed(() => timer.resultWod)
/** A WOD For Time is about its time: that is the big number, and its rounds are not corrected. */
const resultForTime = computed(() => resultWod.value !== null && result.value?.config.mode === 'forTime')
const resultTapped = computed(() =>
  result.value && !resultForTime.value ? countsRoundsByTap(result.value.config.mode) : false,
)
/** Every other run in the history: the result is the newest, so they all came before it. */
const others = computed(() => state.timerSessions.filter((session) => session.id !== result.value?.id))
const record = computed(() => (result.value ? isWodRecord(others.value, result.value) : false))
const bestBefore = computed(() => (result.value?.wodId ? wodBest(others.value, result.value.wodId) : null))

/** "Tabata · 8 × 20s / 10s", or the numbers alone when the name already is the mode. */
function describe(name: string, timerConfig: TimerConfig): string {
  const mode = TIMER_NAMES[timerConfig.mode]
  return name === mode ? timerSummary(timerConfig) : `${mode} · ${timerSummary(timerConfig)}`
}

function roundsText(rounds: number): string {
  return `${rounds} ${rounds === 1 ? 'round' : 'rounds'}`
}

function onDone() {
  dismissResult()
  emit('done')
}

function onAgain() {
  const last = result.value
  const done = resultWod.value
  if (done) startWod(findWod(state.wods, done.id) ?? done)
  else if (last) startTimer(last.config, last.name)
}

/** The back button or gesture: the same as "End" on the run, "Done" on the result. */
function back() {
  if (result.value && !run.value) onDone()
  else if (confirming.value) confirming.value = false
  else onEnd()
}

const keepGoing = ref<InstanceType<typeof Button>>()
watch(confirming, async (open) => {
  if (!open) return
  await nextTick()
  ;(keepGoing.value?.$el as HTMLElement | undefined)?.focus()
})

defineExpose({ back })
</script>

<template>
  <div
    v-if="run && view"
    class="timer"
    :class="[stage, { compact: tapped || stepped, 'for-time': forTime, wod: workout }]"
    role="dialog"
    aria-modal="true"
    :aria-label="`${run.name} timer`"
    @keydown.esc="back"
  >
    <header>
      <h2 class="name">{{ run.name }}</h2>
      <Button size="md" variant="secondary" class="end" @click="onEnd">End</Button>
    </header>

    <button v-if="needsTap" class="sound" @click="wakeSound">
      <Volume2 :size="22" aria-hidden="true" />
      Tap for sound
    </button>

    <section class="centre" :style="workout ? cardSpace : undefined">
      <div class="phase-block">
        <p class="phase">{{ paused ? 'Paused' : PHASE_WORDS[view.phase] }}</p>
        <p v-if="roundLine" class="round">{{ roundLine }}</p>
        <p v-else-if="wodTotal" class="total">{{ totalLine }}</p>
      </div>
      <div class="dial">
        <svg viewBox="0 0 100 100" aria-hidden="true">
          <circle class="track" cx="50" cy="50" :r="RADIUS" />
          <circle
            :key="ringKey"
            class="fill"
            cx="50"
            cy="50"
            :r="RADIUS"
            :stroke-dasharray="CIRCUMFERENCE"
            :stroke-dashoffset="CIRCUMFERENCE * (1 - ring)"
          />
        </svg>
        <span class="time" :class="{ lead: ready, long: clock.length > 4 }" role="timer">{{ clock }}</span>
        <span v-if="capLine" class="below">{{ capLine }}</span>
      </div>
      <div v-if="workout" class="round-card">
        <p v-if="heading" class="round-heading">{{ heading }}</p>
        <ul ref="linesList" class="round-lines" :class="{ more: moreBelow }" aria-live="polite">
          <li v-for="(line, index) in lines" :key="index" :class="line.state">
            <ExerciseMotion
              v-if="line.exercise"
              class="line-tile"
              :exercise="line.exercise"
              :size="40"
              :playing="!paused && (!ticking || line.state === 'current')"
            />
            <span>
              <Check v-if="line.state === 'done'" class="tick" :size="18" :stroke-width="3" aria-label="Done:" />
              {{ line.text }}
            </span>
          </li>
        </ul>
      </div>
      <div v-else class="info">
        <p v-if="totalLine" class="total">{{ totalLine }}</p>
        <p v-if="next" class="next">{{ next }}</p>
      </div>
    </section>

    <div v-if="stepped && button" class="tap-area steps">
      <button class="back-step" :disabled="run.taps === 0 || ready" aria-label="Back one step" @click="untapRound">
        <Undo2 :size="22" aria-hidden="true" />
        Back
      </button>
      <button
        class="tap"
        :disabled="paused || ready"
        :aria-label="`${button.big}: ${button.small}`"
        @click="tapRound"
      >
        <span class="tap-big">{{ button.big }}</span>
        <span class="tap-small">{{ button.small }}</span>
      </button>
    </div>

    <div v-else-if="tapped" class="tap-area">
      <button
        class="tap"
        :disabled="paused || ready"
        :aria-label="`Add a round. ${roundsText(run.taps)} so far.`"
        @click="tapRound"
      >
        <span class="count">{{ run.taps }}</span>
        <span class="count-label">{{ workout ? 'rounds done' : run.taps === 1 ? 'round' : 'rounds' }}</span>
        <span class="tap-hint">+1 · tap</span>
      </button>
      <button
        class="minus"
        :disabled="run.taps === 0 || ready"
        aria-label="One round less"
        @click="untapRound"
      >
        −1
      </button>
    </div>

    <div class="controls">
      <button
        v-if="forTime && !workout"
        class="hold"
        :class="{ holding }"
        :disabled="ready"
        @pointerdown="onHoldDown"
        @pointerup="onHoldUp"
        @pointercancel="onHoldUp"
        @contextmenu.prevent
        @click="onHoldClick"
      >
        <span class="hold-fill" aria-hidden="true" />
        <span class="hold-label">{{ holdHint ? 'Keep holding to finish' : 'Hold to finish' }}</span>
      </button>
      <Button :variant="paused ? 'default' : 'secondary'" class="w-full big-button" @click="onToggle">
        <Play v-if="paused" :size="20" aria-hidden="true" />
        <Pause v-else :size="20" aria-hidden="true" />
        {{ paused ? 'Resume' : 'Pause' }}
      </Button>
    </div>

    <div v-if="confirming" class="confirm" @click.self="onEndChoice('keep')">
      <div class="panel" role="alertdialog" aria-modal="true" aria-labelledby="end-title">
        <h3 id="end-title">{{ workout ? 'End WOD?' : 'End timer?' }}</h3>
        <p class="muted">Save it to your history as stopped early, or throw it away.</p>
        <Button ref="keepGoing" class="w-full" @click="onEndChoice('keep')">Keep going</Button>
        <Button variant="secondary" class="w-full" @click="onEndChoice('save')">Save and end</Button>
        <Button variant="destructive" class="w-full" @click="onEndChoice('discard')">Discard</Button>
      </div>
    </div>
  </div>

  <div
    v-else-if="result"
    class="timer result"
    :class="outcome"
    role="dialog"
    aria-modal="true"
    :aria-label="`${result.name} result`"
    @keydown.esc="back"
  >
    <header class="result-head">
      <h2>{{ result.name }}</h2>
      <p class="muted">{{ resultWod ? wodFormat(resultWod) : describe(result.name, result.config) }}</p>
    </header>

    <section class="summary">
      <div class="chips">
        <p class="status">{{ OUTCOME_LABELS[outcome] }}</p>
        <p v-if="record" class="status record">New record</p>
      </div>
      <template v-if="resultTapped">
        <div class="rounds-edit">
          <button
            class="step"
            :disabled="result.rounds === 0"
            aria-label="One round less"
            @click="adjustResultRounds(-1)"
          >
            <Minus :size="24" aria-hidden="true" />
          </button>
          <p class="big" aria-live="polite">{{ result.rounds }}</p>
          <button class="step" aria-label="One round more" @click="adjustResultRounds(1)">
            <Plus :size="24" aria-hidden="true" />
          </button>
        </div>
        <p class="caption">{{ result.rounds === 1 ? 'round' : 'rounds' }}</p>
        <p v-if="bestBefore" class="best">Previous best {{ bestText(bestBefore) }}</p>
        <p class="second">{{ formatClock(result.seconds) }}</p>
      </template>
      <template v-else>
        <p class="big">{{ formatClock(result.seconds) }}</p>
        <p class="caption">total time</p>
        <p v-if="bestBefore" class="best">Previous best {{ bestText(bestBefore) }}</p>
        <p v-if="!resultForTime" class="second">{{ roundsText(result.rounds) }}</p>
        <p v-else-if="resultWod && resultWod.rounds > 1" class="second">
          {{ Math.min(result.rounds, resultWod.rounds) }} of {{ resultWod.rounds }} rounds
        </p>
      </template>
    </section>

    <Button v-if="timer.undo" variant="ghost" size="md" class="undo" @click="undoFinish">Not finished? Undo</Button>

    <div class="controls two">
      <Button variant="secondary" class="w-full big-button" @click="onAgain">Again</Button>
      <Button class="w-full big-button" @click="onDone">Done</Button>
    </div>
  </div>
</template>

<style scoped>
.timer {
  position: fixed;
  inset: 0;
  z-index: 70;
  max-width: 480px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: clamp(10px, 2dvh, 20px);
  padding: calc(env(safe-area-inset-top) + 12px) 16px calc(env(safe-area-inset-bottom) + 16px);
  overflow: hidden;
  --stage: var(--primary);
  --phase: clamp(26px, 5dvh, 40px);
  /* The phase colour glows behind the ring, so it reads from across the room. */
  background:
    radial-gradient(110% 55% at 50% 38%, color-mix(in srgb, var(--stage) 16%, transparent), transparent 72%),
    var(--background);
}

.ready {
  --stage: var(--warn);
}

.rest {
  --stage: var(--rest);
}

.paused {
  --stage: var(--muted-foreground);
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.name {
  min-width: 0;
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--muted-foreground);
  overflow-wrap: anywhere;
}

.end {
  flex: none;
  min-width: 72px;
}

.sound {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 56px;
  border-radius: 18px;
  background: var(--foreground);
  color: var(--background);
  font-size: var(--fs-md);
  font-weight: 800;
}

.centre {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: clamp(6px, 1.6dvh, 16px);
  text-align: center;
}

.phase-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.phase {
  color: var(--stage);
  font-size: var(--phase);
  line-height: 1;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

/* About 60% of the phase word: the second thing read from across the room. */
.round {
  font-size: clamp(17px, 3dvh, 24px);
  line-height: 1.1;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

/* Sized by the screen, and the numbers by the dial, so "10:00" fits a narrow phone too. */
.dial {
  position: relative;
  width: min(84vw, 380px, 44dvh);
  aspect-ratio: 1;
  container-type: inline-size;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.compact .dial {
  width: min(64vw, 300px, 28dvh);
}

.dial svg {
  position: absolute;
  inset: 0;
  transform: rotate(-90deg);
}

.dial circle {
  fill: none;
  stroke-width: 6;
}

.track {
  stroke: color-mix(in srgb, var(--stage) 18%, var(--border));
}

.fill {
  stroke: var(--stage);
  stroke-linecap: round;
  transition: stroke-dashoffset 200ms linear;
}

.time {
  position: relative;
  font-size: 30cqi;
  line-height: 1;
  font-weight: 900;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.04em;
}

.time.lead {
  font-size: 46cqi;
  color: var(--stage);
}

/* Between full and 60%, so the digits keep a contrast above 4.5:1 at their dimmest. */
.paused .time {
  animation: blink 1.6s ease-in-out infinite;
}

.below {
  position: absolute;
  top: 70%;
  font-size: 6.5cqi;
  font-weight: 700;
  color: var(--muted-foreground);
}

.info {
  min-height: 2.8em;
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: var(--fs-md);
  font-weight: 700;
}

.total {
  color: var(--muted-foreground);
  font-variant-numeric: tabular-nums;
}

.next {
  color: var(--foreground);
}

/* Room below, so a tap that slides off the count never lands on Pause. */
.tap-area {
  position: relative;
  flex: none;
  margin-bottom: max(8px, calc(28px - clamp(10px, 2dvh, 20px)));
}

.tap {
  width: 100%;
  height: clamp(132px, 30dvh, 260px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 28px;
  background: var(--stage);
  color: var(--primary-foreground);
  transition:
    transform 80ms ease,
    background-color 300ms ease;
}

.tap:active:not(:disabled) {
  transform: scale(0.97);
}

.tap:disabled {
  opacity: 1;
  background: var(--secondary);
  color: var(--muted-foreground);
}

.count {
  font-size: clamp(64px, 13dvh, 120px);
  line-height: 0.95;
  font-weight: 900;
  letter-spacing: -0.04em;
  font-variant-numeric: tabular-nums;
}

.count-label {
  font-size: var(--fs-md);
  font-weight: 800;
}

.tap-hint {
  margin-top: 4px;
  font-size: var(--fs-sm);
  font-weight: 700;
  opacity: 0.7;
}

.minus {
  position: absolute;
  top: 12px;
  left: 12px;
  width: 52px;
  height: 44px;
  border-radius: 14px;
  background: rgb(0 0 0 / 0.18);
  color: var(--primary-foreground);
  font-size: var(--fs-md);
  font-weight: 800;
}

.tap:disabled + .minus,
.minus:disabled {
  background: transparent;
  color: var(--muted-foreground);
  opacity: 0.5;
}

.controls {
  display: grid;
  gap: 10px;
}

.controls.two {
  grid-template-columns: 1fr 1fr;
}

/* Hit without looking, with a sweaty thumb. */
.big-button {
  height: 64px;
  border-radius: 18px;
  font-size: var(--fs-md);
}

.hold {
  position: relative;
  height: 52px;
  overflow: hidden;
  border-radius: 16px;
  background: var(--secondary);
  font-size: var(--fs-base-plus);
  font-weight: 800;
  touch-action: none;
  -webkit-touch-callout: none;
}

.hold-fill {
  position: absolute;
  inset: 0;
  background: color-mix(in srgb, var(--stage) 45%, var(--secondary));
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 150ms ease-out;
}

.holding .hold-fill {
  transform: scaleX(1);
  transition: transform 700ms linear;
}

.hold-label {
  position: relative;
}

@media (max-height: 700px) {
  .big-button {
    height: 56px;
  }

  .hold {
    height: 46px;
  }

  /* For Time stacks three controls under the ring: everything above them gives a little. */
  .for-time .dial {
    width: min(56vw, 260px, 24dvh);
  }

  .for-time .tap {
    height: clamp(112px, 24dvh, 240px);
  }

  .for-time .count {
    font-size: clamp(56px, 11dvh, 120px);
  }

  .timer {
    --phase: 26px;
  }

  .info {
    min-height: 0;
    font-size: var(--fs-base);
  }
}

/*
 * A WOD: the round card under the ring says what to do, in type that reads from the floor.
 * The ring takes what height is left once the card has its lines, and the clock stays the
 * biggest thing on screen: the button and the paddings give way first.
 */
.timer.wod .centre {
  container-type: size;
  --line: clamp(20px, 3.2dvh, 30px);
  /* A line is as high as its text or its 40px picture with a little air, whichever is taller. */
  --picture: 44px;
  --row: max(1.2 * var(--line), var(--picture));
  --room: calc(
    var(--phase) + var(--total) * 26px + var(--heading) * 24px + var(--shown) * (var(--row) + 0.02 * var(--line)) +
      var(--current) * 6.5px + 24px + 2 * clamp(6px, 1.6dvh, 16px)
  );
}

.timer.wod .dial {
  width: min(78vw, 360px, calc(100cqh - var(--room)));
}

/* Four digits get a bigger size than five, which must still clear the ring. */
.timer.wod .time:not(.lead, .long) {
  font-size: 34cqi;
}

.timer.wod .time.long {
  font-size: 28cqi;
}

.timer.wod .below {
  font-size: max(14px, 6.5cqi);
}

.round-card {
  flex: 0 1 auto;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 16px 10px;
  border: 1px solid var(--border);
  border-radius: 20px;
  background: color-mix(in srgb, var(--card) 88%, transparent);
}

.round-heading {
  color: var(--stage);
  font-size: var(--fs-base-plus);
  line-height: 1.25;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.round-lines {
  position: relative;
  flex: 0 1 auto;
  min-height: 0;
  max-height: calc(4 * var(--row) + var(--current) * 6.5px);
  margin: 0;
  padding: 0;
  list-style: none;
  overflow-y: auto;
  overscroll-behavior: contain;
  font-size: var(--line);
  line-height: 1.2;
  font-weight: 900;
  letter-spacing: -0.01em;
  scroll-behavior: smooth;
}

.round-lines li {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: var(--row);
  padding: 0 4px;
  text-align: left;
}

/* Ticked off in a single round: done ones step back, the one to do now stands out. */
.round-lines .done {
  color: var(--muted-foreground);
  font-weight: 700;
}

.round-lines .done .line-tile {
  opacity: 0.5;
}

.tick {
  display: inline;
  vertical-align: -2px;
  color: var(--stage);
}

.round-lines .current {
  margin: 2px 0;
  border-radius: 10px;
  background: color-mix(in srgb, var(--stage) 18%, transparent);
  font-size: max(22px, calc(var(--line) * 1.1));
}

/* More lines below the four in sight: the last one fades out, so the card reads as one that scrolls. */
.round-lines.more {
  mask-image: linear-gradient(to bottom, #000 calc(100% - 1em), transparent);
}

.timer.wod .tap {
  height: clamp(96px, 17dvh, 200px);
}

.timer.wod .count {
  font-size: clamp(44px, 8.5dvh, 88px);
}

/* The For Time button: what the tap does, big, and what it is for, small. Nothing overlaps. */
.steps {
  display: flex;
  gap: 10px;
}

.steps .tap {
  flex: 1;
  min-width: 0;
  gap: 4px;
  padding: 8px 16px;
}

.tap-big {
  font-size: clamp(28px, 5dvh, 44px);
  line-height: 1;
  font-weight: 900;
  letter-spacing: -0.02em;
}

.tap-small {
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: var(--fs-base-plus);
  font-weight: 700;
  opacity: 0.8;
}

.back-step {
  flex: none;
  width: 64px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border-radius: 20px;
  background: var(--secondary);
  font-size: var(--fs-sm);
  font-weight: 800;
}

@media (max-height: 700px) {
  .timer.wod {
    gap: 10px;
  }

  /* No pictures on a short screen: the clock keeps its size. */
  .timer.wod .centre {
    --picture: 0px;
  }

  .line-tile {
    display: none;
  }

  .round-lines li {
    justify-content: center;
    text-align: inherit;
  }

  .timer.wod .tap {
    height: clamp(72px, 11.5dvh, 200px);
  }

  .timer.wod .count {
    font-size: clamp(40px, 7dvh, 88px);
  }

  .timer.wod .tap-hint {
    display: none;
  }

  .timer.wod .tap-area {
    margin-bottom: 4px;
  }

  .timer.wod .big-button {
    height: 50px;
  }
}

/* "End timer?" rises over the run like a sheet. The run goes on underneath until it is answered. */
.confirm {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  background: rgb(0 0 0 / 0.6);
  animation: fade 160ms ease-out;
}

.panel {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 20px 16px calc(env(safe-area-inset-bottom) + 20px);
  border-radius: 24px 24px 0 0;
  background: var(--popover);
  border-top: 1px solid var(--border);
  animation: rise 200ms ease-out;
}

.panel h3 {
  font-size: var(--fs-xl);
  font-weight: 800;
}

.panel p {
  margin-bottom: 6px;
  font-size: var(--fs-base);
}

/* The result. */

.completed {
  --stage: var(--primary);
}

.capped,
.stopped {
  --stage: var(--warn);
}

.result-head {
  flex-direction: column;
  align-items: center;
  gap: 2px;
  margin-top: 12px;
  text-align: center;
}

.result-head h2 {
  font-size: var(--fs-xl);
  font-weight: 800;
}

.summary {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  text-align: center;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-bottom: 8px;
}

.status {
  padding: 6px 14px;
  border-radius: var(--r-full);
  background: color-mix(in srgb, var(--stage) 18%, transparent);
  color: var(--stage);
  font-size: var(--fs-base);
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.record {
  background: var(--primary);
  color: var(--primary-foreground);
}

/* The best before this run, to measure it by. */
.undo {
  align-self: center;
}

.best {
  margin-top: 6px;
  color: var(--muted-foreground);
  font-size: var(--fs-lg);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.big {
  min-width: 2ch;
  font-size: min(30vw, 22dvh, 140px);
  line-height: 1;
  font-weight: 900;
  letter-spacing: -0.04em;
  font-variant-numeric: tabular-nums;
}

.rounds-edit {
  display: flex;
  align-items: center;
  gap: 16px;
}

.step {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--secondary);
}

.caption {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--muted-foreground);
}

.second {
  margin-top: 14px;
  font-size: var(--fs-2xl);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

@keyframes blink {
  50% {
    opacity: 0.6;
  }
}

@keyframes fade {
  from {
    opacity: 0;
  }
}

@keyframes rise {
  from {
    transform: translateY(40px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .fill,
  .hold-fill {
    transition: none;
  }

  .paused .time,
  .confirm,
  .panel {
    animation: none;
  }
}
</style>
