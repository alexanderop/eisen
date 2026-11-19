<script setup lang="ts">
/** A label above a control. With `as="div"` it only groups, for controls that label themselves. */
withDefaults(defineProps<{ label: string; as?: 'label' | 'div' }>(), { as: 'label' })
</script>

<template>
  <component :is="as" class="field">
    <span class="eyebrow">{{ label }}</span>
    <slot />
  </component>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
