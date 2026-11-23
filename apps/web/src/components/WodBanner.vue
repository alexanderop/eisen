<script setup lang="ts">
import { useDocumentVisibility, useElementVisibility } from '@vueuse/core'
import { computed, useTemplateRef } from 'vue'
import { MOTION_BACKGROUND, MOTION_FRAME_MS, MOTION_FRAMES, MOTION_STILL_FRAME } from '../domain/motions'
import type { Wod } from '../domain/wod'
import { bannerMotions } from '../domain/wodScreens'
import { findExercise } from '../store'
import ExerciseImage from './ExerciseImage.vue'

/**
 * A WOD's picture: the figures of its movements side by side, from the motion strips, and the
 * still picture of a movement that has none, so every movement has its place. Still on the middle
 * frame unless `playing`, and then paused off screen or in the background like the motion tile.
 * Nothing is drawn for a WOD without a strip: check {@link bannerMotions} first.
 */
const props = withDefaults(defineProps<{ workout: Wod; playing?: boolean }>(), { playing: false })

const strips = import.meta.glob<string>('../assets/motions/*.webp', { eager: true, import: 'default' })

const slots = computed(() =>
  bannerMotions(props.workout).map((slot) => ({
    ...slot,
    url: slot.strip ? strips[`../assets/motions/${slot.strip}.webp`] : undefined,
    exercise: findExercise(slot.exerciseId),
  })),
)

const banner = useTemplateRef<HTMLElement>('banner')
const onScreen = useElementVisibility(banner)
const page = useDocumentVisibility()
const running = computed(() => onScreen.value && page.value === 'visible')

const style = {
  '--frames': MOTION_FRAMES,
  '--still': MOTION_STILL_FRAME,
  '--loop': `${MOTION_FRAMES * MOTION_FRAME_MS}ms`,
  backgroundColor: MOTION_BACKGROUND,
}
</script>

<template>
  <div v-if="slots.length > 0" ref="banner" class="wod-banner" :style="style" aria-hidden="true">
    <template v-for="slot in slots" :key="slot.exerciseId">
      <span
        v-if="slot.url"
        class="figure"
        :class="{ playing, paused: !running }"
        :style="{ backgroundImage: `url(${slot.url})` }"
      />
      <ExerciseImage v-else class="still" :image="slot.exercise?.image" :name="slot.exercise?.name ?? ''" :size="40" />
    </template>
  </div>
</template>

<style scoped>
/* As high as it is given. The figures are squares of that height, narrower when four do not fit. */
.wod-banner {
  container-type: size;
  display: flex;
  align-items: center;
  justify-content: space-evenly;
  width: 100%;
  overflow: hidden;
}

/*
 * The strip is six frames wide. A position in percent puts frame n at n / (frames - 1) of the
 * way, so the figures follow the size of the square without knowing it in pixels.
 */
.figure {
  flex: none;
  width: min(100cqh, 25cqi);
  aspect-ratio: 1;
  background-repeat: no-repeat;
  background-size: calc(var(--frames) * 100%) 100%;
  background-position: calc(var(--still) / (var(--frames) - 1) * 100%) 0;
}

/* A movement without a figure: its picture, small, so the figures stay the thing to see. */
.still {
  flex: none;
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
    background-position-x: calc(var(--frames) / (var(--frames) - 1) * 100%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .playing {
    animation: none;
  }
}
</style>
