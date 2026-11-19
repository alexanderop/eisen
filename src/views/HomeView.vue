<script setup lang="ts">
import { computed } from 'vue'
import { EXERCISE_NAMES, PROGRAM } from '../domain/program'
import { fmt, formatDate, formatShortDate } from '../format'
import { discardWorkout, lastChanges, startWorkout, state } from '../store'

const emit = defineEmits<{ open: [] }>()

const type = computed(() => state.active?.type ?? state.nextType)
const slots = computed(() => PROGRAM[type.value])

const WEEK_MS = 7 * 24 * 60 * 60 * 1000
const thisWeek = computed(
  () => state.history.filter((workout) => Date.now() - workout.startedAt < WEEK_MS).length,
)
const lastWorkout = computed(() => state.history[0] ?? null)

const CHANGE_TEXT = { up: 'Up', repeat: 'Repeat', deload: 'Deload' } as const

function start() {
  startWorkout()
  emit('open')
}
</script>

<template>
  <header>
    <p class="eyebrow">{{ formatDate(Date.now()) }}</p>
    <h1 class="page-title">Eisen 5×5</h1>
  </header>

  <section v-if="lastChanges" class="card summary">
    <div class="row">
      <h2>Workout saved</h2>
      <button class="link" @click="lastChanges = null">Dismiss</button>
    </div>
    <ul>
      <li v-for="change in lastChanges" :key="change.id" :class="change.kind">
        <span>{{ EXERCISE_NAMES[change.id] }}</span>
        <span class="muted">{{ CHANGE_TEXT[change.kind] }}</span>
        <strong>{{ fmt(change.to) }}</strong>
      </li>
    </ul>
  </section>

  <section class="card next">
    <p class="eyebrow">{{ state.active ? 'In progress' : 'Next up' }}</p>
    <h2>Workout {{ type }}</h2>
    <ul>
      <li v-for="slot in slots" :key="slot.id">
        <span>{{ EXERCISE_NAMES[slot.id] }}</span>
        <span class="muted">{{ slot.sets }}×{{ slot.reps }}</span>
        <strong>{{ fmt(state.progress[slot.id].weight) }}</strong>
      </li>
    </ul>
    <button v-if="state.active" class="btn btn-primary" @click="emit('open')">Resume workout</button>
    <button v-else class="btn btn-primary" @click="start">Start workout</button>
    <button v-if="state.active" class="link link-danger" @click="discardWorkout()">
      Discard workout
    </button>
  </section>

  <section class="stats">
    <div class="card">
      <strong>{{ state.history.length }}</strong>
      <span class="muted">workouts</span>
    </div>
    <div class="card">
      <strong>{{ thisWeek }}</strong>
      <span class="muted">last 7 days</span>
    </div>
    <div class="card">
      <strong>{{ lastWorkout ? formatShortDate(lastWorkout.startedAt) : '—' }}</strong>
      <span class="muted">last workout</span>
    </div>
  </section>
</template>

<style scoped>
h2 {
  font-size: 24px;
  font-weight: 800;
}

.summary h2 {
  font-size: 17px;
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
  border-bottom: 1px solid var(--line);
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

.next .link {
  align-self: center;
}

.summary .up strong {
  color: var(--accent);
}

.summary .deload strong {
  color: var(--warn);
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.stats .card {
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
