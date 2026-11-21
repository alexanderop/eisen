<script setup lang="ts">
import { haptic } from '../../haptics'
import { Button } from './button'

/** Changes a number in fixed steps with thumb-sized buttons: weights, sets, reps, seconds. */
const model = defineModel<number>({ required: true })
const props = withDefaults(
  defineProps<{ step: number; min?: number; max?: number; label: string; format?: (value: number) => string }>(),
  { min: 0, max: Infinity, format: (value: number) => String(value) },
)

function change(direction: 1 | -1) {
  const next = Math.min(props.max, Math.max(props.min, model.value + direction * props.step))
  if (next === model.value) return
  model.value = next
  haptic('step')
}
</script>

<template>
  <div class="stepper">
    <Button size="icon-lg" variant="secondary" :aria-label="`Decrease ${label}`" :disabled="model <= min" @click="change(-1)">
      −
    </Button>
    <output :aria-label="label">{{ format(model) }}</output>
    <Button size="icon-lg" variant="secondary" :aria-label="`Increase ${label}`" :disabled="model >= max" @click="change(1)">
      +
    </Button>
  </div>
</template>

<style scoped>
.stepper {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

output {
  min-width: 84px;
  text-align: center;
  font-weight: 700;
  font-size: var(--fs-md);
}
</style>
