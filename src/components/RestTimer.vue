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
</script>

<template>
  <aside v-if="rest.active" class="timer" :class="{ over: restOver }" role="timer" aria-live="off">
    <div class="track"><div class="fill" :style="{ width: `${restFraction * 100}%` }" /></div>
    <div class="body">
      <div class="clock">
        {{ restOver ? `+${formatClock(restOvertime)}` : formatClock(restRemaining) }}
      </div>
      <p class="note">{{ restOver ? 'Rest is over. Start your next set.' : rest.note }}</p>
      <button v-if="!restOver" class="extend" @click="extendRest(30)">+30s</button>
      <button class="dismiss" aria-label="Dismiss timer" @click="stopRest()">✕</button>
    </div>
  </aside>
</template>

<style scoped>
.timer {
  position: fixed;
  left: 50%;
  bottom: calc(env(safe-area-inset-bottom) + 12px);
  transform: translateX(-50%);
  width: min(448px, calc(100% - 24px));
  z-index: 20;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 0 12px 32px rgb(0 0 0 / 0.5);
}

.track {
  height: 4px;
  background: var(--line);
}

.fill {
  height: 100%;
  background: var(--accent);
}

.body {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 10px 10px 16px;
}

.clock {
  font-size: 26px;
  font-weight: 800;
  min-width: 68px;
}

.note {
  flex: 1;
  font-size: 13px;
  color: var(--muted);
}

.extend,
.dismiss {
  flex: none;
  height: 40px;
  border-radius: 12px;
  background: var(--surface);
  font-weight: 700;
  font-size: 13px;
}

.extend {
  padding: 0 12px;
}

.dismiss {
  width: 40px;
  color: var(--muted);
}

.over {
  border-color: var(--accent);
}

.over .clock {
  color: var(--accent);
}
</style>
