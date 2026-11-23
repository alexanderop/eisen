<script setup lang="ts">
import { Mic } from '@lucide/vue'
import { computed, ref } from 'vue'
import { timerSummary } from '../domain/timer'
import { callsSummary } from '../domain/timerScreens'
import { isLibraryWod, wodBest, wodFormat, wodLines, wodName, type Wod } from '../domain/wod'
import { lastRun, runText } from '../domain/wodScreens'
import { formatShortDate } from '../format'
import { deleteWod, findExercise, state } from '../store'
import ExerciseMotion from './ExerciseMotion.vue'
import TimerCalls from './TimerCalls.vue'
import { Button, Card, ConfirmButton, ListRow, Sheet } from './ui'

const props = defineProps<{ workout: Wod }>()
const emit = defineEmits<{ back: []; edit: []; copy: []; start: [] }>()

const library = computed(() => isLibraryWod(props.workout.id))
const name = computed(() => wodName(props.workout))
const format = computed(() => wodFormat(props.workout))

/** One row per movement, with its picture. A movement whose exercise was deleted is left out. */
const rows = computed(() => {
  const lines = wodLines(props.workout, findExercise, state.settings.unit)
  const exercises = props.workout.items.flatMap((item) => findExercise(item.exerciseId) ?? [])
  return exercises.map((exercise, index) => ({ exercise, line: lines[index], key: `${index}-${exercise.id}` }))
})

const best = computed(() => wodBest(state.timerSessions, props.workout.id))
const last = computed(() => lastRun(state.timerSessions, props.workout.id))

const voiceOpen = ref(false)
/** The movement shown large in a sheet, by its row's key. */
const watching = ref<string>()
const watched = computed(() => rows.value.find((row) => row.key === watching.value))

function onDelete() {
  deleteWod(props.workout.id)
  emit('back')
}
</script>

<template>
  <Button class="back px-1" variant="ghost" size="md" @click="emit('back')">‹ WODs</Button>

  <header class="head">
    <h1 class="page-title">{{ name }}</h1>
    <p v-if="format !== name" class="format">{{ format }}</p>
    <p class="time">{{ timerSummary(workout.timer) }}</p>
  </header>

  <Card as="ul" class="moves">
    <li v-for="row in rows" :key="row.key" class="move">
      <button class="watch" :aria-label="`Watch ${row.exercise.name}`" @click="watching = row.key">
        <ExerciseMotion :exercise="row.exercise" :size="56" />
      </button>
      <span>{{ row.line }}</span>
    </li>
  </Card>

  <Card v-if="last" class="runs">
    <div v-if="best" class="run">
      <span class="eyebrow">Best</span>
      <strong>{{ runText(best) }}</strong>
      <span class="muted">{{ formatShortDate(best.startedAt) }}</span>
    </div>
    <div class="run">
      <span class="eyebrow">Last</span>
      <strong>{{ runText(last) }}</strong>
      <span class="muted">{{ formatShortDate(last.startedAt) }}</span>
    </div>
  </Card>

  <Card as="section" class="voice">
    <ListRow :icon="Mic" label="Voice" :description="callsSummary(state.settings.timerCalls)" @click="voiceOpen = true" />
  </Card>

  <Button v-if="library" variant="secondary" class="w-full" @click="emit('copy')">Edit a copy</Button>
  <div v-else class="actions">
    <Button variant="secondary" @click="emit('edit')">Edit</Button>
    <ConfirmButton :key="workout.id" label="Delete" confirm-label="Tap again to delete" @confirm="onDelete" />
  </div>

  <div class="dock">
    <Button class="w-full start" @click="emit('start')">Start</Button>
  </div>

  <Sheet v-if="voiceOpen" title="Voice" @close="voiceOpen = false">
    <div class="bleed">
      <TimerCalls />
    </div>
    <Button class="w-full" @click="voiceOpen = false">Done</Button>
  </Sheet>

  <Sheet v-if="watched" :title="watched.exercise.name" @close="watching = undefined">
    <ExerciseMotion class="mx-auto" :exercise="watched.exercise" :size="240" />
    <p v-if="watched.line !== watched.exercise.name" class="watched-line">{{ watched.line }}</p>
    <Button class="w-full" @click="watching = undefined">Done</Button>
  </Sheet>
</template>

<style scoped>
.back {
  align-self: flex-start;
}

.head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: -8px;
}

.page-title {
  overflow-wrap: anywhere;
}

.format {
  margin-top: 2px;
  color: var(--primary);
  font-size: var(--fs-2xl);
  line-height: 1.1;
  font-weight: 900;
  letter-spacing: -0.02em;
}

.time {
  color: var(--muted-foreground);
  font-size: var(--fs-md);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.moves {
  margin: 0;
  padding: 4px 16px;
  list-style: none;
}

.move {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 72px;
  padding: 10px 0;
  font-size: var(--fs-md);
  font-weight: 700;
  line-height: 1.25;
}

.watch {
  flex: none;
  border-radius: 10px;
}

.move + .move {
  border-top: 1px solid var(--border);
}

.runs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.run {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.run strong {
  font-size: var(--fs-xl);
  font-weight: 800;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}

.run .muted {
  font-size: var(--fs-sm);
}

.voice {
  padding: 4px 0;
  overflow: hidden;
}

.actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.start {
  font-size: var(--fs-md);
}

.bleed {
  margin: 0 -16px;
}

.watched-line {
  font-size: var(--fs-lg);
  font-weight: 800;
  text-align: center;
}
</style>
