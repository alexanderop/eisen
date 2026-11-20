<script setup lang="ts">
import { Card, ConfirmButton } from '../components/ui'
import CopyMarkdown from '../components/CopyMarkdown.vue'
import type { Workout } from '../domain/program'
import { durationMinutes } from '../domain/share'
import { fmt, formatDate } from '../format'
import { deleteWorkout, state } from '../store'

function duration(workout: Workout): string {
  const minutes = durationMinutes(workout)
  return minutes === null ? '' : `${minutes} min`
}

</script>

<template>
  <h1 class="page-title">History</h1>

  <Card v-if="state.history.length === 0" as="p" class="muted">
    No workouts yet. Finish your first one and it shows up here.
  </Card>

  <Card v-for="workout in state.history" as="article" :key="workout.id">
    <header class="row">
      <div>
        <h2>{{ workout.name }}</h2>
        <p class="muted meta">{{ formatDate(workout.startedAt) }} · {{ duration(workout) }}</p>
      </div>
      <div class="actions">
        <CopyMarkdown class="px-1" variant="ghost" size="md" :workout="workout" />
        <ConfirmButton
          variant="ghost"
          size="md"
          label="Delete"
          confirm-label="Confirm"
          @confirm="deleteWorkout(workout.id)"
        />
      </div>
    </header>
    <ul>
      <li v-for="log in workout.exercises" :key="log.id">
        <span class="name">{{ log.name }}</span>
        <strong>{{ log.equipment === 'bodyweight' ? 'BW' : fmt(log.weight, workout.unit) }}</strong>
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
  </Card>
</template>

<style scoped>
h2 {
  font-size: 18px;
  font-weight: 700;
}

.actions {
  display: flex;
  gap: 8px;
  flex: none;
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
  grid-template-columns: 1fr 64px minmax(92px, auto);
  gap: 8px;
  align-items: baseline;
  padding: 8px 0;
  border-top: 1px solid var(--border);
  font-size: 15px;
}

li strong {
  text-align: right;
}

.reps {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  color: var(--primary);
  font-weight: 600;
}

.reps .missed {
  color: var(--warn);
}
</style>
