<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { Button } from './button'

/**
 * A destructive action that needs two taps instead of a dialog.
 * The first tap arms it and changes the label. It disarms by itself after a few seconds.
 * `ghost` is the quiet form, for a list row. Other attributes such as `size` pass through.
 */
const props = withDefaults(
  defineProps<{ label: string; confirmLabel?: string; variant?: 'destructive' | 'ghost' }>(),
  { confirmLabel: 'Tap again to confirm', variant: 'destructive' },
)
const emit = defineEmits<{ confirm: [] }>()

const DISARM_AFTER_MS = 4000
const armed = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined

function onClick() {
  clearTimeout(timer)
  if (armed.value) {
    armed.value = false
    emit('confirm')
    return
  }
  armed.value = true
  timer = setTimeout(() => (armed.value = false), DISARM_AFTER_MS)
}

onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <Button :variant="variant" :class="{ 'text-destructive': variant === 'ghost' }" @click="onClick">{{ armed ? props.confirmLabel : props.label }}</Button>
</template>
