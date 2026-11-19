<script setup lang="ts">
/**
 * The one button of the app. `variant` sets how loud it is, `tone` whether it is destructive.
 * Use one primary button per screen. `as="label"` wraps a hidden file input.
 */
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost'
    tone?: 'default' | 'danger'
    size?: 'sm' | 'md' | 'lg'
    /** Fill the width of the container. */
    block?: boolean
    as?: 'button' | 'label'
  }>(),
  { variant: 'secondary', tone: 'default', size: 'lg', block: false, as: 'button' },
)
</script>

<template>
  <component :is="as" class="button" :class="[variant, size, { block, danger: tone === 'danger' }]">
    <slot />
  </component>
</template>

<style scoped>
.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: var(--control-lg);
  padding: 0 18px;
  border-radius: var(--radius-lg);
  font-weight: 700;
  font-size: var(--text-base-plus);
  cursor: pointer;
  user-select: none;
  -webkit-user-select: none;
  transition: transform 0.08s ease;
}

.button:active:not(:disabled) {
  transform: scale(0.98);
}

.block {
  display: flex;
  width: 100%;
}

.md {
  min-height: var(--control-md);
  font-size: var(--text-base);
}

.sm {
  min-height: var(--control-sm);
  padding: 0 var(--space-3);
  border-radius: var(--radius-md);
  font-size: var(--text-sm-plus);
}

.primary {
  background: var(--accent);
  color: var(--accent-ink);
}

.secondary {
  background: var(--surface-2);
}

.ghost {
  padding: 0 var(--space-1);
  color: var(--accent);
  font-weight: 600;
}

.danger {
  color: var(--danger);
}

.primary.danger {
  background: var(--danger);
  color: var(--accent-ink);
}
</style>
