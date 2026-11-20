<script setup lang="ts">
import {
  extendRest,
  formatClock,
  rest,
  restFraction,
  restOver,
  restOvertime,
  restRemaining,
  stopRest,
} from '../rest'
import { Button } from './ui'
import { computed } from 'vue'

/** The last seconds of a rest change colour, so a glance from the bar is enough. */
const SOON_SECONDS = 10
const stage = computed(() => {
  if (restOver.value) return 'over'
  return restRemaining.value <= SOON_SECONDS ? 'soon' : 'calm'
})
/** The bar drains: what is left of it is what is left of the rest. */
const left = computed(() => (restOver.value ? 100 : (1 - restFraction.value) * 100))
</script>

<template>
  <aside v-if="rest.active" class="timer" :class="stage" role="timer" aria-live="off">
    <div class="wash" :style="{ width: `${left}%` }" />
    <div class="body">
      <div class="clock">
        {{ restOver ? `+${formatClock(restOvertime)}` : formatClock(restRemaining) }}
      </div>
      <p class="note">{{ restOver ? 'Rest is over. Start your next set.' : rest.note }}</p>
      <button v-if="!restOver" class="extend" @click="extendRest(30)">+30s</button>
      <Button size="icon" variant="secondary" class="bg-card text-muted-foreground" aria-label="Dismiss timer" @click="stopRest()">
        ✕
      </Button>
    </div>
    <div class="track"><div class="fill" :style="{ width: `${left}%` }" /></div>
  </aside>
</template>

<style scoped>
/*
 * Hangs below the sticky workout header and floats over the page,
 * so starting a rest never moves the set buttons.
 */
.timer {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: var(--secondary);
  border-block: 1px solid var(--border);
  box-shadow: 0 12px 24px rgb(0 0 0 / 0.5);
}

.body {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px 8px 16px;
}

.clock {
  min-width: 124px;
  font-size: 48px;
  line-height: 1;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.note {
  flex: 1;
  font-size: 13px;
  color: var(--muted-foreground);
}

.extend {
  flex: none;
  padding: 0 12px;
  height: 40px;
  border-radius: 12px;
  background: var(--card);
  font-weight: 700;
  font-size: 13px;
}

.timer {
  --stage: var(--primary);
  overflow: hidden;
}

.soon {
  --stage: var(--warn);
}

.over {
  --stage: var(--destructive);
  animation: shake 1.6s infinite;
}

/* Tints the band itself, so the remaining time reads from across the room. */
.wash {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--stage);
  opacity: 0.16;
  transition: width 250ms linear;
}

.body {
  position: relative;
}

.track {
  position: relative;
  height: 10px;
  background: var(--border);
}

.fill {
  height: 100%;
  background: var(--stage);
  transition: width 250ms linear;
}

.soon .clock,
.over .clock {
  color: var(--stage);
}

.over .wash {
  opacity: 0.28;
}

@keyframes shake {
  0%,
  30%,
  100% {
    transform: translateX(0);
  }
  5%,
  15%,
  25% {
    transform: translateX(-5px);
  }
  10%,
  20% {
    transform: translateX(5px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .over {
    animation: none;
  }
}
</style>
