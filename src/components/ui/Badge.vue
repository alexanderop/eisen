<script setup lang="ts">
/** A short status word next to a title. */
withDefaults(defineProps<{ variant?: 'accent' | 'neutral' }>(), { variant: 'accent' })
</script>

<template>
  <span class="badge" :class="variant"><slot /></span>
</template>

<style scoped>
.badge {
  flex: none;
  font-size: var(--text-xs);
  font-weight: 700;
  padding: 2px var(--space-2);
  border-radius: var(--radius-full);
}

.accent {
  background: var(--accent);
  color: var(--accent-ink);
}

.neutral {
  background: var(--surface-2);
  color: var(--muted);
}
</style>
