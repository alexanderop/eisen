<script setup lang="ts">
/**
 * How many more reps the set had in it, rated with one tap. Tapping the chosen chip again takes
 * the rating back: it is never required.
 */
import { useId } from 'vue'
import { MAX_RIR, rirSpoken, rirText } from '../domain/program'
import { haptic } from '../haptics'

const model = defineModel<number | null>({ required: true })

const VALUES = Array.from({ length: MAX_RIR + 1 }, (_, rir) => rir)
const labelId = useId()

/** "1 rep", not "1 reps". */
function spoken(rir: number): string {
  return `${rirSpoken(rir)} ${rir === 1 ? 'rep' : 'reps'} in reserve`
}

function pick(rir: number) {
  haptic('rate')
  model.value = model.value === rir ? null : rir
}
</script>

<template>
  <section class="rir">
    <h3 :id="labelId">Reps in reserve</h3>
    <p class="hint">How many more could you have done?</p>
    <div class="chips" role="radiogroup" :aria-labelledby="labelId">
      <button
        v-for="rir in VALUES"
        :key="rir"
        type="button"
        role="radio"
        :aria-checked="model === rir"
        :aria-label="spoken(rir)"
        @click="pick(rir)"
      >
        {{ rirText(rir) }}
      </button>
    </div>
  </section>
</template>

<style scoped>
h3 {
  font-size: var(--fs-lg);
  line-height: 1.2;
  font-weight: 800;
}

.hint {
  margin-top: 2px;
  font-size: var(--fs-sm-plus);
  font-weight: 600;
  color: var(--muted-foreground);
}

.chips {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  margin-top: 10px;
}

button {
  min-height: 48px;
  border-radius: var(--r-full);
  background: var(--secondary);
  color: var(--muted-foreground);
  font-size: var(--fs-md);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  transition:
    background-color 0.12s ease,
    transform 0.08s ease;
}

button:active {
  transform: scale(0.94);
}

button[aria-checked='true'] {
  background: var(--primary);
  color: var(--primary-foreground);
}

button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

@media (max-height: 700px) {
  h3 {
    font-size: var(--fs-md);
  }

  .hint {
    margin-top: 0;
    font-size: var(--fs-sm);
  }

  .chips {
    margin-top: 8px;
  }
}

@media (prefers-reduced-motion: reduce) {
  button {
    transition: none;
  }
}
</style>
