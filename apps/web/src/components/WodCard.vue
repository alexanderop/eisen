<script setup lang="ts">
import { Play } from '@lucide/vue'
import { computed } from 'vue'
import { bestText, wodBest, wodFormat, wodLines, wodName, type Wod } from '../domain/wod'
import { cardLabel } from '../domain/wodScreens'
import { findExercise, state } from '../store'
import ExerciseImage from './ExerciseImage.vue'
import { Card } from './ui'

/**
 * A WOD in a list: the whole card opens it, the round button starts it at once.
 * `preview` is the builder's picture of the card: it looks the same, without the button, and does nothing.
 */
const props = defineProps<{ workout: Wod; preview?: boolean }>()
const emit = defineEmits<{ open: []; start: [] }>()

/** The first movement's picture, or its letter. */
const first = computed(() => props.workout.items.map((item) => findExercise(item.exerciseId)).find(Boolean))

const name = computed(() => wodName(props.workout))
const format = computed(() => wodFormat(props.workout))
const moves = computed(() => wodLines(props.workout, findExercise, state.settings.unit).join(' · '))
const best = computed(() => wodBest(state.timerSessions, props.workout.id))
const label = computed(() => cardLabel(props.workout, findExercise, state.settings.unit, best.value))
</script>

<template>
  <Card as="div" class="wod-card" :class="{ preview }" :aria-hidden="preview || undefined">
    <component :is="preview ? 'div' : 'button'" class="open" :aria-label="preview ? undefined : label" @click="emit('open')">
      <ExerciseImage :image="first?.image" :name="first?.name ?? name" :size="56" />
      <span class="text">
        <strong class="name">{{ name }}</strong>
        <span v-if="format !== name" class="format">{{ format }}</span>
        <span class="moves">{{ moves || 'No movements yet' }}</span>
        <span v-if="best" class="best">Best {{ bestText(best) }}</span>
      </span>
    </component>
    <button v-if="!preview" class="play" :aria-label="`Start ${name}`" @click="emit('start')">
      <Play :size="22" :stroke-width="2.5" fill="currentColor" aria-hidden="true" />
    </button>
  </Card>
</template>

<style scoped>
.wod-card {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 88px;
  padding: 0 12px 0 0;
  overflow: hidden;
}

.preview {
  padding-right: 0;
}

.open {
  flex: 1;
  min-width: 0;
  align-self: stretch;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 4px 12px 12px;
  text-align: left;
}

button.open:active {
  background: var(--secondary);
}

.text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.name {
  font-size: var(--fs-md);
  font-weight: 800;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.format {
  color: var(--primary);
  font-size: var(--fs-sm-plus);
  font-weight: 800;
}

.moves {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--muted-foreground);
  font-size: var(--fs-sm);
}

.best {
  margin-top: 2px;
  font-size: var(--fs-xs);
  font-weight: 700;
}

.play {
  flex: none;
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--primary);
  color: var(--primary-foreground);
}

.play:active {
  transform: scale(0.94);
}
</style>
