<script setup lang="ts">
/**
 * The rest between two sets, over the whole screen: the time as large as it fits, a rating of the
 * reps in reserve, and wheels to correct the set just logged. Minimised it becomes the bar in the
 * workout header. After an exercise's last set there is no rest: the screen then shows only the
 * set, to rate and correct it.
 */
import { ChevronDown } from '@lucide/vue'
import { computed, onBeforeUnmount, ref } from 'vue'
import { canRate, holdText, loadText, rirOf } from '../domain/program'
import { nextOpenSet, repsWheel, setSummary, weightWheel, wheelWeightText } from '../domain/rest'
import { haptic } from '../haptics'
import {
  adjustRest,
  clearRestNote,
  closeSetDone,
  doneLog,
  doneSet,
  formatClock,
  minimiseRestScreen,
  rest,
  restFraction,
  restLog,
  restOver,
  restOvertime,
  restRemaining,
  stopRest,
} from '../rest'
import { correctReps, rateSet, setActiveWeight, state } from '../store'
import { useWakeLock } from '../wakeLock'
import RirPicker from './RirPicker.vue'
import { Button, Wheel } from './ui'

useWakeLock()

const ADJUST_SECONDS = 15
/** A rating is all that is left to do after a last set: the screen closes a moment after it. */
const CLOSE_AFTER_RATING_MS = 400

const setDone = computed(() => doneLog.value !== null)
const log = computed(() => doneLog.value ?? restLog.value)
const set = computed(() => (setDone.value ? doneSet.value : rest.set))
const exerciseIndex = computed(() => set.value?.exerciseIndex ?? 0)
const setIndex = computed(() => set.value?.setIndex ?? 0)
const unit = computed(() => state.active?.unit ?? state.settings.unit)
const bodyweight = computed(() => log.value?.equipment === 'bodyweight')
const logged = computed(() => log.value?.sets[setIndex.value] ?? 0)
const next = computed(() => (log.value ? nextOpenSet(log.value, setIndex.value) : null))

const weights = computed(() =>
  log.value ? weightWheel(log.value.equipment, unit.value, state.settings.bar, log.value.weight) : [],
)
const reps = computed(() => repsWheel(log.value?.targetReps ?? null, logged.value))

const rateable = computed(
  () => state.settings.trackRir !== false && log.value !== null && canRate(log.value, setIndex.value),
)
let closeTimer: ReturnType<typeof setTimeout> | undefined
/** Rating never touches the rest: it runs on as it was. */
const rating = computed({
  get: () => (log.value ? rirOf(log.value, setIndex.value) : null),
  set: (value: number | null) => {
    rateSet(exerciseIndex.value, setIndex.value, value)
    if (!setDone.value) return
    clearTimeout(closeTimer)
    if (value !== null) closeTimer = setTimeout(closeSetDone, CLOSE_AFTER_RATING_MS)
  },
})
onBeforeUnmount(() => clearTimeout(closeTimer))

const weight = computed({
  get: () => log.value?.weight ?? 0,
  set: (value: number) => setActiveWeight(exerciseIndex.value, value),
})
const count = computed({
  get: () => logged.value,
  set: (value: number) => {
    correctReps(exerciseIndex.value, setIndex.value, value)
    clearRestNote()
  },
})

const clock = computed(() =>
  restOver.value ? `+${formatClock(restOvertime.value)}` : formatClock(restRemaining.value),
)
const total = computed(() => Math.round((rest.endsAt - rest.startedAt) / 1000))
/** The bar drains: what is left of it is what is left of the rest. */
const left = computed(() => (restOver.value ? 0 : (1 - restFraction.value) * 100))

function onNext() {
  haptic('tap')
  stopRest()
}

/** Minimises a rest to the bar. A last set has no rest to keep, so its screen just closes. */
function close() {
  if (setDone.value) closeSetDone()
  else minimiseRestScreen()
}

/** Swipe down from anywhere but the wheels to minimise, like a sheet. */
const SWIPE_CLOSE_PX = 90
const pull = ref(0)
let pullFrom: number | null = null

function onTouchStart(event: TouchEvent) {
  const screen = event.currentTarget as HTMLElement
  const onWheel = (event.target as HTMLElement).closest('.wheel')
  pullFrom = onWheel || screen.scrollTop > 0 ? null : event.touches[0].clientY
}

