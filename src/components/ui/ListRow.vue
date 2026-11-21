<script setup lang="ts">
import { ChevronRight } from '@lucide/vue'
import type { Component } from 'vue'

/**
 * One line of a settings list: an icon, a label and what the setting is right now.
 * It runs from screen edge to screen edge and the whole line is the tap target.
 * `switch` makes the row itself the switch, so there is no small control to aim for.
 */
const props = withDefaults(
  defineProps<{
    /** Leave it out to fill the `leading` slot with something else, like a picture. */
    icon?: Component
    label: string
    /** A second, smaller line under the label. */
    description?: string
    /** What the setting is right now, at the right edge. */
    value?: string
    trailing?: 'chevron' | 'switch' | 'none'
    /** Only for `switch`. */
    checked?: boolean
    tone?: 'default' | 'destructive' | 'accent'
    /** `div` for a line that only shows something, `label` to wrap a hidden file input. */
    as?: 'button' | 'div' | 'label'
    disabled?: boolean
  }>(),
  { trailing: 'chevron', tone: 'default', as: 'button' },
)
</script>

<template>
  <component
    :is="as"
    class="list-row"
    :class="[tone, { static: as === 'div' }]"
    :role="trailing === 'switch' ? 'switch' : undefined"
    :aria-checked="trailing === 'switch' ? props.checked : undefined"
    :disabled="as === 'button' ? disabled : undefined"
  >
    <slot name="leading">
      <component :is="icon" class="icon" :size="24" :stroke-width="1.75" aria-hidden="true" />
    </slot>
    <span class="text">
      <span class="label">{{ label }}</span>
      <span v-if="description" class="description">{{ description }}</span>
    </span>
    <span v-if="value" class="value">{{ value }}</span>
    <span v-if="trailing === 'switch'" class="switch" :class="{ on: checked }" aria-hidden="true" />
    <ChevronRight
      v-else-if="trailing === 'chevron'"
      class="chevron"
      :size="18"
      :stroke-width="2"
      aria-hidden="true"
    />
    <slot />
  </component>
</template>

<style scoped>
.list-row {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  min-height: 56px;
  padding: 10px 16px;
  text-align: left;
  font-size: 16px;
  transition: background-color 0.12s ease;
}

.list-row:not(.static):active {
  background: var(--secondary);
}

.icon {
  flex: none;
}

.text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.description {
  font-size: 13px;
  color: var(--muted-foreground);
}

.value {
  flex: none;
  max-width: 55%;
  color: var(--muted-foreground);
  font-size: 15px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.chevron {
  flex: none;
  margin-left: -6px;
  color: var(--muted-foreground);
}

.destructive {
  color: var(--destructive);
}

.accent {
  color: var(--primary);
}

.switch {
  flex: none;
  position: relative;
  width: 46px;
  height: 28px;
  border-radius: 999px;
  background: var(--input);
  transition: background-color 0.18s ease;
}

.switch::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--foreground);
  transition: transform 0.18s ease;
}

.switch.on {
  background: var(--primary);
}

.switch.on::after {
  background: var(--primary-foreground);
  transform: translateX(18px);
}

@media (prefers-reduced-motion: reduce) {
  .switch,
  .switch::after {
    transition: none;
  }
}
</style>
