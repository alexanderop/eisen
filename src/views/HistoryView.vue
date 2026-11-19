<script setup lang="ts">
import { ref } from 'vue'
import { EXERCISE_NAMES, isBodyweight, type Workout } from '../domain/program'
import { fmt, formatDate } from '../format'
import { deleteWorkout, state } from '../store'

const pendingDelete = ref<string | null>(null)

function duration(workout: Workout): string {
  if (workout.finishedAt === null) return ''
  return `${Math.max(1, Math.round((workout.finishedAt - workout.startedAt) / 60000))} min`
}

function onDelete(id: string) {
  if (pendingDelete.value !== id) {
    pendingDelete.value = id
    return
  }
  deleteWorkout(id)
  pendingDelete.value = null
}
</script>

<template>
  <h1 class="page-title">History</h1>

  <p v-if="state.history.length === 0" class="card muted">
    No workouts yet. Finish your first one and it shows up here.
  </p>

  <article v-for="workout in state.history" :key="workout.id" class="card">
    <header class="row">
      <div>
        <h2>Workout {{ workout.type }}</h2>
        <p class="muted meta">{{ formatDate(workout.startedAt) }} · {{ duration(workout) }}</p>
      </div>
      <button class="link link-danger" @click="onDelete(workout.id)">
        {{ pendingDelete === workout.id ? 'Tap to confirm' : 'Delete' }}
      </button>
    </header>
    <ul>
      <li v-for="log in workout.exercises" :key="log.id">
        <span class="name">{{ EXERCISE_NAMES[log.id] }}</span>
        <strong>{{ isBodyweight(log.id) ? 'BW' : fmt(log.weight, workout.unit) }}</strong>
        <span class="reps">
          <span
            v-for="(reps, index) in log.sets"
            :key="index"
            :class="{ missed: reps === null || (log.targetReps !== null && reps !== log.targetReps) }"
            >{{ reps ?? '–' }}</span
          >
        </span>
      </li>
    </ul>
  </article>
</template>

<style scoped>
h2 {
  font-size: 18px;
  font-weight: 700;
}

.meta {
  font-size: 13px;
}

ul {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
}

li {
  display: grid;
  grid-template-columns: 1fr 64px 92px;
  gap: 8px;
  align-items: baseline;
  padding: 8px 0;
  border-top: 1px solid var(--line);
  font-size: 15px;
}

li strong {
  text-align: right;
}

.reps {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  color: var(--accent);
  font-weight: 600;
}

.reps .missed {
  color: var(--warn);
}
</style>
