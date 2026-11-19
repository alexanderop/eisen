<script setup lang="ts">
const model = defineModel<number>({ required: true })
const props = withDefaults(
  defineProps<{ step: number; min?: number; label: string; format?: (value: number) => string }>(),
  { min: 0, format: (value: number) => String(value) },
)

function change(direction: 1 | -1) {
  model.value = Math.max(props.min, model.value + direction * props.step)
}
</script>

<template>
  <div class="stepper">
    <button :aria-label="`Decrease ${label}`" :disabled="model <= min" @click="change(-1)">−</button>
    <output :aria-label="label">{{ format(model) }}</output>
    <button :aria-label="`Increase ${label}`" @click="change(1)">+</button>
  </div>
</template>

<style scoped>
.stepper {
  display: flex;
  align-items: center;
  gap: 4px;
}

button {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--surface-2);
  font-size: 22px;
  font-weight: 600;
  line-height: 1;
}

output {
  min-width: 84px;
  text-align: center;
  font-weight: 700;
  font-size: 17px;
}
</style>
