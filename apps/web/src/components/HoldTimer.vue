<script setup lang="ts">
/**
 * The hold timer, over the whole screen. The phone often lies on the floor next to a plank,
 * so the time is as large as the screen allows.
 */
import { computed } from 'vue'
import { holdText } from '../domain/program'
import {
  cancelHold,
  heldSeconds,
  hold,
  holdLeft,
  holdReady,
  holdRemaining,
  leadInLeft,
  skipLeadIn,
  stopHold,
} from '../hold'
import { formatClock } from '../rest'
import { state } from '../store'
import { useWakeLock } from '../wakeLock'
import { Button } from './ui'

useWakeLock()

const log = computed(() => state.active?.exercises.find((entry) => entry.id === hold.exerciseId))

const RADIUS = 46
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

/** The ring empties with the lead-in and with a countdown. A max hold fills it once a minute. */
const ring = computed(() => {
  if (holdReady.value || hold.target !== null) return holdRemaining.value
  return (heldSeconds.value % 60) / 60
})

const clock = computed(() => {
  if (holdReady.value) return String(leadInLeft.value)
  return formatClock(hold.target === null ? heldSeconds.value : holdLeft.value)
})

const below = computed(() => {
  if (holdReady.value) return 'Tap to start now'
  return hold.target === null ? 'Max hold' : `of ${holdText(hold.target)}`
})
</script>

<template>
  <div
    class="hold"
    :class="holdReady ? 'ready' : 'holding'"
    role="dialog"
    aria-modal="true"
    :aria-label="`${log?.name ?? 'Hold'} timer`"
  >
    <header>
      <h2>{{ log?.name }}</h2>
      <p class="muted">Set {{ hold.setIndex + 1 }} of {{ log?.sets.length }}</p>
    </header>

    <p class="phase">{{ holdReady ? 'Get ready' : 'Hold' }}</p>

    <component
      :is="holdReady ? 'button' : 'div'"
      class="dial"
      :aria-label="holdReady ? `Starts in ${leadInLeft} seconds. Tap to start now.` : undefined"
      @click="holdReady && skipLeadIn()"
    >
      <svg viewBox="0 0 100 100" aria-hidden="true">
        <circle class="track" cx="50" cy="50" :r="RADIUS" />
        <circle
          class="fill"
          cx="50"
          cy="50"
          :r="RADIUS"
          :stroke-dasharray="CIRCUMFERENCE"
          :stroke-dashoffset="CIRCUMFERENCE * (1 - ring)"
        />
      </svg>
      <span class="time" role="timer">{{ clock }}</span>
      <span class="below">{{ below }}</span>
    </component>

    <div class="actions">
      <template v-if="!holdReady">
        <Button v-if="hold.target === null" class="w-full" @click="stopHold">Done</Button>
        <Button v-else variant="secondary" class="w-full" @click="stopHold">Stop</Button>
      </template>
      <Button variant="ghost" class="w-full" @click="cancelHold">Cancel</Button>
    </div>
  </div>
</template>

<style scoped>
.hold {
  position: fixed;
  inset: 0;
  z-index: 60;
  max-width: 480px;
  margin: 0 auto;
  background: var(--background);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: calc(env(safe-area-inset-top) + 24px) 16px calc(env(safe-area-inset-bottom) + 20px);
  text-align: center;
  --stage: var(--primary);
}

.ready {
  --stage: var(--warn);
}

header h2 {
  font-size: var(--fs-xl);
  font-weight: 800;
  overflow-wrap: anywhere;
}

header p {
  font-size: var(--fs-md);
  font-weight: 600;
}

.phase {
  color: var(--stage);
  font-size: var(--fs-2xl);
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

/* Sized by the screen, and the numbers by the dial, so "10:00" fits a narrow phone too. */
.dial {
  position: relative;
  width: min(86vw, 360px, 52dvh);
  aspect-ratio: 1;
  container-type: inline-size;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
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
  stroke: var(--border);
}

.fill {
  stroke: var(--stage);
  stroke-linecap: round;
  transition: stroke-dashoffset 250ms linear;
}

.time {
  position: relative;
  font-size: 27cqi;
  line-height: 1;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
}

.ready .time {
  font-size: 40cqi;
  color: var(--stage);
}

.below {
  position: relative;
  margin-top: 4cqi;
  font-size: 6cqi;
  font-weight: 600;
  color: var(--muted-foreground);
}

/* Room for two buttons in every phase, so the dial stays put when "Stop" appears. */
.actions {
  width: 100%;
  min-height: calc(var(--control-lg) * 2 + 8px);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 8px;
}

@media (prefers-reduced-motion: reduce) {
  .fill {
    transition: none;
  }
}
</style>
