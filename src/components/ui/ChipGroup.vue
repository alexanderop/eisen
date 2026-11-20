<script setup lang="ts" generic="T extends string">
import { ToggleGroup, ToggleGroupItem } from './toggle-group'

/**
 * Picks one of many options in a row that scrolls sideways.
 * `bleed` lets the row run to the screen edge from inside a 16px padding.
 */
const model = defineModel<T>({ required: true })
defineProps<{ options: { value: T; label: string }[]; label: string; bleed?: boolean }>()

/** One chip is always on: tapping the active chip again changes nothing. */
function onChange(value: unknown) {
  if (typeof value === 'string' && value !== '') model.value = value as T
}
</script>

<template>
  <ToggleGroup
    type="single"
    :model-value="model"
    :spacing="2"
    :aria-label="label"
    class="w-full overflow-x-auto pb-1 [scrollbar-width:none]"
    :class="{ '-mx-4 w-auto px-4': bleed }"
    @update:model-value="onChange"
  >
    <ToggleGroupItem
      v-for="option in options"
      :key="option.value"
      :value="option.value"
      class="bg-secondary text-muted-foreground data-[state=on]:bg-primary data-[state=on]:text-primary-foreground h-10 rounded-full px-3.5 text-sm font-semibold"
    >
      {{ option.label }}
    </ToggleGroupItem>
  </ToggleGroup>
</template>
