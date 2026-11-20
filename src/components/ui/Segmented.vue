<script setup lang="ts" generic="T extends string">
import { Tabs, TabsList, TabsTrigger } from './tabs'

/** A switch between two or three views or modes of the same thing. */
const model = defineModel<T>({ required: true })
defineProps<{ options: { value: T; label: string }[]; disabled?: boolean }>()
</script>

<template>
  <Tabs :model-value="model" @update:model-value="model = $event as T">
    <TabsList class="bg-card w-full rounded-xl border p-1 group-data-horizontal/tabs:h-11">
      <TabsTrigger
        v-for="option in options"
        :key="option.value"
        :value="option.value"
        :disabled="disabled"
        class="data-active:bg-secondary data-active:text-foreground h-full rounded-[9px] text-sm font-semibold"
      >
        {{ option.label }}
      </TabsTrigger>
    </TabsList>
  </Tabs>
</template>
