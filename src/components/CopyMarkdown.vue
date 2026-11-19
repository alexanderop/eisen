<script setup lang="ts">
/** Copies a workout as Markdown. Button props such as `variant` and `block` pass through. */
import { ref } from 'vue'
import { copyText } from '../clipboard'
import type { Workout } from '../domain/program'
import { workoutMarkdown } from '../domain/share'
import { formatDate } from '../format'
import { Button } from './ui'

const props = defineProps<{ workout: Workout; label?: string }>()

const status = ref<'idle' | 'copied' | 'failed'>('idle')
let timer: ReturnType<typeof setTimeout> | undefined

async function copy() {
  const copied = await copyText(workoutMarkdown(props.workout, formatDate(props.workout.startedAt)))
  status.value = copied ? 'copied' : 'failed'
  clearTimeout(timer)
  timer = setTimeout(() => (status.value = 'idle'), 2000)
}
</script>

<template>
  <Button @click="copy">
    <span aria-live="polite">
      {{ status === 'copied' ? 'Copied' : status === 'failed' ? 'Copy failed' : (label ?? 'Copy') }}
    </span>
  </Button>
</template>
