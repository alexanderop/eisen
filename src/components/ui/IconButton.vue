<script setup lang="ts">
/** A square button that shows only a symbol. `label` is what screen readers announce. */
withDefaults(
  defineProps<{
    label: string
    /** `sunken` sits on a raised surface such as the rest timer. */
    variant?: 'secondary' | 'sunken'
    tone?: 'default' | 'muted' | 'danger'
    size?: 'md' | 'lg'
    round?: boolean
  }>(),
  { variant: 'secondary', tone: 'default', size: 'md', round: false },
)
</script>

<template>
  <button class="icon-button" :class="[variant, tone, size, { round }]" :aria-label="label">
    <slot />
  </button>
</template>

<style scoped>
.icon-button {
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: var(--surface-2);
  font-weight: 700;
  line-height: 1;
}

.lg {
  width: var(--control-md);
  height: var(--control-md);
  font-size: 22px;
  font-weight: 600;
}

.sunken {
  background: var(--surface);
}

.round {
  border-radius: var(--radius-full);
}

.muted {
  color: var(--muted);
}

.danger {
  color: var(--danger);
}
</style>
