<script setup lang="ts">
import { useDocumentVisibility, useElementVisibility } from '@vueuse/core'
import { computed, useTemplateRef } from 'vue'
import type { Exercise } from '../domain/exercises'
import { MOTION_BACKGROUND, MOTION_FRAME_MS, MOTION_FRAMES, MOTION_STILL_FRAME, motionOf } from '../domain/motions'
import ExerciseImage from './ExerciseImage.vue'

/**
 * A figure doing one repetition of an exercise, over and over, for the screens of a WOD.
 * The strip slides behind a square window one frame at a time. Without a strip it is the
 * exercise's still tile. It stands still on the middle frame when it must not move, and it
 * pauses while it is off screen or the app is in the background.
 */
const props = withDefaults(
  defineProps<{
    exercise: Exercise
    /** Edge length in px. */
    size?: number
    playing?: boolean
  }>(),
  { size: 44, playing: true },
)

const strips = import.meta.glob<string>('../assets/motions/*.webp', { eager: true, import: 'default' })

const strip = computed(() => {
  const name = motionOf(props.exercise.id)
  return name ? strips[`../assets/motions/${name}.webp`] : undefined
})

const tile = useTemplateRef<HTMLElement>('tile')
const onScreen = useElementVisibility(tile)
const page = useDocumentVisibility()
const running = computed(() => onScreen.value && page.value === 'visible')

const style = computed(() => ({
  '--size': `${props.size}px`,
  '--frames': MOTION_FRAMES,
  '--still': MOTION_STILL_FRAME,
  '--loop': `${MOTION_FRAMES * MOTION_FRAME_MS}ms`,
  width: `${props.size}px`,
  height: `${props.size}px`,
  backgroundColor: MOTION_BACKGROUND,
  backgroundImage: `url(${strip.value})`,
}))
</script>

<template>
  <span
    v-if="strip"
    ref="tile"
    class="motion"
    :class="{ playing, paused: !running }"
    :style="style"
    aria-hidden="true"
  />
  <ExerciseImage v-else :image="exercise.image" :name="exercise.name" :size="size" />
</template>

<style scoped>
.motion {
  flex: none;
  display: block;
  border-radius: 10px;
  /* Inset, so the frame keeps the whole square: a border would crop it. */
  box-shadow: inset 0 0 0 1px var(--border);
  background-repeat: no-repeat;
  background-size: calc(var(--frames) * var(--size)) var(--size);
  background-position: calc(var(--still) * var(--size) * -1) 0;
}

.playing {
  animation: motion var(--loop) steps(var(--frames)) infinite;
}

.paused {
  animation-play-state: paused;
}

@keyframes motion {
  from {
    background-position-x: 0;
  }
  to {
    background-position-x: calc(var(--frames) * var(--size) * -1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .playing {
    animation: none;
  }
}
</style>
