<script setup lang="ts" generic="T extends string">
/** A switch between two or three views or modes of the same thing. */
const model = defineModel<T>({ required: true })
defineProps<{ options: { value: T; label: string }[]; disabled?: boolean }>()
</script>

<template>
  <div class="segmented">
    <button
      v-for="option in options"
      :key="option.value"
      :aria-pressed="model === option.value"
      :disabled="disabled"
      @click="model = option.value"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<style scoped>
.segmented {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 1fr;
  gap: var(--space-1);
  padding: var(--space-1);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
}

button {
  min-height: var(--control-sm);
  border-radius: 9px;
  font-weight: 600;
  font-size: var(--text-sm-plus);
  color: var(--muted);
}

button[aria-pressed='true'] {
  background: var(--surface-2);
  color: var(--text);
}
</style>
