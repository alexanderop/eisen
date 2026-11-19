<script setup lang="ts">
/** A raised surface that groups one topic. `as="button"` makes the whole card tappable. */
withDefaults(defineProps<{ as?: 'div' | 'section' | 'article' | 'p' | 'button' }>(), { as: 'div' })
</script>

<template>
  <component :is="as" class="card"><slot /></component>
</template>

<style scoped>
.card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: var(--space-4);
}

button.card {
  width: 100%;
  text-align: left;
}
</style>
