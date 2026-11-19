<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import Button from './Button.vue'

/**
 * A destructive action that needs two taps instead of a dialog.
 * The first tap arms it and changes the label. It disarms by itself after a few seconds.
 * Button props such as `variant` and `block` pass through.
 */
const props = withDefaults(defineProps<{ label: string; confirmLabel?: string }>(), {
  confirmLabel: 'Tap again to confirm',
})
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
  <Button tone="danger" @click="onClick">{{ armed ? props.confirmLabel : props.label }}</Button>
</template>
