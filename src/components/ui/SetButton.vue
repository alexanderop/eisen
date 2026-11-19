<script setup lang="ts">
/** One set of an exercise. Lime when all reps were done, an amber ring when reps were missed. */
withDefaults(defineProps<{ state?: 'empty' | 'done' | 'missed' }>(), { state: 'empty' })
</script>

<template>
  <button class="set" :class="state"><slot /></button>
</template>

<style scoped>
.set {
  aspect-ratio: 1;
  max-width: 68px;
  width: 100%;
  border-radius: var(--radius-full);
  background: var(--surface-2);
  color: var(--muted);
  font-size: var(--text-lg);
  font-weight: 700;
  transition:
    background-color 0.12s ease,
    transform 0.08s ease;
}

.set:active {
  transform: scale(0.94);
}

.done {
  background: var(--accent);
  color: var(--accent-ink);
}

.missed {
  background: transparent;
  color: var(--warn);
  box-shadow: inset 0 0 0 2px var(--warn);
}
</style>
