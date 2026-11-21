<script setup lang="ts">
import { Badge, Button, Card, ChipGroup, ConfirmButton } from '../components/ui'
import { computed } from 'vue'
import CopyMarkdown from '../components/CopyMarkdown.vue'
import ExerciseImage from '../components/ExerciseImage.vue'
import { outcome, totalReps } from '../domain/program'
import { recordIds } from '../domain/records'
import { durationMinutes } from '../domain/share'
import { fmt, formatDate, formatShortDate } from '../format'
import { stopRest } from '../rest'
import {
  discardWorkout,
  findExercise,
  lastChanges,
  nextRoutine,
  progressOf,
  selectRoutine,
  startWorkout,
  state,
} from '../store'

const emit = defineEmits<{ open: [] }>()

const title = computed(() => state.active?.name ?? nextRoutine.value.name)

/** The running workout as it was started, or the next one as it is set up right now. */
const rows = computed(() => {
  if (state.active) {
    return state.active.exercises.map((log) => ({
      id: log.id,
      name: log.name,
      sets: log.sets.length,
      reps: log.targetReps,
      weight: log.equipment === 'bodyweight' ? null : log.weight,
    }))
  }
  return nextRoutine.value.slots.flatMap((slot) => {
    const exercise = findExercise(slot.exerciseId)
    if (!exercise) return []
    return [
      {
        id: exercise.id,
        name: exercise.name,
        sets: slot.sets,
        reps: slot.reps,
        weight: exercise.equipment === 'bodyweight' ? null : progressOf(exercise.id).weight,
      },
    ]
  })
})

const WEEK_MS = 7 * 24 * 60 * 60 * 1000
const thisWeek = computed(
  () => state.history.filter((workout) => Date.now() - workout.startedAt < WEEK_MS).length,
)
const lastWorkout = computed(() => state.history[0] ?? null)

/** The workout that was just saved, in numbers. */
const saved = computed(() => {
  const workout = lastWorkout.value
  if (!workout) return null
  const sets = workout.exercises.flatMap((log) => log.sets)
  return {
    minutes: durationMinutes(workout),
    logged: sets.filter((reps) => reps !== null).length,
    total: sets.length,
    skipped: workout.exercises.filter((log) => outcome(log) === 'skipped').map((log) => log.name),
  }
})

/** Records the workout that was just saved set, as "Hack Squat 85kg". */
const records = computed(() => {
  const workout = lastWorkout.value
  if (!workout) return []
  const ids = recordIds(state.history, 0)
  return workout.exercises
    .filter((log) => ids.has(log.id))
    .map((log) =>
      log.equipment === 'bodyweight'
        ? `${log.name} ${totalReps(log)} reps`
        : `${log.name} ${fmt(log.weight, workout.unit)}`,
    )
})

const CHANGE_TEXT = { up: 'Up', repeat: 'Repeat', deload: 'Deload' } as const

function onDiscard() {
  stopRest()
  discardWorkout()
}

const routineOptions = computed(() =>
  state.routines.map((routine) => ({ value: routine.id, label: routine.name })),
)

function start() {
  if (startWorkout()) emit('open')
}
</script>

<template>
  <header>
    <p class="eyebrow">{{ formatDate(Date.now()) }}</p>
    <h1 class="page-title">Eisen 5×5</h1>
  </header>

  <Card v-if="lastChanges" as="section" class="summary">
    <div class="row">
      <h2>Workout saved</h2>
      <Button class="px-1" variant="ghost" size="md" @click="lastChanges = null">Dismiss</Button>
    </div>
    <p v-if="saved" class="saved-line">
      <strong>{{ saved.minutes }} min</strong>
      <span class="muted">·</span>
      <strong>{{ saved.logged }} of {{ saved.total }} sets</strong>
    </p>
    <p v-if="records.length > 0" class="records">
      <Badge>New record</Badge>
      {{ records.join(', ') }}
    </p>
    <p v-if="lastChanges.length === 0" class="muted">No weight changes this time.</p>
    <ul v-else>
      <li v-for="change in lastChanges" :key="change.id" :class="change.kind">
        <span>{{ change.name }}</span>
        <span class="muted">{{ CHANGE_TEXT[change.kind] }}</span>
        <strong>{{ fmt(change.to) }}</strong>
      </li>
    </ul>
    <p v-if="saved && saved.skipped.length > 0" class="muted skipped">
      Skipped: {{ saved.skipped.join(', ') }}
    </p>
    <CopyMarkdown v-if="lastWorkout" class="w-full" :workout="lastWorkout" label="Copy as Markdown" />
  </Card>

  <Card as="section" class="next">
    <p class="eyebrow">{{ state.active ? 'In progress' : 'Next up' }}</p>
    <ChipGroup
      v-if="!state.active && state.routines.length > 1"
      :model-value="nextRoutine.id"
      :options="routineOptions"
      label="Next workout"
      bleed
      @update:model-value="selectRoutine"
    />
    <h2>{{ title }}</h2>
    <p v-if="rows.length === 0" class="muted empty">
      This workout has no exercises yet. Add some in the Workouts tab.
    </p>
    <ul v-else>
      <li v-for="row in rows" :key="row.id" class="exercise">
        <ExerciseImage :image="findExercise(row.id)?.image" :name="row.name" :size="40" />
        <span>{{ row.name }}</span>
        <span class="muted">{{ row.sets }}×{{ row.reps ?? 'max' }}</span>
        <strong>{{ row.weight === null ? 'BW' : fmt(row.weight) }}</strong>
      </li>
    </ul>
    <Button class="w-full" v-if="state.active" @click="emit('open')">Resume workout</Button>
    <Button class="w-full" v-else :disabled="rows.length === 0" @click="start">
      Start workout
    </Button>
    <ConfirmButton
      v-if="state.active"
      class="discard"
      variant="ghost"
      size="md"
      label="Discard workout"
      confirm-label="Tap again to discard"
      @confirm="onDiscard"
    />
  </Card>

  <section v-if="lastWorkout" class="stats">
    <Card>
      <strong>{{ state.history.length }}</strong>
      <span class="muted">workouts</span>
    </Card>
    <Card>
      <strong>{{ thisWeek }}</strong>
      <span class="muted">last 7 days</span>
    </Card>
    <Card>
      <strong>{{ formatShortDate(lastWorkout.startedAt) }}</strong>
      <span class="muted">last workout</span>
    </Card>
  </section>
</template>

<style scoped>
h2 {
  font-size: 24px;
  font-weight: 800;
}

.summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary h2 {
  font-size: 17px;
}

.saved-line {
  display: flex;
  gap: 8px;
  font-size: 20px;
}

.skipped {
  font-size: 13px;
}

.records {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

li {
  display: grid;
  grid-template-columns: 1fr auto 76px;
  gap: 12px;
  align-items: baseline;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}

li.exercise {
  grid-template-columns: 40px 1fr auto 76px;
  align-items: center;
  padding: 8px 0;
}

li:last-child {
  border-bottom: 0;
}

li strong {
  text-align: right;
}

.next {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.empty {
  padding: 12px 0;
}

.next .discard {
  align-self: center;
}

.summary .up strong {
  color: var(--primary);
}

.summary .deload strong {
  color: var(--warn);
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.stats > * {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 14px 12px;
}

.stats strong {
  font-size: 17px;
}

.stats .muted {
  font-size: 12px;
}
</style>
