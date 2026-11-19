<script setup lang="ts" generic="T extends string">
/**
 * Picks one of many options in a row that scrolls sideways.
 * `bleed` lets the row run to the screen edge from inside a 16px padding.
 */
const model = defineModel<T>({ required: true })
defineProps<{ options: { value: T; label: string }[]; label: string; bleed?: boolean }>()
</script>

<template>
  <div class="chips" :class="{ bleed }" role="group" :aria-label="label">
    <button
      v-for="option in options"
      :key="option.value"
      :aria-pressed="model === option.value"
      @click="model = option.value"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<style scoped>
.chips {
  display: flex;
  gap: var(--space-2);
  overflow-x: auto;
  padding-bottom: var(--space-1);
  scrollbar-width: none;
}

.bleed {
  margin: 0 calc(-1 * var(--space-4));
  padding-inline: var(--space-4);
}

button {
  flex: none;
  min-height: 40px;
  padding: 0 14px;
  border-radius: var(--radius-full);
  background: var(--surface-2);
  color: var(--muted);
  font-weight: 600;
  font-size: var(--text-sm-plus);
}

button[aria-pressed='true'] {
  background: var(--accent);
  color: var(--accent-ink);
}
</style>
