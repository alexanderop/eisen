<script setup lang="ts">
/** A single-line text field. Attributes such as `type` and `placeholder` pass through. */
const model = defineModel<string>({ required: true })
</script>

<template>
  <input v-model="model" class="input" />
</template>

<style scoped>
.input {
  width: 100%;
  min-height: 48px;
  padding: 0 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--text);
  font: inherit;
  /* 16px keeps iOS from zooming in on focus. */
  font-size: var(--text-base-plus);
}

.input::placeholder {
  color: var(--muted);
}

.input:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}
</style>