function onTouchMove(event: TouchEvent) {
  if (pullFrom === null) return
  pull.value = Math.max(0, event.touches[0].clientY - pullFrom)
}

function onTouchEnd() {
  if (pull.value > SWIPE_CLOSE_PX) close()
  pull.value = 0
  pullFrom = null
}
</script>

<template>
  <div
    v-if="log"
    class="rest-screen"
    :class="{ over: !setDone && restOver, pulling: pull > 0, rated: rateable, done: setDone }"
    :style="pull > 0 ? { transform: `translateY(${pull}px)` } : undefined"
    role="dialog"
    aria-modal="true"
    :aria-label="setDone ? `${log.name} set done` : `Rest after ${log.name}`"
    @keydown.esc="close()"
    @touchstart.passive="onTouchStart"
    @touchmove.passive="onTouchMove"
    @touchend.passive="onTouchEnd"
    @touchcancel.passive="onTouchEnd"
  >
    <header>
      <div class="title">
        <h2>{{ log.name }}</h2>
        <p class="muted">Set {{ setIndex + 1 }} of {{ log.sets.length }} done</p>
      </div>
      <Button
        size="icon"
        variant="secondary"
        class="rounded-full text-muted-foreground"
        :aria-label="setDone ? 'Close' : 'Minimise the rest timer'"
        @click="close()"
      >
        <ChevronDown :size="22" aria-hidden="true" />
      </Button>
    </header>

    <section v-if="!setDone" class="time">
      <p class="phase">
        <span>{{ restOver ? 'Rest over' : 'Rest' }}</span>
        <span v-if="!restOver && rest.note" class="note">{{ rest.note }}</span>
      </p>
      <div class="digits" role="timer" :style="{ '--chars': clock.length }">{{ clock }}</div>
      <div class="track"><div class="fill" :style="{ width: `${left}%` }" /></div>
      <div class="row">
        <span class="muted of">of {{ formatClock(total) }}</span>
        <div class="adjust">
          <Button
            size="sm"
            variant="secondary"
            :disabled="restOver"
            :aria-label="`${ADJUST_SECONDS} seconds shorter`"
            @click="adjustRest(-ADJUST_SECONDS)"
          >
            −{{ ADJUST_SECONDS }}s
          </Button>
          <Button
            size="sm"
            variant="secondary"
            :aria-label="`${ADJUST_SECONDS} seconds longer`"
            @click="adjustRest(ADJUST_SECONDS)"
          >
            +{{ ADJUST_SECONDS }}s
          </Button>
        </div>
      </div>
    </section>

    <RirPicker v-if="rateable" v-model="rating" />

    <section class="lift">
      <h3>What did you lift?</h3>
      <div class="wheels">
        <Wheel
          v-model="weight"
          :values="weights"
          :header="bodyweight ? `+${unit}` : unit"
          :label="bodyweight ? 'Added weight' : 'Weight'"
          :format="(value: number) => wheelWeightText(bodyweight, value)"
          :spoken="(value: number) => loadText(bodyweight, value, unit)"
        />
        <Wheel
          v-if="!log.timed"
          v-model="count"
          :values="reps"
          header="reps"
          label="Reps"
          :spoken="(value: number) => `${value} reps`"
        />
        <div v-else class="held">
          <p class="header">held</p>
          <strong>{{ holdText(logged) }}</strong>
        </div>
      </div>
      <p class="summary">{{ setSummary(log, setIndex, unit) }}</p>
    </section>

    <div class="actions">
      <template v-if="setDone">
        <p class="muted">Exercise done</p>
        <Button class="w-full" @click="close()">Done</Button>
      </template>
      <template v-else>
        <p v-if="next !== null" class="muted">Up next: set {{ next + 1 }} of {{ log.sets.length }}</p>
        <Button class="w-full" @click="onNext">Next set</Button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.rest-screen {
  position: fixed;
  inset: 0;
  z-index: 50;
  max-width: 480px;
  margin: 0 auto;
  background: var(--background);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: clamp(8px, 2dvh, 20px);
  padding: calc(env(safe-area-inset-top) + 12px) 16px calc(env(safe-area-inset-bottom) + 16px);
  overflow-y: auto;
  overscroll-behavior: contain;
  --stage: var(--primary);
  animation: rise 220ms ease-out;
  transition: transform 200ms ease-out;
}

.pulling {
  transition: none;
}

.over {
  --stage: var(--warn);
}

/* No clock to spread around: the set sits under its name and the button stays at the bottom. */
.done {
  justify-content: flex-start;
}

.done header {
  margin-bottom: 12px;
}

.done .actions {
  margin-top: auto;
}

header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

header h2 {
  font-size: var(--fs-2xl);
  line-height: 1.1;
  font-weight: 800;
  letter-spacing: -0.02em;
  overflow-wrap: anywhere;
}

header p {
  margin-top: 2px;
  font-size: var(--fs-base);
  font-weight: 600;
}

.time {
  container-type: inline-size;
}

.phase {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  font-size: var(--fs-lg);
  font-weight: 800;
}

.over .phase {
  color: var(--stage);
}

.note {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--muted-foreground);
  text-align: right;
}

/*
 * As wide as the screen allows, and lower on a short phone so the wheels still fit.
 * The box keeps the height of "1:30", so the overtime's extra "+" moves nothing below it.
 */
.digits {
  --fit: calc(100cqi / (var(--chars) * 0.6));
  font-size: min(var(--fit), 20dvh);
  min-height: calc(min(100cqi / 2.4, 20dvh) * 0.9);
  display: flex;
  align-items: center;
  line-height: 0.9;
  font-weight: 900;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.04em;
  white-space: nowrap;
  margin: 2px 0 8px -0.04em;
}

/* The rating takes room above the wheels: the time gives it up, so "Next set" stays on screen. */
.rated {
  gap: clamp(8px, 1.5dvh, 16px);
}

.rated .digits {
  font-size: min(var(--fit), 12dvh);
  min-height: calc(min(100cqi / 2.4, 12dvh) * 0.9);
}

.over .digits {
  color: var(--stage);
}

.track {
  height: 10px;
  border-radius: var(--r-full);
  background: var(--secondary);
  overflow: hidden;
}

.fill {
  height: 100%;
  border-radius: inherit;
  background: var(--primary);
  transition: width 250ms linear;
}

.time .row {
  margin-top: 6px;
}

.of {
  font-size: var(--fs-base);
  font-weight: 600;
}

.adjust {
  display: flex;
  gap: 8px;
}

.lift {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--r-xl);
  background: var(--card);
}

.lift h3 {
  font-size: var(--fs-lg);
  font-weight: 800;
}

.wheels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.held {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.held .header {
  padding: 6px 0;
  text-align: center;
  font-size: var(--fs-base);
  font-weight: 700;
  background: var(--secondary);
  border-bottom: 1px solid var(--border);
}

.held strong {
  flex: 1;
  display: grid;
  place-items: center;
  font-size: 32px;
  font-weight: 800;
}

.summary {
  text-align: center;
  font-size: var(--fs-md);
  font-weight: 800;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: center;
  font-size: var(--fs-sm-plus);
  font-weight: 600;
}

/* A short phone: everything a little tighter, so "Next set" is on screen without scrolling. */
@media (max-height: 700px) {
  .rest-screen {
    gap: 8px;
  }

  header h2 {
    font-size: var(--fs-xl);
  }

  .phase {
    font-size: var(--fs-md);
  }

  .digits {
    font-size: min(var(--fit), 17dvh);
    min-height: calc(min(100cqi / 2.4, 17dvh) * 0.9);
  }

  .lift {
    gap: 8px;
    padding: 12px;
  }

  .lift h3 {
    font-size: var(--fs-md);
  }

  .rated {
    gap: 6px;
  }

  .rated .digits {
    font-size: min(var(--fit), 9dvh);
    min-height: calc(min(100cqi / 2.4, 9dvh) * 0.9);
    margin-bottom: 6px;
  }

  .rated .time .row {
    margin-top: 4px;
  }

  .rated:not(.done) .lift {
    gap: 6px;
    padding: 10px 12px;
  }

  .rated:not(.done) :deep(.wheel) {
    --row: 34px;
  }
}

@keyframes rise {
  from {
    transform: translateY(24px);
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .rest-screen {
    animation: none;
  }

  .fill {
    transition: none;
  }
}
</style>
